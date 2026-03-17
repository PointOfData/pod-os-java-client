package com.pointofdata.podos.log;

/**
 * Logger interface for Pod-OS client operations.
 * Mirrors Go's {@code log.Logger} interface.
 *
 * <p>Implementations must be safe for concurrent use.
 */
public interface PodOsLogger {

    /** Returns true if the given log level is enabled. */
    boolean isEnabled(Level level);

    void debug(String msg, Object... keyvals);
    void info(String msg, Object... keyvals);
    void warn(String msg, Object... keyvals);
    void error(String msg, Object... keyvals);

    /**
     * Returns a new logger with the given key-value pairs added to every log entry.
     * Equivalent to Go's {@code With(keyvals ...any)}.
     */
    PodOsLogger with(Object... keyvals);

    /** Log level enumeration. */
    enum Level {
        DISABLED(0), ERROR(1), WARN(2), INFO(3), DEBUG(4);

        private final int value;
        Level(int value) { this.value = value; }
        public int getValue() { return value; }

        public static Level fromInt(int v) {
            for (Level l : values()) {
                if (l.value == v) return l;
            }
            return DISABLED;
        }
    }
}
