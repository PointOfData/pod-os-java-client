package com.pointofdata.podos.connection;

import com.pointofdata.podos.config.PoolConfig;
import com.pointofdata.podos.errors.GatewayDError;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe LIFO connection pool for {@link ConnectionClient} instances.
 * Mirrors Go's {@code ChannelPool}.
 *
 * <h2>Acquire policy</h2>
 * <ol>
 *   <li>Pop an idle connection that is still {@link ConnectionClient#isConnected() connected}.</li>
 *   <li>If none available and {@code maxCapacity == 0} or {@code liveCount < maxCapacity},
 *       call the factory to create a new connection.</li>
 *   <li>Otherwise block until a connection is released or the timeout expires,
 *       then throw {@link GatewayDError#ERR_POOL_EXHAUSTED}.</li>
 * </ol>
 *
 * <p>Stale (disconnected) connections are discarded lazily on {@link #acquire} and
 * eagerly on {@link #release}.
 */
public class ConnectionPool implements AutoCloseable {

    /**
     * Factory that creates a new, already-connected {@link ConnectionClient}.
     * Permits checked {@link IOException} so factories can propagate dial errors.
     */
    @FunctionalInterface
    public interface ConnectionFactory {
        ConnectionClient create() throws IOException;
    }

    private final PoolConfig config;
    private final ConnectionFactory factory;
    private final Deque<ConnectionClient> idle = new ArrayDeque<>();

    /** Total live connections: idle + in-use. */
    private int liveCount = 0;
    private volatile boolean closed = false;

    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition    available  = lock.newCondition();

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates and pre-populates the pool with up to {@code initialCapacity} connections.
     *
     * @param config  pool size parameters
     * @param factory factory called to create each new connection
     * @throws IllegalArgumentException if config or factory is null
     * @throws IOException              if pre-creating the initial connections fails
     */
    public ConnectionPool(PoolConfig config, ConnectionFactory factory) throws IOException {
        if (config == null)  throw new IllegalArgumentException("config must not be null");
        if (factory == null) throw new IllegalArgumentException("factory must not be null");
        this.config  = config;
        this.factory = factory;

        int cap      = config.maxCapacity > 0 ? config.maxCapacity : Integer.MAX_VALUE;
        int toCreate = Math.min(config.initialCapacity, cap);
        for (int i = 0; i < toCreate; i++) {
            idle.push(factory.create());
            liveCount++;
        }
    }

    // =========================================================================
    // Acquire
    // =========================================================================

    /**
     * Acquires a live connection from the pool, blocking until one becomes available
     * or the timeout elapses.
     *
     * @param timeout maximum time to wait when the pool is at capacity
     * @return a connected {@link ConnectionClient}
     * @throws GatewayDError        ({@code POOL_EXHAUSTED}) if the pool is closed or the
     *                              timeout expires while waiting
     * @throws IOException          if the factory fails to create a new connection
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public ConnectionClient acquire(Duration timeout)
            throws GatewayDError, IOException, InterruptedException {

        if (closed) {
            throw GatewayDError.ERR_POOL_EXHAUSTED.wrap(
                    new IOException("pool is closed"));
        }

        long deadlineNs = System.nanoTime() + timeout.toNanos();

        lock.lock();
        try {
            while (true) {
                if (closed) {
                    throw GatewayDError.ERR_POOL_EXHAUSTED.wrap(
                            new IOException("pool was closed while waiting for a connection"));
                }

                // Drain idle until we find a live connection
                while (!idle.isEmpty()) {
                    ConnectionClient c = idle.pop();
                    if (c.isConnected()) {
                        return c;   // lock released by finally
                    }
                    liveCount--;    // discard stale
                    try { c.close(); } catch (Exception ignored) {}
                }

                // Can we create a new connection?
                int max = config.maxCapacity;
                if (max <= 0 || liveCount < max) {
                    liveCount++;
                    lock.unlock();
                    try {
                        return factory.create();
                        // lock NOT held on this return path — see finally guard
                    } catch (Exception e) {
                        lock.lock();
                        liveCount--;
                        available.signal(); // free capacity for other waiters
                        if (e instanceof IOException) throw (IOException) e;
                        throw new IOException(
                                "pool factory failed to create connection: " + e.getMessage(), e);
                    }
                }

                // Pool at capacity — wait
                long remainingNs = deadlineNs - System.nanoTime();
                if (remainingNs <= 0) {
                    throw GatewayDError.ERR_POOL_EXHAUSTED.wrap(null);
                }
                available.awaitNanos(remainingNs);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // =========================================================================
    // Release
    // =========================================================================

    /**
     * Returns a connection to the idle pool.
     *
     * <p>If the connection is no longer connected, or the pool is closed, the
     * connection is closed and discarded (and {@code liveCount} is decremented).
     * Passing {@code null} is a no-op.
     *
     * @param client the connection to return; may be null
     */
    public void release(ConnectionClient client) {
        if (client == null) return;
        lock.lock();
        try {
            if (closed || !client.isConnected()) {
                liveCount = Math.max(0, liveCount - 1);
                try { client.close(); } catch (Exception ignored) {}
            } else {
                idle.push(client);
                available.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // Close
    // =========================================================================

    /**
     * Closes all idle connections, marks the pool as closed, and unblocks any
     * threads waiting in {@link #acquire} (they will receive a
     * {@link GatewayDError} with code {@code POOL_EXHAUSTED}).
     *
     * <p>In-use connections are not forcibly closed; callers should still call
     * {@link #release} (which will discard them) or close them directly.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            available.signalAll();
            while (!idle.isEmpty()) {
                try { idle.pop().close(); } catch (Exception ignored) {}
            }
            liveCount = 0;
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // Introspection
    // =========================================================================

    /** Number of idle connections ready to be acquired without creating a new one. */
    public int availableCount() {
        lock.lock();
        try { return idle.size(); }
        finally { lock.unlock(); }
    }

    /** Total live connections: idle + currently in use. */
    public int liveCount() {
        lock.lock();
        try { return liveCount; }
        finally { lock.unlock(); }
    }

    /** Returns {@code true} if this pool has been closed. */
    public boolean isClosed() { return closed; }

    /** Returns the {@link PoolConfig} this pool was created with. */
    public PoolConfig getConfig() { return config; }
}
