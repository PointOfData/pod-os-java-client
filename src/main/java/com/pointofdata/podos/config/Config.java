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

    // -------------------------------------------------------------------------
    // Reconnection
    // -------------------------------------------------------------------------

    /** Automatic reconnection configuration. */
    public ReconnectConfig reconnectConfig = new ReconnectConfig();

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

    public Config() {}
}
