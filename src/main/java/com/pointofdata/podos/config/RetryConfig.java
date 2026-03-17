package com.pointofdata.podos.config;

import java.time.Duration;

/**
 * Configuration for connection retry behavior.
 * Mirrors Go's {@code RetryConfig} struct.
 */
public class RetryConfig {
    /** Number of retry attempts. Default: 3. */
    public int retries = 3;
    /** Initial backoff duration. Default: 1 second. */
    public Duration backoff = Duration.ofSeconds(1);
    /** Backoff multiplier per attempt. Default: 1.5. */
    public double backoffMultiplier = 1.5;
    /** If true, disables the built-in caps on backoff duration and multiplier. */
    public boolean disableBackoffCaps = false;

    public RetryConfig() {}

    public RetryConfig(int retries, Duration backoff, double backoffMultiplier) {
        this.retries = retries;
        this.backoff = backoff;
        this.backoffMultiplier = backoffMultiplier;
    }
}
