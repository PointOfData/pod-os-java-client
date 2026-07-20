package com.pointofdata.podos;

import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.connection.ConnectionClient;
import com.pointofdata.podos.connection.ConnectionPool;
import com.pointofdata.podos.connection.Retry;
import com.pointofdata.podos.errors.GatewayDError;
import com.pointofdata.podos.log.NoOpLogger;
import com.pointofdata.podos.log.PodOsLogger;
import com.pointofdata.podos.log.Slf4jLogger;
import com.pointofdata.podos.message.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * High-performance Pod-OS client for the Actor messaging platform.
 *
 * <p>Provides both synchronous and concurrent (non-blocking) send/receive patterns.
 * Capable of sustaining 100K+ messages per second in concurrent mode on modern hardware.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Create a client: {@link #newClient(Config)}</li>
 *   <li>Send messages: {@link #sendMessage(Message, Duration)}</li>
 *   <li>Close when done: {@link #close()}</li>
 * </ol>
 *
 * <h2>Concurrent Mode</h2>
 * When {@link Config#enableConcurrentMode} is {@code true}, a background receiver thread
 * routes responses to waiting callers using {@code MessageId} correlation. Multiple threads
 * can call {@link #sendMessage} simultaneously without serializing on the response.
 *
 * <h2>Registry</h2>
 * Clients are stored in a static registry keyed by {@code clientName:gatewayActorName}.
 * {@link #newClient(Config)} returns an existing connected client if one already exists,
 * preventing duplicate connections.
 */
public class PodOsClient implements AutoCloseable {

    // =========================================================================
    // Static client registry (mirrors Go's clientRegistry + actorRegistry)
    // =========================================================================

    private static final Map<String, PodOsClient> clientRegistry = new ConcurrentHashMap<>();
    private static final Map<String, PodOsClient> actorRegistry  = new ConcurrentHashMap<>();
    private static final ReentrantLock registryLock = new ReentrantLock();

    /**
     * Sentinel error returned when the connection is lost while a request is in flight.
     * Equivalent to Go's {@code ErrConnectionLost}.
     */
    public static final String ERR_CONNECTION_LOST = "connection to gateway was lost during request";

    /** Receive-loop poll interval / liveness check cadence (ms). Fallback when config is null. */
    private static final int DEFAULT_RECEIVE_LOOP_TIMEOUT_MS = 30_000;
    /**
     * Liveness backstop (ms): if requests are pending but no frame has been
     * received for this long, the connection is declared dead even without a hard
     * TCP error. Fallback when config is null.
     */
    private static final long DEFAULT_CONNECTION_LIVENESS_TIMEOUT_MS = 90_000;

    // =========================================================================
    // Instance state
    // =========================================================================

    private final ConnectionClient conn;
    private final ConnectionPool pool;
    private final Config cfg;
    private final String gatewayActorName;
    private final String clientName;
    private final String registryKey;
    private final PodOsLogger logger;

    // Concurrent mode — receiver thread + pending response map
    private final AtomicBoolean receiverActive  = new AtomicBoolean(false);
    private volatile Thread receiverThread;
    private final AtomicBoolean receiverStopped = new AtomicBoolean(false);

    /** messageId → CompletableFuture<byte[]> for pending requests. */
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>(256);

    // Send serialization lock (prevents interleaved writes)
    private final ReentrantLock sendLock = new ReentrantLock();

    // Reconnect state
    private final AtomicBoolean reconnecting     = new AtomicBoolean(false);
    private volatile int        reconnectAttempt = 0;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile CompletableFuture<Boolean> reconnectFuture = null;
    private final Object reconnectFutureLock = new Object();

    // Connection state observer — called on every state transition.
    private volatile BiConsumer<ConnectionState, Exception> stateHandler;

    // Unmatched inbound message handler (concurrent mode only).
    private volatile Consumer<Message> unmatchedHandler;

    // App-level keepalive scheduler
    private ScheduledExecutorService keepaliveExecutor;
    private ScheduledFuture<?> keepaliveFuture;

    // =========================================================================
    // Constructor (private — use newClient())
    // =========================================================================

    private PodOsClient(ConnectionClient conn, ConnectionPool pool, Config cfg, PodOsLogger logger) {
        this.conn              = conn;
        this.pool              = pool;
        this.cfg               = cfg;
        this.gatewayActorName  = cfg.gatewayActorName;
        this.clientName        = cfg.clientName;
        this.registryKey       = makeKey(cfg.clientName, cfg.gatewayActorName);
        this.logger            = logger;
        this.unmatchedHandler  = cfg.unmatchedMessageHandler;
    }

    private int receiveLoopTimeoutMs() {
        if (cfg == null) {
            return DEFAULT_RECEIVE_LOOP_TIMEOUT_MS;
        }
        return (int) cfg.getReceiveLoopTimeout().toMillis();
    }

    private long connectionLivenessTimeoutMs() {
        if (cfg == null) {
            return DEFAULT_CONNECTION_LIVENESS_TIMEOUT_MS;
        }
        return cfg.getConnectionLivenessTimeout().toMillis();
    }

    // =========================================================================
    // Static factory
    // =========================================================================

    /**
     * Creates a new Pod-OS client or returns an existing one if a connected client
     * already exists for {@code clientName + gatewayActorName}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate required config fields.</li>
     *   <li>Check registry for existing connected client.</li>
     *   <li>Establish TCP connection with retry.</li>
     *   <li>Send {@code GatewayId} message and validate response.</li>
     *   <li>Send {@code GatewayStreamOn} (unless disabled).</li>
     *   <li>Start background receiver if {@link Config#enableConcurrentMode} is {@code true}.</li>
     *   <li>Register in static registry.</li>
     * </ol>
     *
     * @param cfg client configuration
     * @return connected client
     * @throws IllegalArgumentException if required config fields are missing
     * @throws IOException              if the connection cannot be established
     */
    public static PodOsClient newClient(Config cfg) throws IOException {
        if (cfg.clientName == null || cfg.clientName.isEmpty()) {
            throw new IllegalArgumentException("clientName is required and cannot be empty");
        }
        if (cfg.gatewayActorName == null || cfg.gatewayActorName.isEmpty()) {
            throw new IllegalArgumentException("gatewayActorName is required and cannot be empty");
        }

        String key = makeKey(cfg.clientName, cfg.gatewayActorName);

        if (!cfg.skipGlobalRegistry) {
            // Fast-path: check registry without lock
            PodOsClient existing = clientRegistry.get(key);
            if (existing != null && existing.isConnected()) {
                return existing;
            }
        }

        // Slow-path: acquire lock and check again (double-checked locking)
        registryLock.lock();
        try {
            if (!cfg.skipGlobalRegistry) {
                PodOsClient existing = clientRegistry.get(key);
                if (existing != null) {
                    if (existing.isConnected()) return existing;
                    // Remove stale entry
                    clientRegistry.remove(key);
                    actorRegistry.remove(cfg.gatewayActorName);
                }
            }

            PodOsLogger logger = resolveLogger(cfg);

            // Build retry config
            Retry retry = new Retry();
            retry.retries           = cfg.retryConfig.retries;
            retry.backoff           = cfg.retryConfig.backoff;
            retry.backoffMultiplier = cfg.retryConfig.backoffMultiplier;
            retry.disableBackoffCaps= cfg.retryConfig.disableBackoffCaps;
            retry.logger            = logger;

            // Establish connection
            ConnectionClient conn = ConnectionClient.builder()
                    .host(cfg.host)
                    .port(cfg.port)
                    .network(cfg.network)
                    .actorName(cfg.gatewayActorName)
                    .dialTimeout(cfg.dialTimeout)
                    .sendTimeout(cfg.sendTimeout)
                    .receiveTimeout(cfg.receiveTimeout)
                    .tcpKeepAliveIdle(cfg.tcpKeepAliveIdle)
                    .tcpKeepAliveInterval(cfg.tcpKeepAliveInterval)
                    .tcpKeepAliveCount(cfg.tcpKeepAliveCount)
                    .retry(retry)
                    .logger(logger)
                    .wireHook(cfg.wireHook)
                    .buildAndConnect();

            PodOsClient client = new PodOsClient(conn, createPoolIfConfigured(cfg, retry, logger), cfg, logger);

            // Send GatewayId handshake
            client.sendGatewayId();

            // Send GatewayStreamOn (default enabled)
            boolean enableStreaming = cfg.enableStreaming == null || cfg.enableStreaming;
            if (enableStreaming) {
                client.sendStreamOn();
            } else {
                logger.info("streaming disabled, skipping STREAM ON", "actor", cfg.gatewayActorName);
            }

            // Start background receiver if concurrent mode is enabled
            if (cfg.enableConcurrentMode) {
                client.startReceiver();
            }

            client.startKeepaliveLoop();

            if (cfg.skipGlobalRegistry) {
                logger.info("created unregistered client (warm-pool standby)", "key", key);
                return client;
            }

            // Register
            clientRegistry.put(key, client);
            actorRegistry.put(cfg.gatewayActorName, client);
            logger.info("registered new client", "key", key);

            return client;

        } finally {
            registryLock.unlock();
        }
    }

    // =========================================================================
    // Connection handshake
    // =========================================================================

    private void sendGatewayId() throws IOException {
        String conversationUUID = UUID.randomUUID().toString();
        Message idMsg = new Message();
        idMsg.to         = "$system@" + gatewayActorName;
        idMsg.from       = clientName + "@" + gatewayActorName;
        idMsg.intent     = IntentTypes.INSTANCE.GatewayId;
        idMsg.clientName = clientName;
        idMsg.passcode   = cfg.passcode != null ? cfg.passcode : "";
        idMsg.messageId  = UUID.randomUUID().toString();
        idMsg.event      = new EventFields();
        idMsg.event.owner             = "$sys";
        idMsg.event.timestamp         = MessageUtils.getTimestamp();
        idMsg.event.locationSeparator = "|";

        SocketMessage socketMsg = MessageEncoder.encodeMessage(idMsg, conversationUUID);
        sendRaw(socketMsg.messageBytes);
        logger.info("GatewayId sent", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);

        // Wait for ID response
        int timeoutMs = (int) (cfg.receiveTimeout.toMillis() > 0 ? cfg.receiveTimeout.toMillis() : 10_000);
        byte[] responseBytes;
        try {
            responseBytes = conn.receive(timeoutMs);
        } catch (GatewayDError e) {
            conn.close();
            throw new IOException("failed to receive GatewayId response: " + e.getMessage(), e);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            conn.close();
            throw new IOException("received empty GatewayId response from " + gatewayActorName);
        }
        Message idResponse = MessageDecoder.decodeMessage(responseBytes);
        if ("ERROR".equals(idResponse.processingStatus())) {
            conn.close();
            throw new IOException("GatewayId rejected by gateway: " + idResponse.processingMessage());
        }
        logger.info("GatewayId accepted", "actor", gatewayActorName, "status", idResponse.processingStatus());
    }

    private void sendStreamOn() throws IOException {
        Message streamOnMsg = new Message();
        streamOnMsg.to         = "$system@" + gatewayActorName;
        streamOnMsg.from       = clientName + "@" + gatewayActorName;
        streamOnMsg.intent     = IntentTypes.INSTANCE.GatewayStreamOn;
        streamOnMsg.clientName = clientName;
        streamOnMsg.passcode   = cfg.passcode != null ? cfg.passcode : "";
        streamOnMsg.messageId  = UUID.randomUUID().toString();
        SocketMessage socketMsg = MessageEncoder.encodeMessage(streamOnMsg, UUID.randomUUID().toString());
        sendRaw(socketMsg.messageBytes);
        logger.info("GatewayStreamOn sent", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);
    }

    private void sendDisconnect() throws IOException {
        if (!isConnected()) return;
        SocketMessage socketMsg = encodeDisconnect();
        sendLock.lock();
        try {
            if (!isConnected()) return;
            sendRaw(socketMsg.messageBytes);
        } finally {
            sendLock.unlock();
        }
        if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
            logger.debug("disconnect sent", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);
        }
    }

    // =========================================================================
    // Public send methods
    // =========================================================================

    /**
     * Sends a message to a Pod-OS actor and waits for the response.
     *
     * <p>Automatically corrects {@link Message#clientName} and {@link Message#from}
     * to match this client's identity. Auto-generates {@link Message#messageId} if empty.
     *
     * <p>When concurrent mode is active, uses MessageId correlation for response routing,
     * allowing multiple concurrent callers.
     *
     * @param msg     the message to send
     * @param timeout response timeout (null = use {@link Config#responseTimeout})
     * @return decoded response message
     * @throws IOException if encoding, sending, receiving, or decoding fails
     */
    public Message sendMessage(Message msg, Duration timeout) throws IOException {
        normalizeMessage(msg);
        Duration effectiveTimeout = timeout != null ? timeout : cfg.responseTimeout;
        if (receiverActive.get()) {
            return sendWithCorrelation(msg, effectiveTimeout);
        }
        return sendSync(msg, effectiveTimeout);
    }

    /**
     * Sends a message and returns both the decoded response and the raw wire bytes.
     *
     * @param msg     the message to send
     * @param timeout response timeout
     * @return array of size 2: [Message, byte[]]
     * @throws IOException if anything fails
     */
    public Object[] sendMessageWithRaw(Message msg, Duration timeout) throws IOException {
        normalizeMessage(msg);
        Duration effectiveTimeout = timeout != null ? timeout : cfg.responseTimeout;
        byte[] rawResponse;
        if (receiverActive.get()) {
            rawResponse = sendWithCorrelationRaw(msg, effectiveTimeout);
        } else {
            rawResponse = sendSyncRaw(msg, effectiveTimeout);
        }
        Message decoded = MessageDecoder.decodeMessage(rawResponse);
        checkResponseError(decoded);
        return new Object[]{decoded, rawResponse};
    }

    /**
     * Sends a control message (fire-and-forget, no response expected).
     *
     * @param socketMsg pre-encoded message bytes
     * @throws IOException if send fails
     */
    public void sendControlMessage(SocketMessage socketMsg) throws IOException {
        if (!isConnected()) throw new IOException("connection closed before sending control message");
        sendLock.lock();
        try {
            if (!isConnected()) throw new IOException("connection closed before sending control message");
            sendRaw(socketMsg.messageBytes);
        } finally {
            sendLock.unlock();
        }
        logger.info("control message sent", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);
    }

    /**
     * Sends an app-level AIP Keepalive (message_type 18) on the primary connection.
     * Fire-and-forget; no response is expected.
     */
    public void sendKeepalive() throws IOException {
        SocketMessage socketMsg = encodeKeepalive();
        sendLock.lock();
        try {
            if (!isConnected()) throw new IOException("connection closed before sending keepalive");
            sendRaw(socketMsg.messageBytes);
        } finally {
            sendLock.unlock();
        }
        if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
            logger.debug("keepalive sent", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);
        }
    }

    private Message buildKeepaliveMessage() {
        Message msg = new Message();
        msg.to         = "$system@" + gatewayActorName;
        msg.from       = clientName + "@" + gatewayActorName;
        msg.intent     = IntentTypes.INSTANCE.Keepalive;
        msg.clientName = clientName;
        msg.messageId  = UUID.randomUUID().toString();
        return msg;
    }

    private SocketMessage encodeKeepalive() {
        return encode(buildKeepaliveMessage());
    }

    private Message buildDisconnectMessage() {
        Message msg = new Message();
        msg.to         = "$system@" + gatewayActorName;
        msg.from       = clientName + "@" + gatewayActorName;
        msg.intent     = IntentTypes.INSTANCE.GatewayDisconnect;
        msg.clientName = clientName;
        msg.messageId  = UUID.randomUUID().toString();
        return msg;
    }

    private SocketMessage encodeDisconnect() {
        return encode(buildDisconnectMessage());
    }

    private void startKeepaliveLoop() {
        Duration interval = cfg.getKeepaliveInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }

        keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "podos-keepalive-" + gatewayActorName);
            t.setDaemon(true);
            return t;
        });
        long periodMs = interval.toMillis();
        keepaliveFuture = keepaliveExecutor.scheduleAtFixedRate(() -> {
            if (closed.get() || !isConnected() || reconnecting.get()) {
                return;
            }
            try {
                sendKeepalive();
                sendPoolKeepalives();
            } catch (Exception e) {
                if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                    logger.debug("keepalive send failed", "actor", gatewayActorName, "error", e.getMessage());
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopKeepaliveLoop() {
        if (keepaliveFuture != null) {
            keepaliveFuture.cancel(false);
            keepaliveFuture = null;
        }
        if (keepaliveExecutor != null) {
            keepaliveExecutor.shutdownNow();
            keepaliveExecutor = null;
        }
    }

    private void sendPoolKeepalives() {
        if (pool == null || pool.isClosed()) {
            return;
        }
        final byte[] wire;
        try {
            wire = encodeKeepalive().messageBytes;
        } catch (RuntimeException e) {
            if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                logger.debug("encode pool keepalive failed", "error", e.getMessage());
            }
            return;
        }
        pool.pingIdleConnections(client -> {
            try {
                client.send(wire);
            } catch (GatewayDError e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ConnectionPool createPoolIfConfigured(Config cfg, Retry retry, PodOsLogger logger) throws IOException {
        if (cfg.poolConfig == null || cfg.poolConfig.maxCapacity <= 0) {
            return null;
        }
        return new ConnectionPool(cfg.poolConfig, () -> ConnectionClient.builder()
                .host(cfg.host)
                .port(cfg.port)
                .network(cfg.network)
                .actorName(cfg.gatewayActorName)
                .dialTimeout(cfg.dialTimeout)
                .sendTimeout(cfg.sendTimeout)
                .receiveTimeout(cfg.receiveTimeout)
                .retry(retry)
                .logger(logger)
                .wireHook(cfg.wireHook)
                .buildAndConnect());
    }

    // =========================================================================
    // Synchronous send-then-receive
    // =========================================================================

    private Message sendSync(Message msg, Duration timeout) throws IOException {
        try {
            byte[] raw = doSendSyncRaw(msg, timeout);
            Message response = MessageDecoder.decodeMessage(raw);
            checkResponseError(response);
            return response;
        } catch (IOException e) {
            if (cfg.reconnectConfig.isEnabled() && isConnectionLost(e)) {
                logger.info("sync send failed with connection error, attempting reconnection",
                        "actor", gatewayActorName, "error", e.getMessage());
                if (attemptReconnection(e)) {
                    byte[] raw = doSendSyncRaw(msg, timeout);
                    Message response = MessageDecoder.decodeMessage(raw);
                    checkResponseError(response);
                    return response;
                }
            }
            throw e;
        }
    }

    private byte[] sendSyncRaw(Message msg, Duration timeout) throws IOException {
        try {
            return doSendSyncRaw(msg, timeout);
        } catch (IOException e) {
            if (cfg.reconnectConfig.isEnabled() && isConnectionLost(e)) {
                logger.info("sync send (raw) failed with connection error, attempting reconnection",
                        "actor", gatewayActorName, "error", e.getMessage());
                if (attemptReconnection(e)) {
                    return doSendSyncRaw(msg, timeout);
                }
            }
            throw e;
        }
    }

    private byte[] doSendSyncRaw(Message msg, Duration timeout) throws IOException {
        SocketMessage socketMsg = encode(msg);
        if (!isConnected()) {
            if (cfg.reconnectConfig.isEnabled()) {
                if (!waitForReconnect(timeout)) {
                    throw new IOException(ERR_CONNECTION_LOST);
                }
            } else {
                throw new IOException("connection closed before sending message");
            }
        }
        sendLock.lock();
        try { sendRaw(socketMsg.messageBytes); }
        finally { sendLock.unlock(); }
        logger.info("sent message", "actor", gatewayActorName, "bytes", socketMsg.messageBytes.length);
        if (!isConnected()) throw new IOException("connection closed before receiving response");
        int timeoutMs = (int) timeout.toMillis();
        try {
            return conn.receive(timeoutMs);
        } catch (GatewayDError e) {
            throw new IOException("receive failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Concurrent send with MessageId correlation
    // =========================================================================

    private Message sendWithCorrelation(Message msg, Duration timeout) throws IOException {
        byte[] raw = sendWithCorrelationRaw(msg, timeout);
        Message response = MessageDecoder.decodeMessage(raw);
        checkResponseError(response);
        return response;
    }

    private byte[] sendWithCorrelationRaw(Message msg, Duration timeout) throws IOException {
        String messageId = msg.messageId;
        if (messageId == null || messageId.isEmpty()) {
            messageId = UUID.randomUUID().toString();
            msg.messageId = messageId;
        }

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(messageId, future);
        try {
            SocketMessage socketMsg = encode(msg);
            if (!isConnected()) {
                if (cfg.reconnectConfig.isEnabled()) {
                    if (!waitForReconnect(timeout)) {
                        throw new IOException(ERR_CONNECTION_LOST);
                    }
                } else {
                    throw new IOException("connection closed before sending message");
                }
            }
            sendLock.lock();
            try { sendRaw(socketMsg.messageBytes); }
            finally { sendLock.unlock(); }
            logger.info("sent message", "actor", gatewayActorName,
                    "bytes", socketMsg.messageBytes.length, "message_id", messageId);

            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new IOException("request timed out waiting for response [MessageId: " + messageId
                        + "] to " + gatewayActorName, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException("request failed: " + cause.getMessage(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("request interrupted [MessageId: " + messageId + "]", e);
            } catch (CancellationException e) {
                throw new IOException(ERR_CONNECTION_LOST);
            }
        } finally {
            pending.remove(messageId);
        }
    }

    // =========================================================================
    // Background receiver loop
    // =========================================================================

    /**
     * Starts the background receiver thread for concurrent message handling.
     * Automatically called when {@link Config#enableConcurrentMode} is {@code true}.
     */
    public void startReceiver() {
        if (receiverActive.compareAndSet(false, true)) {
            receiverStopped.set(false);
            receiverThread = new Thread(this::receiveLoop, "podos-receiver-" + gatewayActorName);
            receiverThread.setDaemon(true);
            receiverThread.start();
            logger.info("started background receiver", "actor", gatewayActorName);
        }
    }

    /**
     * Stops the background receiver thread. Any pending requests will receive
     * a {@link CancellationException} (mapped to {@link #ERR_CONNECTION_LOST}).
     */
    public void stopReceiver() {
        if (!receiverActive.compareAndSet(true, false)) return;
        receiverStopped.set(true);
        Thread t = receiverThread;
        if (t != null) {
            t.interrupt();
            try { t.join(5_000); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        cancelAllPending();
        logger.info("stopped background receiver", "actor", gatewayActorName);
    }

    private void receiveLoop() {
        // Liveness backstop: if requests are pending but no frame arrives for
        // CONNECTION_LIVENESS_TIMEOUT_MS, the connection is declared dead even
        // without a hard TCP error.
        long lastActivity = System.currentTimeMillis();
        while (receiverActive.get() && !Thread.currentThread().isInterrupted()) {
            if (!isConnected()) {
                // The transport may have been marked dead by a sender's failed
                // write; fail in-flight callers, then reconnect.
                emitState(ConnectionState.DISCONNECTED, null);
                cancelAllPending();
                if (cfg.reconnectConfig.isEnabled() && attemptReconnection(null)) {
                    lastActivity = System.currentTimeMillis();
                    continue;
                }
                logger.info("connection lost, receiver exiting", "actor", gatewayActorName);
                break;
            }

            byte[] responseBytes;
            try {
                responseBytes = conn.receive(receiveLoopTimeoutMs());
            } catch (GatewayDError e) {
                if (!receiverActive.get()) break; // shutting down

                // Benign idle timeout: still alive unless we have pending requests
                // and have heard nothing for too long (liveness backstop).
                if (e.isIdleTimeout()) {
                    if (pending.isEmpty()
                            || System.currentTimeMillis() - lastActivity <= connectionLivenessTimeoutMs()) {
                        continue;
                    }
                    logger.error("liveness timeout: pending requests with no frames received; treating connection as dead",
                            "actor", gatewayActorName);
                } else {
                    logger.error("connection error in receiver", "actor", gatewayActorName, "error", e.getMessage());
                }

                // Fatal: fail all in-flight callers fast, then reconnect.
                emitState(ConnectionState.DISCONNECTED, e);
                cancelAllPending();
                if (cfg.reconnectConfig.isEnabled() && attemptReconnection(e)) {
                    lastActivity = System.currentTimeMillis();
                    continue;
                }
                break;
            }

            lastActivity = System.currentTimeMillis();
            if (responseBytes == null || responseBytes.length == 0) continue;

            // Decode enough to get the MessageId for routing
            String messageId;
            try {
                // Fast path: extract _msg_id from header without full decode
                messageId = extractMessageId(responseBytes);
            } catch (Exception e) {
                logger.warn("failed to extract messageId from response", "actor", gatewayActorName);
                continue;
            }

            if (messageId == null || messageId.isEmpty()) {
                logger.warn("received response without MessageId", "actor", gatewayActorName);
                continue;
            }

            CompletableFuture<byte[]> future = pending.get(messageId);
            if (future != null) {
                future.complete(responseBytes);
                if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                    logger.debug("routed response to caller", "message_id", messageId);
                }
            } else {
                Consumer<Message> handler = unmatchedHandler;
                if (handler != null) {
                    try {
                        Message decoded = MessageDecoder.decodeMessage(responseBytes);
                        Thread dispatch = new Thread(
                                () -> handler.accept(decoded),
                                "podos-unmatched-" + messageId);
                        dispatch.setDaemon(true);
                        dispatch.start();
                    } catch (Exception e) {
                        logger.warn("failed to decode unmatched message",
                                "message_id", messageId, "error", e.getMessage());
                    }
                } else if (logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                    logger.debug("no pending request for MessageId", "message_id", messageId);
                }
            }
        }
        receiverActive.set(false);
        // Ensure no caller is left hanging when the loop exits for any reason.
        cancelAllPending();
        logger.info("receiver loop exited", "actor", gatewayActorName);
    }

    private void cancelAllPending() {
        for (Map.Entry<String, CompletableFuture<byte[]>> entry : pending.entrySet()) {
            // Fail with a retryable connection-lost error so callers fail fast
            // (and can detect it via isConnectionLost) instead of blocking.
            entry.getValue().completeExceptionally(new IOException(ERR_CONNECTION_LOST));
        }
        pending.clear();
    }

    // =========================================================================
    // Reconnection
    // =========================================================================

    /**
     * Attempts to reconnect to the gateway with exponential backoff.
     *
     * @param triggerErr the error that caused the reconnection attempt; forwarded to the
     *                   {@link ConnectionState#RECONNECTING} callback. May be null.
     * @return true if reconnection and re-authentication succeeded, false otherwise
     */
    public boolean attemptReconnection(Exception triggerErr) {
        if (closed.get()) return false;

        if (!reconnecting.compareAndSet(false, true)) {
            return waitForReconnect(cfg.responseTimeout != null ? cfg.responseTimeout : Duration.ofSeconds(30));
        }

        reconnectAttempt = 0;

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        synchronized (reconnectFutureLock) {
            reconnectFuture = future;
        }

        emitState(ConnectionState.RECONNECTING, triggerErr);

        Exception lastErr = null;
        try {
            int maxRetries = cfg.reconnectConfig.maxRetries;
            boolean unlimited = maxRetries == 0;
            Duration backoff = cfg.reconnectConfig.getInitialBackoff();
            double multiplier = cfg.reconnectConfig.getBackoffMultiplier();
            Duration maxBackoff = cfg.reconnectConfig.getMaxBackoff();

            for (int attempt = 1; unlimited || attempt <= maxRetries; attempt++) {
                if (closed.get() || receiverStopped.get() || Thread.currentThread().isInterrupted()) {
                    future.complete(false);
                    emitState(ConnectionState.RECONNECT_FAILED, lastErr);
                    return false;
                }
                reconnectAttempt = attempt;
                logger.info("reconnect attempt", "attempt", attempt, "max", maxRetries, "actor", gatewayActorName, "backoff", backoff);

                try {
                    conn.reconnect();
                    reAuthenticate();
                    logger.info("reconnection successful", "actor", gatewayActorName);
                    future.complete(true);
                    emitState(ConnectionState.CONNECTED, null);
                    return true;
                } catch (Exception e) {
                    lastErr = e;
                    logger.warn("reconnect attempt failed", "attempt", attempt, "actor", gatewayActorName, "error", e.getMessage());
                }

                try { Thread.sleep(backoff.toMillis()); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    future.complete(false);
                    emitState(ConnectionState.RECONNECT_FAILED, lastErr);
                    return false;
                }

                long nextMs = (long) (backoff.toMillis() * multiplier);
                if (nextMs > maxBackoff.toMillis()) nextMs = maxBackoff.toMillis();
                backoff = Duration.ofMillis(nextMs);
            }
            logger.warn("max reconnection attempts reached", "actor", gatewayActorName);
            future.complete(false);
            emitState(ConnectionState.RECONNECT_FAILED, lastErr);
            return false;
        } finally {
            reconnecting.set(false);
        }
    }

    /** Backwards-compatible overload for callers without a trigger error. */
    public boolean attemptReconnection() {
        return attemptReconnection(null);
    }

    private void reAuthenticate() throws IOException {
        sendGatewayId();
        boolean enableStreaming = cfg.enableStreaming == null || cfg.enableStreaming;
        if (enableStreaming) sendStreamOn();
    }

    // =========================================================================
    // Registry access
    // =========================================================================

    /**
     * Returns the registered client for the given gateway actor name, or null.
     * Equivalent to Go's {@code GetClientByGatewayActorName}.
     */
    public static PodOsClient getClientByGatewayActorName(String gatewayActorName) {
        return actorRegistry.get(gatewayActorName);
    }

    /**
     * Registers a client in the registry.
     * Equivalent to Go's {@code RegisterClient}.
     */
    public static void registerClient(PodOsClient client) {
        if (client == null) throw new IllegalArgumentException("cannot register null client");
        if (client.gatewayActorName == null || client.gatewayActorName.isEmpty())
            throw new IllegalArgumentException("client gatewayActorName cannot be empty");
        registryLock.lock();
        try {
            clientRegistry.put(client.registryKey, client);
            actorRegistry.put(client.gatewayActorName, client);
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Removes a client from the registry by gateway actor name.
     * Does not close the client.
     * Equivalent to Go's {@code RemoveClientByGatewayActorName}.
     */
    public static void removeClientByGatewayActorName(String gatewayActorName) {
        registryLock.lock();
        try {
            PodOsClient client = actorRegistry.remove(gatewayActorName);
            if (client != null) clientRegistry.remove(client.registryKey);
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Returns the number of registered clients.
     * Equivalent to Go's {@code GetClientCount}.
     */
    public static int getClientCount() {
        return actorRegistry.size();
    }

    // =========================================================================
    // State
    // =========================================================================

    /** Returns true if the underlying TCP connection is alive. */
    public boolean isConnected() {
        return conn != null && conn.isConnected();
    }

    /** Returns true if a reconnection is currently in progress. */
    public boolean isReconnecting() {
        return reconnecting.get();
    }

    /** Returns the current reconnect attempt number (0 if not reconnecting). */
    public int getReconnectAttempt() {
        return reconnectAttempt;
    }

    /** Returns true if the background receiver thread is active. */
    public boolean isReceiverActive() {
        return receiverActive.get();
    }

    public String getClientName()       { return clientName; }
    public String getGatewayActorName() { return gatewayActorName; }
    public PodOsLogger getLogger()      { return logger; }

    /** Returns true if this client has been permanently closed. */
    public boolean isClosed() { return closed.get(); }

    /** Returns the underlying {@link ConnectionClient} for direct socket operations. */
    public ConnectionClient getConn()   { return conn; }

    /**
     * Registers a callback that fires on every connection state transition.
     * The error parameter is:
     * <ul>
     *   <li>{@link ConnectionState#DISCONNECTED}: the error that caused the disconnect.</li>
     *   <li>{@link ConnectionState#RECONNECTING}: the trigger error (may be null).</li>
     *   <li>{@link ConnectionState#CONNECTED}: null (reconnect succeeded).</li>
     *   <li>{@link ConnectionState#RECONNECT_FAILED}: the last reconnect attempt error.</li>
     * </ul>
     *
     * <p>Note: {@link ConnectionState#CONNECTED} is not emitted for the initial connection
     * in {@link #newClient(Config)} because no handler can be registered before the
     * constructor returns. It is only emitted after a successful reconnect.
     *
     * <p>The callback is invoked synchronously so it should be fast and non-blocking.
     * Safe to call before or after {@link #startReceiver()}.
     *
     * @param fn the state change handler
     */
    public void onConnectionStateChange(BiConsumer<ConnectionState, Exception> fn) {
        this.stateHandler = fn;
    }

    /**
     * Registers a handler invoked (on a new thread) for every message received by the
     * background receiver that does not match any pending outbound request.
     * Must be called before {@link #startReceiver()} (or before {@link #newClient(Config)}
     * when {@link Config#enableConcurrentMode} is true) to avoid missing early messages.
     */
    public void setUnmatchedMessageHandler(Consumer<Message> fn) {
        this.unmatchedHandler = fn;
    }

    private void emitState(ConnectionState state, Exception err) {
        BiConsumer<ConnectionState, Exception> fn = stateHandler;
        if (fn != null) {
            fn.accept(state, err);
        }
    }

    /**
     * Blocks until the client is connected or the timeout expires.
     * Returns true if the connection was restored, false otherwise.
     */
    private boolean waitForReconnect(Duration timeout) {
        if (isConnected()) return true;

        CompletableFuture<Boolean> future;
        synchronized (reconnectFutureLock) {
            future = reconnectFuture;
        }
        if (future == null) return false;

        try {
            Boolean result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return result != null && result && isConnected();
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            return false;
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Closes this client, stops the background receiver, and removes it from the registry.
     */
    @Override
    public void close() {
        closed.set(true);
        synchronized (reconnectFutureLock) {
            CompletableFuture<Boolean> future = reconnectFuture;
            if (future != null && !future.isDone()) {
                future.complete(false);
            }
        }

        if (receiverActive.get()) stopReceiver();
        else receiverStopped.set(true);

        stopKeepaliveLoop();

        registryLock.lock();
        try {
            clientRegistry.remove(registryKey);
            actorRegistry.remove(gatewayActorName);
        } finally {
            registryLock.unlock();
        }
        if (pool != null) {
            try { pool.close(); } catch (Exception ignored) {}
        }
        if (conn != null) {
            try {
                sendDisconnect();
            } catch (IOException e) {
                logger.warn("failed to send GatewayDisconnect before close",
                        "error", e.getMessage(), "actor", gatewayActorName);
            }
            conn.close();
        }
        logger.info("client closed", "key", registryKey, "actor", gatewayActorName);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void normalizeMessage(Message msg) {
        if (msg.clientName == null || !msg.clientName.equals(clientName)) {
            msg.clientName = clientName;
        }
        if (msg.from != null && msg.from.contains("@")) {
            String[] parts = msg.from.split("@", 2);
            String expected = clientName + "@" + parts[1];
            if (!msg.from.equals(expected)) msg.from = expected;
        }
        if (msg.messageId == null || msg.messageId.isEmpty()) {
            msg.messageId = UUID.randomUUID().toString();
        }
    }

    private SocketMessage encode(Message msg) {
        try {
            return MessageEncoder.encodeMessage(msg, UUID.randomUUID().toString());
        } catch (Exception e) {
            throw new RuntimeException("failed to encode message: " + e.getMessage(), e);
        }
    }

    private void sendRaw(byte[] data) throws IOException {
        try {
            conn.send(data);
        } catch (GatewayDError e) {
            throw new IOException("send failed: " + e.getMessage(), e);
        }
    }

    private static void checkResponseError(Message msg) throws IOException {
        if ("ERROR".equals(msg.processingStatus())) {
            String errMsg = msg.processingMessage();
            throw new IOException(errMsg != null && !errMsg.isEmpty() ? errMsg : "unknown error from Pod-OS actor");
        }
    }

    /**
     * Extracts _msg_id from the wire header bytes without full decode.
     * Fast path for routing in the concurrent receiver loop.
     */
    private static String extractMessageId(byte[] raw) {
        if (raw == null || raw.length < 63) return null;
        try {
            // Parse field positions from length prefix
            long toLen     = decodeLengthFromBytes(raw, 9);
            long fromLen   = decodeLengthFromBytes(raw, 18);
            long headerLen = decodeLengthFromBytes(raw, 27);
            int headerStart = (int) (63 + toLen + fromLen);
            int headerEnd   = (int) (headerStart + headerLen);
            if (headerEnd > raw.length) return null;
            String header = new String(raw, headerStart, (int) headerLen, java.nio.charset.StandardCharsets.UTF_8);
            // Scan for _msg_id= in header (tab-separated)
            for (String field : header.split("\t", -1)) {
                if (field.startsWith("_msg_id=")) {
                    return field.substring("_msg_id=".length()).trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long decodeLengthFromBytes(byte[] data, int offset) {
        String s = new String(data, offset, 9, java.nio.charset.StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) return 0;
        if (s.charAt(0) == 'x') return Long.parseLong(s.substring(1), 16);
        return Long.parseLong(s, 10);
    }

    /**
     * Typed connection-loss classification (replaces substring matching): walks the
     * cause chain for a {@link GatewayDError} flagged as connection-lost, or an
     * IOException carrying the {@link #ERR_CONNECTION_LOST} sentinel message.
     */
    private static boolean isConnectionLost(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof GatewayDError && ((GatewayDError) cur).isConnectionLost()) {
                return true;
            }
            if (cur instanceof IOException && ERR_CONNECTION_LOST.equals(cur.getMessage())) {
                return true;
            }
            if (cur.getCause() == cur) break; // guard against self-referential cause
        }
        return false;
    }

    private static String makeKey(String clientName, String actorName) {
        return clientName + ":" + actorName;
    }

    private static PodOsLogger resolveLogger(Config cfg) {
        if (cfg.logger != null) return cfg.logger;
        if (cfg.logLevel > 0) {
            PodOsLogger.Level level = PodOsLogger.Level.fromInt(cfg.logLevel);
            return Slf4jLogger.forName("com.pointofdata.podos.PodOsClient", level);
        }
        return NoOpLogger.INSTANCE;
    }
}
