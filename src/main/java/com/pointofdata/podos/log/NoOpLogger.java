package com.pointofdata.podos.log;

/**
 * No-op logger implementation — all log calls are discarded with zero overhead.
 * Equivalent to Go's {@code NoOpLogger}.
 */
public final class NoOpLogger implements PodOsLogger {

    /** Shared singleton instance. */
    public static final NoOpLogger INSTANCE = new NoOpLogger();

    @Override public boolean isEnabled(Level level) { return false; }
    @Override public void debug(String msg, Object... kv) {}
    @Override public void info(String msg, Object... kv) {}
    @Override public void warn(String msg, Object... kv) {}
    @Override public void error(String msg, Object... kv) {}
    @Override public PodOsLogger with(Object... kv) { return this; }
}
