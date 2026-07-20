package com.pointofdata.podos.config;

import com.pointofdata.podos.connection.WireHook;
import com.pointofdata.podos.log.PodOsLogger;

import java.time.Duration;

/**
 * Complete configuration for a Pod-OS client connection.
 * Mirrors Go's {@code config.Config} struct.
 *
 * <p>Usage:
 * <pre>{@code
 * Config cfg = new Config();
 * cfg.host = "zeroth.pod-os.com";
 * cfg.port = "62312";
 * cfg.gatewayActorName = "zeroth.pod-os.com";
 * cfg.clientName = "my-java-client";
 * cfg.enableConcurrentMode = true;
 * }</pre>
 */
public class Config {

    // -------------------------------------------------------------------------
    // Network / Connection
    // -------------------------------------------------------------------------

    /** Network protocol: "tcp", "udp", or "unix". Default: "tcp". */
    public String network = "tcp";
    /** Gateway hostname or IP address. */
    public String host = "";
    /** Gateway port. Default: "62312". */
    public String port = "62312";
    /**
     * Name of the gateway Actor (e.g. "zeroth.pod-os.com").
     * Used as the {@code @GatewayName} part of {@code To} / {@code From} addresses.
     * Required.
     */
    public String gatewayActorName = "";

    // -------------------------------------------------------------------------
    // Identity / Auth
    // -------------------------------------------------------------------------

    /**
     * Unique client identifier for this connection. Required.
     * Used in the GatewayId message and all subsequent messages.
     */
    public String clientName = "";
    /** Optional passcode for authentication. */
    public String passcode = "";

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    /** Retry configuration for initial connection attempts. */
    public RetryConfig retryConfig = new RetryConfig();

    // -------------------------------------------------------------------------
    // Timeouts
    // -------------------------------------------------------------------------

    /** TCP dial timeout. Default: 5 seconds. */
    public Duration dialTimeout = Duration.ofSeconds(5);
    /** Timeout for receive operations. Default: 5 seconds. */
    public Duration receiveTimeout = Duration.ofSeconds(5);
    /** Timeout for send operations. Default: 5 seconds. */
    public Duration sendTimeout = Duration.ofSeconds(5);

    // -------------------------------------------------------------------------
    // Connection Pool
    // -------------------------------------------------------------------------

    /** Connection pool configuration. {@code maxCapacity = 0} disables pooling. */
    public PoolConfig poolConfig = new PoolConfig();

    // -------------------------------------------------------------------------
    // Streaming
    // -------------------------------------------------------------------------

    /**
     * Whether to enable streaming mode by sending {@code STREAM_ON} after connect.
     * {@code null} (default) = enabled; {@code false} = disabled.
     * Equivalent to Go's {@code *bool} with nil = enabled.
     */
    public Boolean enableStreaming = null;

    // -------------------------------------------------------------------------
    // Concurrent Mode
    // -------------------------------------------------------------------------

    /**
     * When true, starts a background receiver goroutine and uses MessageId correlation
     * to route responses to callers. Allows multiple concurrent {@code sendMessage} calls.
     */
    public boolean enableConcurrentMode = false;

    /** Response timeout for concurrent mode. Default: 30 seconds. */
    public Duration responseTimeout = Duration.ofSeconds(30);

    /**
     * Invoked for inbound messages that do not match a pending outbound request when
     * {@link #enableConcurrentMode} is true. Wired before {@code startReceiver} in
     * {@link com.pointofdata.podos.PodOsClient#newClient(Config)}.
     */
    public java.util.function.Consumer<com.pointofdata.podos.message.Message> unmatchedMessageHandler = null;

    // -------------------------------------------------------------------------
    // Reconnection
    // -------------------------------------------------------------------------

    /** Automatic reconnection configuration. */
    public ReconnectConfig reconnectConfig = new ReconnectConfig();

    /**
     * App-level AIP Keepalive interval. When unset (null), defaults to 30 seconds.
     * Zero disables keepalive. Negative values disable keepalive.
     */
    public Duration keepaliveInterval = null;

    /**
     * When true, prevents storing this client in the global clientRegistry/actorRegistry.
     * Used by warm-pool standby connections so they do not clobber the canonical primary
     * client for the same gateway FQN.
     */
    public boolean skipGlobalRegistry = false;

    /**
     * Bounds each background receive iteration in concurrent mode.
     * When unset (null or zero), defaults to 30 seconds.
     */
    public Duration receiveLoopTimeout = null;

    /**
     * Liveness backstop: if requests are pending but no frame has been received for
     * this long, the connection is declared dead. When unset (null or zero), defaults
     * to 90 seconds.
     */
    public Duration connectionLivenessTimeout = null;

    /** TCP keepalive idle time. When unset (null or zero), uses connection-layer default (15s). */
    public Duration tcpKeepAliveIdle = null;

    /** TCP keepalive probe interval. When unset (null or zero), uses default (5s). */
    public Duration tcpKeepAliveInterval = null;

    /** TCP keepalive probe count before declaring dead. When zero, uses default (3). */
    public int tcpKeepAliveCount = 0;

    /**
     * TCP user timeout (Linux TCP_USER_TIMEOUT). When unset (null or zero), uses default (60s).
     * Not supported on all platforms; ignored where unavailable.
     */
    public Duration tcpUserTimeout = null;

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    /**
     * Log level: 0 = disabled (zero overhead), 1 = ERROR, 2 = WARN, 3 = INFO, 4 = DEBUG.
     * Ignored if {@link #logger} is set.
     */
    public int logLevel = 0;

    /**
     * Custom logger implementation. If null and logLevel > 0, a SLF4J-backed logger
     * is created automatically.
     */
    public PodOsLogger logger = null;

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------

    /** Whether OpenTelemetry tracing is enabled. */
    public boolean enableTracing = false;
    /** Tracer name for OpenTelemetry spans. */
    public String tracerName = "";

    /**
     * Optional wire-frame observer. Called after every successful Send and Receive.
     * Set to null (default) for zero overhead. Implementations must be thread-safe.
     */
    public WireHook wireHook = null;

    /** Default app-level AIP Keepalive period. */
    public static final Duration DEFAULT_KEEPALIVE_INTERVAL = Duration.ofSeconds(30);

    /** Default per-iteration receive timeout in concurrent mode. */
    public static final Duration DEFAULT_RECEIVE_LOOP_TIMEOUT = Duration.ofSeconds(30);

    /** Default pending-request liveness backstop. */
    public static final Duration DEFAULT_CONNECTION_LIVENESS_TIMEOUT = Duration.ofSeconds(90);

    /**
     * Returns the configured keepalive interval, or the default when unset.
     * Returns {@link Duration#ZERO} when keepalive is explicitly disabled.
     */
    public Duration getKeepaliveInterval() {
        if (keepaliveInterval != null) {
            if (keepaliveInterval.isNegative() || keepaliveInterval.isZero()) {
                return Duration.ZERO;
            }
            return keepaliveInterval;
        }
        return DEFAULT_KEEPALIVE_INTERVAL;
    }

    /** Returns the configured receive-loop timeout or the default. */
    public Duration getReceiveLoopTimeout() {
        if (receiveLoopTimeout == null || receiveLoopTimeout.isZero() || receiveLoopTimeout.isNegative()) {
            return DEFAULT_RECEIVE_LOOP_TIMEOUT;
        }
        return receiveLoopTimeout;
    }

    /** Returns the configured liveness backstop or the default. */
    public Duration getConnectionLivenessTimeout() {
        if (connectionLivenessTimeout == null || connectionLivenessTimeout.isZero()
                || connectionLivenessTimeout.isNegative()) {
            return DEFAULT_CONNECTION_LIVENESS_TIMEOUT;
        }
        return connectionLivenessTimeout;
    }

    public Config() {}
}
