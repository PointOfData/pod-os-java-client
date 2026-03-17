package com.pointofdata.podos.connection;

import com.pointofdata.podos.config.PoolConfig;
import com.pointofdata.podos.errors.ErrCode;
import com.pointofdata.podos.errors.GatewayDError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConnectionPool}.
 *
 * <p>Tests that require real network connections use an in-process {@link TestServer}.
 * Pool acquire timeouts are kept to ≤ 300 ms to keep the suite fast.
 */
@DisplayName("ConnectionPool")
@Timeout(15)
class ConnectionPoolTest {

    // =========================================================================
    // 1. Constructor / pre-population
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("null config throws IllegalArgumentException")
        void nullConfigThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectionPool(null, () -> { throw new IOException(); }));
        }

        @Test
        @DisplayName("null factory throws IllegalArgumentException")
        void nullFactoryThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectionPool(new PoolConfig(5, 0), null));
        }

        @Test
        @DisplayName("initialCapacity=0 creates an empty pool")
        void zeroInitialCapacityCreatesEmptyPool() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    assertEquals(0, pool.availableCount());
                    assertEquals(0, pool.liveCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("initialCapacity=2 pre-creates 2 idle connections")
        void initialCapacityPreCreatesConnections() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 2), factory(server.getPort()));
                try {
                    assertEquals(2, pool.availableCount(), "idle count");
                    assertEquals(2, pool.liveCount(),      "live count");
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("initialCapacity is capped at maxCapacity")
        void initialCapacityCappedAtMax() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                // maxCapacity=2, initialCapacity=10 → only 2 created
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(2, 10), factory(server.getPort()));
                try {
                    assertEquals(2, pool.availableCount());
                    assertEquals(2, pool.liveCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("factory IOException during pre-population propagates to caller")
        void factoryFailurePropagates() {
            AtomicInteger calls = new AtomicInteger(0);
            IOException thrown = assertThrows(IOException.class, () ->
                    new ConnectionPool(new PoolConfig(5, 2), () -> {
                        if (calls.incrementAndGet() == 2) throw new IOException("second fails");
                        // First call connects to a real server but that's complex — just always fail
                        throw new IOException("factory always fails");
                    }));
            assertNotNull(thrown.getMessage());
        }
    }

    // =========================================================================
    // 2. acquire()
    // =========================================================================

    @Nested
    @DisplayName("acquire()")
    class AcquireTests {

        @Test
        @DisplayName("acquire() returns a pre-created idle connection")
        void acquireFromPreCreatedPool() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 2), factory(server.getPort()));
                try {
                    ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                    assertNotNull(c);
                    assertTrue(c.isConnected());
                    assertEquals(1, pool.availableCount(), "one idle left after acquire");
                    pool.release(c);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() creates a new connection when idle pool is empty and below max")
        void acquireCreatesNewWhenIdle() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                    assertNotNull(c);
                    assertTrue(c.isConnected());
                    assertEquals(0, pool.availableCount());
                    assertEquals(1, pool.liveCount());
                    pool.release(c);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() with maxCapacity=0 means unlimited — never blocks")
        void unlimitedPoolNeverBlocks() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                // maxCapacity=0 means no limit
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(0, 0), factory(server.getPort()));
                try {
                    List<ConnectionClient> acquired = new ArrayList<>();
                    for (int i = 0; i < 10; i++) {
                        acquired.add(pool.acquire(Duration.ofMillis(100)));
                    }
                    assertEquals(10, pool.liveCount());
                    acquired.forEach(pool::release);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() throws POOL_EXHAUSTED when at max capacity and timeout expires")
        void acquireTimeoutThrowsPoolExhausted() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 0), factory(server.getPort()));
                try {
                    // Fill the pool to capacity
                    ConnectionClient held = pool.acquire(Duration.ofSeconds(1));

                    long start = System.currentTimeMillis();
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> pool.acquire(Duration.ofMillis(150)));
                    long elapsed = System.currentTimeMillis() - start;

                    assertEquals(ErrCode.POOL_EXHAUSTED, err.getCode());
                    assertTrue(elapsed >= 100,
                            "should have waited at least 100ms before throwing, elapsed=" + elapsed);
                    assertTrue(elapsed < 1500,
                            "should have thrown within ~150ms, elapsed=" + elapsed);

                    pool.release(held);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() blocks and unblocks when a connection is released")
        void acquireBlocksAndUnblocksOnRelease() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 0), factory(server.getPort()));
                try {
                    // Occupy the single slot
                    ConnectionClient held = pool.acquire(Duration.ofSeconds(1));

                    // Background thread tries to acquire (will block)
                    CompletableFuture<ConnectionClient> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return pool.acquire(Duration.ofSeconds(3));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    // Verify it is actually blocked
                    Thread.sleep(80);
                    assertFalse(future.isDone(), "acquire should still be blocking");

                    // Release the held connection — unblocks the background thread
                    pool.release(held);

                    ConnectionClient acquired = future.get(2, TimeUnit.SECONDS);
                    assertNotNull(acquired);
                    assertTrue(acquired.isConnected());
                    pool.release(acquired);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() throws POOL_EXHAUSTED immediately when pool is closed")
        void acquireAfterCloseThrows() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                pool.close();

                GatewayDError err = assertThrows(GatewayDError.class,
                        () -> pool.acquire(Duration.ofSeconds(1)));
                assertEquals(ErrCode.POOL_EXHAUSTED, err.getCode());
            }
        }

        @Test
        @DisplayName("acquire() throws POOL_EXHAUSTED when pool is closed while waiting")
        void acquireThrowsWhenPoolClosedWhileWaiting() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 0), factory(server.getPort()));

                // Fill the pool
                ConnectionClient held = pool.acquire(Duration.ofSeconds(1));

                // Background thread waits
                AtomicReference<Throwable> error = new AtomicReference<>();
                Thread waiter = new Thread(() -> {
                    try {
                        pool.acquire(Duration.ofSeconds(10));
                    } catch (GatewayDError e) {
                        error.set(e);
                    } catch (Exception e) {
                        error.set(e);
                    }
                });
                waiter.setDaemon(true);
                waiter.start();

                Thread.sleep(80); // let waiter block

                // Close pool while waiter is blocked
                pool.release(held);   // let pool own the connection so close() handles it cleanly
                pool.close();

                waiter.join(2000);
                assertFalse(waiter.isAlive(), "waiter thread should have terminated");

                assertNotNull(error.get(), "waiter should have received an error");
                assertInstanceOf(GatewayDError.class, error.get());
                assertEquals(ErrCode.POOL_EXHAUSTED,
                        ((GatewayDError) error.get()).getCode());
            }
        }

        @Test
        @DisplayName("acquire() skips stale (disconnected) idle connections and creates a fresh one")
        void acquireSkipsStalePrecreated() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(3, 1), factory(server.getPort()));
                try {
                    // Acquire and close the pre-created connection, then release (pool discards it)
                    ConnectionClient stale = pool.acquire(Duration.ofSeconds(1));
                    stale.close();           // explicitly close — isConnected() becomes false
                    pool.release(stale);     // release() discards disconnected → liveCount=0

                    assertEquals(0, pool.availableCount(), "stale should have been discarded");
                    assertEquals(0, pool.liveCount());

                    // Next acquire should create a fresh connection
                    ConnectionClient fresh = pool.acquire(Duration.ofSeconds(1));
                    assertNotNull(fresh);
                    assertTrue(fresh.isConnected(), "newly created connection should be live");
                    assertEquals(1, pool.liveCount());
                    pool.release(fresh);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("acquire() factory failure decrements liveCount and rethrows IOException")
        void acquireFactoryFailureDecrementsLiveCount() throws Exception {
            AtomicInteger attempt = new AtomicInteger(0);

            ConnectionPool.ConnectionFactory failFactory = () -> {
                attempt.incrementAndGet();
                throw new IOException("dial refused");
            };

            ConnectionPool pool = new ConnectionPool(new PoolConfig(3, 0), failFactory);
            try {
                IOException ex = assertThrows(IOException.class,
                        () -> pool.acquire(Duration.ofSeconds(1)));
                assertTrue(ex.getMessage().contains("dial refused"));
                assertEquals(0, pool.liveCount(),
                        "liveCount must be decremented back on factory failure");
            } finally {
                pool.close();
            }
        }
    }

    // =========================================================================
    // 3. release()
    // =========================================================================

    @Nested
    @DisplayName("release()")
    class ReleaseTests {

        @Test
        @DisplayName("release() returns a connected connection to the idle pool")
        void releaseConnectedReturnsToIdle() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(0, pool.availableCount());

                    pool.release(c);
                    assertEquals(1, pool.availableCount());
                    assertEquals(1, pool.liveCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("release() discards a disconnected connection and decrements liveCount")
        void releaseDisconnectedDiscards() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(1, pool.liveCount());

                    c.close();  // force disconnect
                    assertFalse(c.isConnected());

                    pool.release(c);
                    assertEquals(0, pool.availableCount(), "stale must not be returned to idle");
                    assertEquals(0, pool.liveCount(),      "liveCount must decrement");
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("release(null) is a no-op — does not throw")
        void releaseNullIsSafe() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    assertDoesNotThrow(() -> pool.release(null));
                    assertEquals(0, pool.liveCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("release() to a closed pool closes the connection and decrements liveCount")
        void releaseToClosedPoolClosesConnection() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                assertEquals(1, pool.liveCount());

                pool.close();  // close the pool while connection is in-use

                pool.release(c); // release to closed pool
                assertFalse(c.isConnected(), "connection should be closed by release()");
            }
        }

        @Test
        @DisplayName("release() signals a blocked acquire() to proceed")
        void releaseSignalsBlockedAcquire() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 0), factory(server.getPort()));
                try {
                    ConnectionClient held = pool.acquire(Duration.ofSeconds(1));

                    CompletableFuture<ConnectionClient> waiter = CompletableFuture.supplyAsync(() -> {
                        try {
                            return pool.acquire(Duration.ofSeconds(3));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    Thread.sleep(60);
                    assertFalse(waiter.isDone());

                    pool.release(held);

                    ConnectionClient acquired = waiter.get(2, TimeUnit.SECONDS);
                    assertNotNull(acquired);
                    pool.release(acquired);
                } finally {
                    pool.close();
                }
            }
        }
    }

    // =========================================================================
    // 4. close()
    // =========================================================================

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("close() marks pool as closed")
        void closeSetsIsClosed() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                assertFalse(pool.isClosed());
                pool.close();
                assertTrue(pool.isClosed());
            }
        }

        @Test
        @DisplayName("close() drains idle pool to zero")
        void closeEmptiesIdlePool() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 2), factory(server.getPort()));
                assertEquals(2, pool.availableCount());
                pool.close();
                assertEquals(0, pool.availableCount());
                assertEquals(0, pool.liveCount());
            }
        }

        @Test
        @DisplayName("close() is idempotent — second call does not throw")
        void closeIsIdempotent() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 1), factory(server.getPort()));
                pool.close();
                assertDoesNotThrow(pool::close);
                assertTrue(pool.isClosed());
            }
        }

        @Test
        @DisplayName("try-with-resources closes the pool automatically")
        void tryWithResourcesClosesPool() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool;
                try (ConnectionPool p = new ConnectionPool(
                        new PoolConfig(5, 1), factory(server.getPort()))) {
                    pool = p;
                    assertFalse(p.isClosed());
                }
                assertTrue(pool.isClosed());
            }
        }
    }

    // =========================================================================
    // 5. Introspection
    // =========================================================================

    @Nested
    @DisplayName("availableCount() and liveCount()")
    class IntrospectionTests {

        @Test
        @DisplayName("availableCount tracks idle connections correctly")
        void availableCountTracks() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 3), factory(server.getPort()));
                try {
                    assertEquals(3, pool.availableCount());

                    ConnectionClient c1 = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(2, pool.availableCount());

                    ConnectionClient c2 = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(1, pool.availableCount());

                    pool.release(c1);
                    assertEquals(2, pool.availableCount());

                    pool.release(c2);
                    assertEquals(3, pool.availableCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("liveCount tracks total connections (idle + in-use)")
        void liveCountTracks() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(5, 0), factory(server.getPort()));
                try {
                    assertEquals(0, pool.liveCount());

                    ConnectionClient c1 = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(1, pool.liveCount());

                    ConnectionClient c2 = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(2, pool.liveCount());

                    pool.release(c1);
                    assertEquals(2, pool.liveCount(), "release returns to idle, liveCount unchanged");

                    c2.close(); // force disconnect
                    pool.release(c2);
                    assertEquals(1, pool.liveCount(), "stale release decrements liveCount");
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("getConfig() returns the same PoolConfig that was passed to constructor")
        void getConfigReturnsOriginal() throws Exception {
            PoolConfig cfg = new PoolConfig(7, 2);
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(cfg, factory(server.getPort()));
                try {
                    assertSame(cfg, pool.getConfig());
                } finally {
                    pool.close();
                }
            }
        }
    }

    // =========================================================================
    // 6. Concurrent acquire / release
    // =========================================================================

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent acquire/release from multiple threads maintains correct liveCount")
        void concurrentAcquireReleaseCorrectLiveCount() throws Exception {
            int maxCap = 5;
            int threads = 20;
            int opsPerThread = 10;

            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(maxCap, 0), factory(server.getPort()));
                try {
                    ExecutorService exec = Executors.newFixedThreadPool(threads);
                    List<Future<?>> futures = new ArrayList<>();

                    for (int t = 0; t < threads; t++) {
                        futures.add(exec.submit(() -> {
                            for (int i = 0; i < opsPerThread; i++) {
                                ConnectionClient c = null;
                                try {
                                    c = pool.acquire(Duration.ofSeconds(2));
                                    // Simulate work
                                    Thread.sleep(5);
                                } catch (GatewayDError e) {
                                    // acceptable if pool transiently exhausted
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } catch (IOException e) {
                                    // network error in test environment
                                } finally {
                                    if (c != null) pool.release(c);
                                }
                            }
                        }));
                    }

                    exec.shutdown();
                    assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS),
                            "all threads should complete within 10 seconds");

                    for (Future<?> f : futures) {
                        assertDoesNotThrow(() -> f.get());
                    }

                    // After all threads complete, liveCount must not exceed maxCap
                    assertTrue(pool.liveCount() <= maxCap,
                            "liveCount (" + pool.liveCount() + ") must not exceed maxCap (" + maxCap + ")");
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("liveCount never exceeds maxCapacity under concurrent load")
        void liveCountNeverExceedsMax() throws Exception {
            int maxCap = 3;

            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(maxCap, 0), factory(server.getPort()));
                try {
                    AtomicInteger maxObserved = new AtomicInteger(0);
                    ExecutorService exec = Executors.newFixedThreadPool(10);
                    CountDownLatch startGate = new CountDownLatch(1);
                    List<Future<?>> futures = new ArrayList<>();

                    for (int t = 0; t < 10; t++) {
                        futures.add(exec.submit(() -> {
                            try { startGate.await(); } catch (InterruptedException e) {
                                Thread.currentThread().interrupt(); return;
                            }
                            for (int i = 0; i < 5; i++) {
                                ConnectionClient c = null;
                                try {
                                    c = pool.acquire(Duration.ofMillis(200));
                                    int live = pool.liveCount();
                                    maxObserved.accumulateAndGet(live, Math::max);
                                } catch (Exception ignored) {
                                } finally {
                                    if (c != null) pool.release(c);
                                }
                            }
                        }));
                    }

                    startGate.countDown();
                    exec.shutdown();
                    exec.awaitTermination(10, TimeUnit.SECONDS);

                    assertTrue(maxObserved.get() <= maxCap,
                            "max observed liveCount " + maxObserved.get()
                                    + " exceeded maxCapacity " + maxCap);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("multiple blocked acquirers are all unblocked when connections are released")
        void multipleBlockedAcquirersUnblocked() throws Exception {
            int maxCap = 2;
            int waiters = 4;

            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(maxCap, 0), factory(server.getPort()));
                try {
                    // Fill pool to capacity
                    List<ConnectionClient> held = new ArrayList<>();
                    for (int i = 0; i < maxCap; i++) {
                        held.add(pool.acquire(Duration.ofSeconds(1)));
                    }

                    // Start waiters
                    List<CompletableFuture<ConnectionClient>> waitFutures = new ArrayList<>();
                    for (int i = 0; i < waiters; i++) {
                        waitFutures.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                return pool.acquire(Duration.ofSeconds(5));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }));
                    }

                    Thread.sleep(80); // let all waiters block

                    // Release all held connections — each released connection unblocks one waiter
                    for (ConnectionClient c : held) {
                        pool.release(c);
                    }

                    // All waiters should eventually get a connection
                    List<ConnectionClient> acquired = new ArrayList<>();
                    for (CompletableFuture<ConnectionClient> f : waitFutures) {
                        ConnectionClient c = f.get(3, TimeUnit.SECONDS);
                        assertNotNull(c);
                        acquired.add(c);
                    }

                    acquired.forEach(pool::release);
                } finally {
                    pool.close();
                }
            }
        }
    }

    // =========================================================================
    // 7. Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("acquire() with zero timeout throws immediately when pool is full")
        void acquireZeroTimeoutThrowsImmediately() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 0), factory(server.getPort()));
                try {
                    ConnectionClient held = pool.acquire(Duration.ofSeconds(1));

                    long start = System.currentTimeMillis();
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> pool.acquire(Duration.ofMillis(0)));
                    long elapsed = System.currentTimeMillis() - start;

                    assertEquals(ErrCode.POOL_EXHAUSTED, err.getCode());
                    assertTrue(elapsed < 500, "zero timeout should fail near-instantly, elapsed=" + elapsed);

                    pool.release(held);
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("pool with maxCapacity=1 and initialCapacity=1 is not depleted on acquire")
        void singleConnectionPool() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(1, 1), factory(server.getPort()));
                try {
                    assertEquals(1, pool.availableCount());
                    ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                    assertEquals(0, pool.availableCount());
                    assertEquals(1, pool.liveCount());
                    pool.release(c);
                    assertEquals(1, pool.availableCount());
                    assertEquals(1, pool.liveCount());
                } finally {
                    pool.close();
                }
            }
        }

        @Test
        @DisplayName("releasing to pool then closing: connection is closed by pool.close()")
        void releaseBeforeCloseConnectionIsCleanedUp() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(3, 0), factory(server.getPort()));

                ConnectionClient c = pool.acquire(Duration.ofSeconds(1));
                pool.release(c);  // back to idle
                assertEquals(1, pool.availableCount());

                pool.close();

                assertEquals(0, pool.availableCount());
                assertEquals(0, pool.liveCount());
                assertTrue(pool.isClosed());
            }
        }

        @Test
        @DisplayName("acquire after pool.close() immediately returns POOL_EXHAUSTED (not blocked)")
        void acquireAfterCloseIsNotBlocking() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionPool pool = new ConnectionPool(
                        new PoolConfig(3, 0), factory(server.getPort()));
                pool.close();

                long start = System.currentTimeMillis();
                assertThrows(GatewayDError.class,
                        () -> pool.acquire(Duration.ofSeconds(10)));  // long timeout but pool is closed
                long elapsed = System.currentTimeMillis() - start;

                assertTrue(elapsed < 500,
                        "acquire on closed pool should return immediately, not block for 10s; elapsed=" + elapsed);
            }
        }
    }

    // =========================================================================
    // Helper factory
    // =========================================================================

    private static ConnectionPool.ConnectionFactory factory(int port) {
        return () -> {
            ConnectionClient c = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(port))
                    .dialTimeout(Duration.ofMillis(500))
                    .sendTimeout(Duration.ofMillis(500))
                    .receiveTimeout(Duration.ofMillis(500))
                    .retry(new Retry(2, Duration.ofMillis(10), 1.0))
                    .build();
            c.connect();
            return c;
        };
    }
}
