package com.pointofdata.podos.connection;

import com.pointofdata.podos.log.NoOpLogger;
import com.pointofdata.podos.log.PodOsLogger;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Implements exponential-backoff retry logic for connection attempts.
 * Mirrors Go's {@code connection.Retry} struct.
 *
 * <p>Backoff formula: {@code backoff * backoffMultiplier ^ attempt}, capped at
 * {@link #BACKOFF_DURATION_CAP} and {@link #BACKOFF_MULTIPLIER_CAP} unless
 * {@link #disableBackoffCaps} is set.
 */
public class Retry {

    /** Maximum backoff multiplier cap (applied unless disableBackoffCaps = true). */
    public static final double BACKOFF_MULTIPLIER_CAP = 10.0;
    /** Maximum backoff duration cap (applied unless disableBackoffCaps = true). */
    public static final Duration BACKOFF_DURATION_CAP = Duration.ofMinutes(1);

    public int retries                = 3;
    public Duration backoff           = Duration.ofSeconds(1);
    public double backoffMultiplier   = 1.5;
    public boolean disableBackoffCaps = false;
    public PodOsLogger logger         = NoOpLogger.INSTANCE;

    public Retry() {}

    public Retry(int retries, Duration backoff, double backoffMultiplier) {
        this.retries = retries;
        this.backoff = backoff;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * Executes the given {@link Callable}, retrying up to {@link #retries} times
     * on failure with exponential backoff.
     *
     * @param action the operation to retry
     * @param <T>    return type
     * @return result of the first successful invocation
     * @throws RetriesExhaustedException if all attempts fail
     */
    public <T> T retry(Callable<T> action) throws RetriesExhaustedException {
        int maxAttempts = retries > 0 ? retries : 1;
        Duration currentBackoff = backoff;
        Exception lastErr = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastErr = e;
                if (attempt < maxAttempts) {
                    PodOsLogger log = logger != null ? logger : NoOpLogger.INSTANCE;
                    if (log.isEnabled(PodOsLogger.Level.WARN)) {
                        log.warn("retry attempt failed",
                                "attempt", attempt, "max", maxAttempts, "error", e.getMessage());
                    }
                    try {
                        Thread.sleep(currentBackoff.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RetriesExhaustedException(attempt, lastErr);
                    }
                    // Apply multiplier with optional caps
                    double mult = backoffMultiplier;
                    if (!disableBackoffCaps && mult > BACKOFF_MULTIPLIER_CAP) mult = BACKOFF_MULTIPLIER_CAP;
                    currentBackoff = Duration.ofMillis((long) (currentBackoff.toMillis() * mult));
                    if (!disableBackoffCaps && currentBackoff.compareTo(BACKOFF_DURATION_CAP) > 0) {
                        currentBackoff = BACKOFF_DURATION_CAP;
                    }
                }
            }
        }
        throw new RetriesExhaustedException(maxAttempts, lastErr);
    }

    /** Exception thrown when all retry attempts are exhausted. */
    public static class RetriesExhaustedException extends Exception {
        private final int attempts;
        public RetriesExhaustedException(int attempts, Throwable lastErr) {
            super("retries exhausted after " + attempts + " attempts: " +
                    (lastErr != null ? lastErr.getMessage() : "unknown error"), lastErr);
            this.attempts = attempts;
        }
        public int getAttempts() { return attempts; }
    }
}
