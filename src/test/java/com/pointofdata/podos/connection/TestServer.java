package com.pointofdata.podos.connection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Minimal in-process TCP server for connection unit tests.
 *
 * <p>Binds to an OS-assigned port on {@code localhost} and dispatches each accepted
 * connection to a configurable {@link Consumer handler} on a daemon thread.
 * Use the factory methods ({@link #acceptOnly()}, {@link #sendBytesAndWait(byte[])},
 * {@link #closeImmediately()}, {@link #echo()}) for the most common test patterns.
 */
class TestServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread       acceptThread;
    private final BlockingQueue<Socket> accepted = new LinkedBlockingQueue<>();
    private final Consumer<Socket> handler;
    private volatile boolean running = true;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Starts a server that runs {@code handler} for each accepted connection.
     *
     * @param handler called on a daemon thread for every accepted {@link Socket}
     */
    TestServer(Consumer<Socket> handler) throws IOException {
        this.handler      = handler;
        this.serverSocket = new ServerSocket(0);  // OS picks a free port
        this.acceptThread = new Thread(this::acceptLoop,
                "test-server:" + serverSocket.getLocalPort());
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /** Server that accepts connections and keeps them open indefinitely (no I/O). */
    static TestServer acceptOnly() throws IOException {
        return new TestServer(s -> park());
    }

    /**
     * Server that writes {@code bytes} to each client immediately after accepting,
     * then keeps the connection open.
     */
    static TestServer sendBytesAndWait(byte[] bytes) throws IOException {
        return new TestServer(s -> {
            try {
                s.getOutputStream().write(bytes);
                s.getOutputStream().flush();
                park();
            } catch (IOException ignored) {}
        });
    }

    /** Server that closes the connection immediately after accepting. */
    static TestServer closeImmediately() throws IOException {
        return new TestServer(s -> {
            try { s.close(); } catch (IOException ignored) {}
        });
    }

    /**
     * Server that writes {@code prefix} (simulating a framing header) and then
     * closes — use to test EOF-during-body reads.
     */
    static TestServer sendThenClose(byte[] prefix) throws IOException {
        return new TestServer(s -> {
            try {
                s.getOutputStream().write(prefix);
                s.getOutputStream().flush();
                s.close();
            } catch (IOException ignored) {}
        });
    }

    /** Server that echoes every received byte back to the sender. */
    static TestServer echo() throws IOException {
        return new TestServer(s -> {
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = s.getInputStream().read(buf)) != -1) {
                    s.getOutputStream().write(buf, 0, n);
                    s.getOutputStream().flush();
                }
            } catch (IOException ignored) {}
        });
    }

    // =========================================================================
    // API
    // =========================================================================

    /** Returns the port this server is listening on. */
    int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the most recently accepted server-side socket, waiting up to 2 s.
     * Returns {@code null} if no connection arrived in time.
     */
    Socket lastAccepted() throws InterruptedException {
        return accepted.poll(2, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Wire-frame helpers — shared by test classes
    // =========================================================================

    /**
     * Builds a minimal wire frame with a hex-encoded 9-byte total-length prefix
     * followed by {@code body}.
     *
     * <p>Format: {@code x########} where {@code ########} is the total length
     * (prefix + body) as 8 lowercase hex digits.
     */
    static byte[] hexFrame(byte[] body) {
        int total = 9 + body.length;
        byte[] frame  = new byte[total];
        byte[] prefix = String.format("x%08x", total)
                .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, frame, 0, 9);
        System.arraycopy(body,   0, frame, 9, body.length);
        return frame;
    }

    /**
     * Builds a wire frame with a decimal-encoded 9-byte total-length prefix
     * followed by {@code body}.
     *
     * <p>Format: {@code #########} — zero-padded 9-digit decimal total length.
     */
    static byte[] decimalFrame(byte[] body) {
        int total = 9 + body.length;
        byte[] frame  = new byte[total];
        byte[] prefix = String.format("%09d", total)
                .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, frame, 0, 9);
        System.arraycopy(body,   0, frame, 9, body.length);
        return frame;
    }

    /**
     * Returns a 9-byte hex prefix claiming a total length larger than the prefix
     * itself — used to test EOF-during-body reads.
     *
     * @param claimedTotal total message length encoded in the prefix
     */
    static byte[] hexPrefixOnly(int claimedTotal) {
        return String.format("x%08x", claimedTotal)
                .getBytes(StandardCharsets.US_ASCII);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                accepted.offer(client);
                Thread t = new Thread(() -> handler.accept(client),
                        "test-conn:" + serverSocket.getLocalPort());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                break;  // normal on close()
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
    }

    private static void park() {
        try { Thread.sleep(60_000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
