package com.pointofdata.podos.config;

import java.time.Duration;
import java.util.Map;

/**
 * Populates a {@link Config} from INI key-value pairs as parsed by Pod-OS actor binaries
 * (flat key=value format, no section headers).
 *
 * <p>Recognized keys:
 * <ul>
 *   <li>{@code host}, {@code port}, {@code agent} (GatewayActorName), {@code client} (ClientName)</li>
 *   <li>{@code stream_messages}, {@code concurrent_mode}</li>
 *   <li>{@code reconnect_enabled}, {@code reconnect_max_retries}, {@code reconnect_initial_backoff},
 *       {@code reconnect_backoff_multiplier}, {@code reconnect_max_backoff}</li>
 *   <li>{@code dial_timeout}, {@code send_timeout}, {@code receive_timeout}</li>
 *   <li>{@code retry_count}, {@code retry_backoff}, {@code retry_backoff_multiplier}</li>
 *   <li>{@code passcode}, {@code log_level}</li>
 * </ul>
 *
 * <p>Unrecognized keys are silently ignored. Numeric durations are in seconds.
 */
public final class ConfigFromINI {

    private ConfigFromINI() {}

    /**
     * Reads key-value pairs and returns a populated {@link Config}.
     *
     * @param kvs flat map of INI key=value pairs
     * @return Config with values from the INI map
     */
    public static Config load(Map<String, String> kvs) {
        Config cfg = new Config();
        cfg.network = "tcp";

        for (Map.Entry<String, String> entry : kvs.entrySet()) {
            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue();

            switch (key) {
                case "host":
                    cfg.host = value;
                    break;
                case "port":
                    cfg.port = value;
                    break;
                case "agent":
                    cfg.gatewayActorName = value;
                    break;
                case "client":
                    cfg.clientName = value;
                    break;
                case "passcode":
                    cfg.passcode = value;
                    break;
                case "stream_messages":
                    cfg.enableStreaming = parseBool(value);
                    break;
                case "concurrent_mode":
                    cfg.enableConcurrentMode = parseBool(value);
                    break;
                case "dial_timeout": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.dialTimeout = Duration.ofSeconds(secs);
                    break;
                }
                case "send_timeout": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.sendTimeout = Duration.ofSeconds(secs);
                    break;
                }
                case "receive_timeout": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.receiveTimeout = Duration.ofSeconds(secs);
                    break;
                }
                case "log_level":
                    cfg.logLevel = parseInt(value);
                    break;

                // Reconnection settings
                case "reconnect_enabled":
                    cfg.reconnectConfig.enabled = parseBool(value);
                    break;
                case "reconnect_max_retries":
                    cfg.reconnectConfig.maxRetries = parseInt(value);
                    break;
                case "reconnect_initial_backoff": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.reconnectConfig.initialBackoff = Duration.ofSeconds(secs);
                    break;
                }
                case "reconnect_backoff_multiplier": {
                    double f = parseDouble(value);
                    if (f > 0) cfg.reconnectConfig.backoffMultiplier = f;
                    break;
                }
                case "reconnect_max_backoff": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.reconnectConfig.maxBackoff = Duration.ofSeconds(secs);
                    break;
                }

                // Retry settings (initial dial)
                case "retry_count":
                    cfg.retryConfig.retries = parseInt(value);
                    break;
                case "retry_backoff": {
                    int secs = parseInt(value);
                    if (secs > 0) cfg.retryConfig.backoff = Duration.ofSeconds(secs);
                    break;
                }
                case "retry_backoff_multiplier": {
                    double f = parseDouble(value);
                    if (f > 0) cfg.retryConfig.backoffMultiplier = f;
                    break;
                }

                default:
                    break;
            }
        }

        return cfg;
    }

    private static boolean parseBool(String v) {
        if (v == null) return false;
        String upper = v.trim().toUpperCase();
        return "Y".equals(upper) || "YES".equals(upper) || "TRUE".equals(upper) || "1".equals(upper);
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
