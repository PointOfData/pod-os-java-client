package com.pointofdata.podos.config;

import java.time.Duration;

/**
 * Builds a {@link Config} from {@code PODOS_*} environment variables.
 *
 * <p>Intended for Category 1 (self-registering) containers that receive
 * gateway connection details via environment rather than INI files.
 *
 * <p>Recognized variables:
 * <ul>
 *   <li>{@code PODOS_GATEWAY_HOST}, {@code PODOS_GATEWAY_PORT}, {@code PODOS_GATEWAY_FQN}</li>
 *   <li>{@code PODOS_ACTOR_NAME}, {@code PODOS_PASSCODE}</li>
 *   <li>{@code PODOS_RECONNECT_ENABLED}, {@code PODOS_RECONNECT_MAX_RETRIES},
 *       {@code PODOS_RECONNECT_INITIAL_BACKOFF}, {@code PODOS_RECONNECT_MAX_BACKOFF},
 *       {@code PODOS_RECONNECT_BACKOFF_MULTIPLIER}</li>
 *   <li>{@code PODOS_CONCURRENT_MODE}</li>
 *   <li>{@code PODOS_DIAL_TIMEOUT}, {@code PODOS_SEND_TIMEOUT}, {@code PODOS_RECEIVE_TIMEOUT}</li>
 *   <li>{@code PODOS_LOG_LEVEL}</li>
 * </ul>
 *
 * <p>Unset variables are left at their default value. Numeric durations are in seconds.
 */
public final class ConfigFromEnv {

    private ConfigFromEnv() {}

    /**
     * Reads {@code PODOS_*} environment variables and returns a populated {@link Config}.
     *
     * @return Config with values from the environment
     */
    public static Config load() {
        Config cfg = new Config();
        cfg.network = "tcp";

        String host = env("PODOS_GATEWAY_HOST");
        if (host != null) cfg.host = host;

        String port = env("PODOS_GATEWAY_PORT");
        if (port != null) cfg.port = port;

        String fqn = env("PODOS_GATEWAY_FQN");
        if (fqn != null) cfg.gatewayActorName = fqn;

        String actorName = env("PODOS_ACTOR_NAME");
        if (actorName != null) cfg.clientName = actorName;

        String passcode = env("PODOS_PASSCODE");
        if (passcode != null) cfg.passcode = passcode;

        String concurrentMode = env("PODOS_CONCURRENT_MODE");
        if (concurrentMode != null) cfg.enableConcurrentMode = parseBool(concurrentMode);

        String dialTimeout = env("PODOS_DIAL_TIMEOUT");
        if (dialTimeout != null) {
            int secs = parseInt(dialTimeout);
            if (secs > 0) cfg.dialTimeout = Duration.ofSeconds(secs);
        }

        String sendTimeout = env("PODOS_SEND_TIMEOUT");
        if (sendTimeout != null) {
            int secs = parseInt(sendTimeout);
            if (secs > 0) cfg.sendTimeout = Duration.ofSeconds(secs);
        }

        String receiveTimeout = env("PODOS_RECEIVE_TIMEOUT");
        if (receiveTimeout != null) {
            int secs = parseInt(receiveTimeout);
            if (secs > 0) cfg.receiveTimeout = Duration.ofSeconds(secs);
        }

        String logLevel = env("PODOS_LOG_LEVEL");
        if (logLevel != null) cfg.logLevel = parseInt(logLevel);

        // Reconnection settings
        String reconnectEnabled = env("PODOS_RECONNECT_ENABLED");
        if (reconnectEnabled != null) cfg.reconnectConfig.enabled = parseBool(reconnectEnabled);

        String reconnectMaxRetries = env("PODOS_RECONNECT_MAX_RETRIES");
        if (reconnectMaxRetries != null) cfg.reconnectConfig.maxRetries = parseInt(reconnectMaxRetries);

        String reconnectInitialBackoff = env("PODOS_RECONNECT_INITIAL_BACKOFF");
        if (reconnectInitialBackoff != null) {
            int secs = parseInt(reconnectInitialBackoff);
            if (secs > 0) cfg.reconnectConfig.initialBackoff = Duration.ofSeconds(secs);
        }

        String reconnectMultiplier = env("PODOS_RECONNECT_BACKOFF_MULTIPLIER");
        if (reconnectMultiplier != null) {
            double f = parseDouble(reconnectMultiplier);
            if (f > 0) cfg.reconnectConfig.backoffMultiplier = f;
        }

        String reconnectMaxBackoff = env("PODOS_RECONNECT_MAX_BACKOFF");
        if (reconnectMaxBackoff != null) {
            int secs = parseInt(reconnectMaxBackoff);
            if (secs > 0) cfg.reconnectConfig.maxBackoff = Duration.ofSeconds(secs);
        }

        return cfg;
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static boolean parseBool(String v) {
        if (v == null) return false;
        String lower = v.trim().toLowerCase();
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "y".equals(lower);
    }

    private static int parseInt(String v) {
        if (v == null) return 0;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String v) {
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
