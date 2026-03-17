package com.pointofdata.podos.config;

import java.time.Duration;

/**
 * Configuration for automatic reconnection behavior.
 * Mirrors Go's {@code ReconnectConfig} struct.
 */
public class ReconnectConfig {
    /**
     * Whether reconnection is enabled.
     * {@code null} = enabled (same as Go's {@code *bool} nil = enabled).
     */
    public Boolean enabled = null;
    /** Maximum number of reconnection attempts. 0 = unlimited. Default: 10. */
    public int maxRetries = 10;
    /** Initial backoff between attempts. Default: 1 second. */
    public Duration initialBackoff = Duration.ofSeconds(1);
    /** Backoff multiplier per attempt. Default: 2.0. */
    public double backoffMultiplier = 2.0;
    /** Maximum backoff duration. Default: 60 seconds. */
    public Duration maxBackoff = Duration.ofSeconds(60);

    public ReconnectConfig() {}

    /** Returns true if reconnection is enabled (null → enabled). */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public Duration getInitialBackoff() {
        return initialBackoff != null ? initialBackoff : Duration.ofSeconds(1);
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier > 0 ? backoffMultiplier : 2.0;
    }

    public Duration getMaxBackoff() {
        return maxBackoff != null ? maxBackoff : Duration.ofSeconds(60);
    }
}
