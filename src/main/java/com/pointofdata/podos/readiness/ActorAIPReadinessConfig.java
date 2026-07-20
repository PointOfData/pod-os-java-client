package com.pointofdata.podos.readiness;

import java.time.Duration;

/**
 * Tunes the readiness polling loop. Zero/null fields fall back to defaults tuned
 * to absorb gateway/peer-process startup latency (~60s budget).
 */
public class ActorAIPReadinessConfig {

    public Duration timeout = Duration.ZERO;
    public Duration initialBackoff = Duration.ZERO;
    public Duration maxBackoff = Duration.ZERO;
    /**
     * Number of back-to-back successful probes required before ready.
     * Values &lt;= 1 return on first success (default).
     */
    public int requiredConsecutive = 0;
    /** Pause between consecutive probes once a success streak has started. */
    public Duration successInterval = Duration.ZERO;

    ActorAIPReadinessConfig normalized() {
        ActorAIPReadinessConfig c = new ActorAIPReadinessConfig();
        c.timeout = (timeout == null || timeout.isZero() || timeout.isNegative())
                ? Duration.ofSeconds(60) : timeout;
        c.initialBackoff = (initialBackoff == null || initialBackoff.isZero() || initialBackoff.isNegative())
                ? Duration.ofSeconds(2) : initialBackoff;
        c.maxBackoff = (maxBackoff == null || maxBackoff.isZero() || maxBackoff.isNegative())
                ? Duration.ofSeconds(8) : maxBackoff;
        c.requiredConsecutive = requiredConsecutive <= 0 ? 1 : requiredConsecutive;
        c.successInterval = (successInterval == null || successInterval.isZero() || successInterval.isNegative())
                ? Duration.ofSeconds(2) : successInterval;
        return c;
    }
}
