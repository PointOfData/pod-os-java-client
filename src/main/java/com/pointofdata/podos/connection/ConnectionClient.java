package com.pointofdata.podos.connection;

import com.pointofdata.podos.errors.GatewayDError;
import com.pointofdata.podos.log.NoOpLogger;
import com.pointofdata.podos.log.PodOsLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Low-level TCP client for Pod-OS gateway connections.
 * Mirrors Go's {@code connection.Client} struct.
 *
 * <p>Thread-safety:
 * <ul>
 *   <li>{@link #send} is protected by a {@link ReentrantLock}.</li>
 *   <li>{@link #isConnected()} uses an {@link AtomicBoolean} for lock-free reads.</li>
 *   <li>{@link #receive} should be called from a single reader thread (the background
 *       receiver loop when concurrent mode is enabled).</li>
 * </ul>
 *
 * <p>For high throughput, keep the socket write buffer adequately sized and
 * use the background receiver ({@link com.pointofdata.podos.PodOsClient}) to
 * pipeline multiple in-flight requests.
 */
public class ConnectionClient {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int MAX_ZERO_WRITES    = 3;

    // -------------------------------------------------------------------------
    // Mutable state
    // -------------------------------------------------------------------------

    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile InputStream  in;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ReentrantLock sendLock  = new ReentrantLock();

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private String host;
    private String port;
    private String network;
    private String actorName;

    private int receiveChunkSize;
    private int sendTimeoutMs;
    private int receiveTimeoutMs;
    private int dialTimeoutMs;
    private boolean tcpKeepAlive = false;

    private final Retry retry;
    private final PodOsLogger logger;
    private final WireHook wireHook;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private ConnectionClient(Builder b) {
        this.host              = b.host;
        this.port              = b.port;
        this.network           = b.network;
        this.actorName         = b.actorName;
        this.receiveChunkSize  = b.receiveChunkSize > 0 ? b.receiveChunkSize : DEFAULT_CHUNK_SIZE;
        this.sendTimeoutMs     = (int) b.sendTimeout.toMillis();
        this.receiveTimeoutMs  = (int) b.receiveTimeout.toMillis();
        this.dialTimeoutMs     = (int) b.dialTimeout.toMillis();
        this.retry             = b.retry != null ? b.retry : new Retry();
        this.logger            = b.logger != null ? b.logger : NoOpLogger.INSTANCE;
        this.wireHook          = b.wireHook;
    }

    /**
     * Establishes the TCP connection with retry logic.
     *
     * @throws IOException if connection could not be established after all retries
     */
    public void connect() throws IOException {
        try {
            retry.retry(() -> {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, Integer.parseInt(port)), dialTimeoutMs);
                s.setSoTimeout(receiveTimeoutMs);
                s.setTcpNoDelay(true);
                s.setKeepAlive(tcpKeepAlive);
                this.socket = s;
                this.out    = s.getOutputStream();
                this.in     = s.getInputStream();
                connected.set(true);
                return s;
            });
        } catch (Retry.RetriesExhaustedException e) {
            throw new IOException("failed to connect to " + host + ":" + port + " after " + retry.retries + " retries", e);
        }
        logger.info("connected", "host", host, "port", port, "actor", actorName);
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    /**
     * Sends data to the gateway.
     *
     * @param data bytes to send
     * @return number of bytes sent
     * @throws GatewayDError if the connection is closed or the write fails
     */
    public int send(byte[] data) throws GatewayDError {
        if (!connected.get() || socket == null) {
            throw GatewayDError.ERR_CLIENT_NOT_CONNECTED.wrap(null);
        }
        sendLock.lock();
        try {
            if (sendTimeoutMs > 0) {
                socket.setSoTimeout(sendTimeoutMs);
            }
            int total = 0;
            int zeroWrites = 0;
            while (total < data.length) {
                // java.io.OutputStream.write() is all-or-nothing, but we track bytes explicitly
                int toWrite = data.length - total;
                try {
                    out.write(data, total, toWrite);
                    total += toWrite;
                    zeroWrites = 0;
                } catch (IOException e) {
                    logger.error("send failed", "host", host, "error", e.getMessage());
                    connected.set(false);
                    throw GatewayDError.ERR_CLIENT_SEND_FAILED.wrap(e);
                }
            }
            if (wireHook != null) wireHook.onSend(data);
            if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                logger.debug("sent data", "host", host, "bytes", total);
            }
            return total;
        } finally {
            sendLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Receive
    // -------------------------------------------------------------------------

    /**
     * Receives a complete message from the gateway.
     *
     * <p>Two-phase read:
     * <ol>
     *   <li>Read exactly 9 bytes of length prefix.</li>
     *   <li>Parse hex ({@code x} prefix) or decimal length.</li>
     *   <li>Read remaining bytes in {@link #receiveChunkSize} chunks.</li>
     * </ol>
     *
     * @param timeoutMs read timeout in milliseconds (0 = use configured timeout)
     * @return complete message bytes including the 9-byte length prefix
     * @throws GatewayDError if the connection is closed, the read times out, or
     *                       the framing is invalid
     */
    public byte[] receive(int timeoutMs) throws GatewayDError {
        if (!connected.get() || socket == null) {
            throw GatewayDError.ERR_CLIENT_NOT_CONNECTED.wrap(null);
        }
        int timeout = timeoutMs > 0 ? timeoutMs : receiveTimeoutMs;
        try {
            socket.setSoTimeout(timeout);
        } catch (IOException e) {
            throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(e);
        }

        // Phase 1: read 9-byte length prefix
        byte[] prefix = new byte[9];
        readFully(prefix, 0, 9, timeout);

        if (!isValidLengthPrefix(prefix)) {
            throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(
                    new IOException("connection out of sync: invalid length prefix: " + new String(prefix)));
        }

        // Parse total message length
        long totalMsgLength;
        try {
            totalMsgLength = decodeLengthPrefix(prefix);
        } catch (NumberFormatException e) {
            throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(
                    new IOException("failed to parse message length prefix", e));
        }

        int remaining = (int) (totalMsgLength - 9);
        if (remaining < 0) {
            throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(
                    new IOException("invalid message length: " + totalMsgLength));
        }

        // Phase 2: read remaining bytes
        byte[] buffer = new byte[(int) totalMsgLength];
        System.arraycopy(prefix, 0, buffer, 0, 9);
        readFully(buffer, 9, remaining, timeout);

        if (wireHook != null) wireHook.onReceive(buffer);
        return buffer;
    }

    private void readFully(byte[] buf, int offset, int length, int timeoutMs) throws GatewayDError {
        int read = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (read < length) {
            if (System.currentTimeMillis() > deadline) {
                throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(
                        new SocketTimeoutException("receive timeout after " + timeoutMs + "ms"));
            }
            int remaining = length - read;
            int chunkSize = Math.min(receiveChunkSize, remaining);
            int n;
            try {
                n = in.read(buf, offset + read, chunkSize);
            } catch (SocketTimeoutException e) {
                // If still within deadline, retry; otherwise propagate
                if (System.currentTimeMillis() < deadline) continue;
                throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(e);
            } catch (IOException e) {
                connected.set(false);
                throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(e);
            }
            if (n == -1) {
                connected.set(false);
                throw GatewayDError.ERR_CLIENT_RECEIVE_FAILED.wrap(
                        new IOException("connection closed by remote (EOF)"));
            }
            read += n;
        }
    }

    // -------------------------------------------------------------------------
    // Reconnect
    // -------------------------------------------------------------------------

    /**
     * Reconnects to the gateway, preserving the original host/port/actorName configuration.
     *
     * @throws IOException if reconnection fails
     */
    public void reconnect() throws IOException {
        close();
        connected.set(false);
        connect();
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    /** Closes the socket and marks the connection as disconnected. */
    public void close() {
        connected.set(false);
        Socket s = socket;
        if (s != null) {
            try {
                s.setSoLinger(true, 0); // immediate close
                s.close();
            } catch (IOException ignored) {}
            socket = null;
            out    = null;
            in     = null;
        }
        logger.info("connection closed", "host", host, "actor", actorName);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    public String remoteAddr() {
        Socket s = socket;
        return (s != null && s.getRemoteSocketAddress() != null)
                ? s.getRemoteSocketAddress().toString() : "";
    }

    public String localAddr() {
        Socket s = socket;
        return (s != null && s.getLocalSocketAddress() != null)
                ? s.getLocalSocketAddress().toString() : "";
    }

    public String getActorName() { return actorName; }
    public String getHost()      { return host; }
    public String getPort()      { return port; }

    // -------------------------------------------------------------------------
    // Wire format helpers
    // -------------------------------------------------------------------------

    private static boolean isValidLengthPrefix(byte[] prefix) {
        if (prefix.length < 9) return false;
        if (prefix[0] == 'x') {
            for (int i = 1; i < 9; i++) {
                byte b = prefix[i];
                if (!((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F'))) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (prefix[i] < '0' || prefix[i] > '9') return false;
        }
        return true;
    }

    private static long decodeLengthPrefix(byte[] prefix) {
        String s = new String(prefix).trim();
        if (s.charAt(0) == 'x') return Long.parseLong(s.substring(1), 16);
        return Long.parseLong(s, 10);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String      host             = "";
        private String      port             = "62312";
        private String      network          = "tcp";
        private String      actorName        = "";
        private int         receiveChunkSize = DEFAULT_CHUNK_SIZE;
        private Duration    dialTimeout      = Duration.ofSeconds(5);
        private Duration    sendTimeout      = Duration.ofSeconds(5);
        private Duration    receiveTimeout   = Duration.ofSeconds(5);
        private Retry       retry;
        private PodOsLogger logger;
        private WireHook    wireHook;

        public Builder host(String v)             { this.host = v; return this; }
        public Builder port(String v)             { this.port = v; return this; }
        public Builder network(String v)          { this.network = v; return this; }
        public Builder actorName(String v)        { this.actorName = v; return this; }
        public Builder receiveChunkSize(int v)    { this.receiveChunkSize = v; return this; }
        public Builder dialTimeout(Duration v)    { this.dialTimeout = v; return this; }
        public Builder sendTimeout(Duration v)    { this.sendTimeout = v; return this; }
        public Builder receiveTimeout(Duration v) { this.receiveTimeout = v; return this; }
        public Builder retry(Retry v)             { this.retry = v; return this; }
        public Builder logger(PodOsLogger v)      { this.logger = v; return this; }
        public Builder wireHook(WireHook v)       { this.wireHook = v; return this; }

        /**
         * Builds and connects the {@link ConnectionClient}.
         *
         * @throws IOException if the initial connection cannot be established
         */
        public ConnectionClient buildAndConnect() throws IOException {
            ConnectionClient c = new ConnectionClient(this);
            c.connect();
            return c;
        }

        /** Builds without connecting (use {@link ConnectionClient#connect()} separately). */
        public ConnectionClient build() {
            return new ConnectionClient(this);
        }
    }
}
