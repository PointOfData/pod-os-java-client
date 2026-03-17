package com.pointofdata.podos.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * SLF4J-backed {@link PodOsLogger} implementation.
 * Equivalent to Go's {@code SlogLogger}.
 *
 * <p>Key-value pairs passed to log methods are formatted as {@code key=value} pairs
 * appended to the message.
 */
public final class Slf4jLogger implements PodOsLogger {

    private final Logger delegate;
    private final Level minLevel;
    private final Object[] context; // pre-bound key-value context pairs

    public Slf4jLogger(Class<?> clazz, Level minLevel) {
        this(LoggerFactory.getLogger(clazz), minLevel, new Object[0]);
    }

    public Slf4jLogger(String name, Level minLevel) {
        this(LoggerFactory.getLogger(name), minLevel, new Object[0]);
    }

    private Slf4jLogger(Logger delegate, Level minLevel, Object[] context) {
        this.delegate = delegate;
        this.minLevel = minLevel;
        this.context  = context;
    }

    @Override
    public boolean isEnabled(Level level) {
        if (level.getValue() > minLevel.getValue()) return false;
        switch (level) {
            case DEBUG: return delegate.isDebugEnabled();
            case INFO:  return delegate.isInfoEnabled();
            case WARN:  return delegate.isWarnEnabled();
            case ERROR: return delegate.isErrorEnabled();
            default:    return false;
        }
    }

    @Override
    public void debug(String msg, Object... kv) {
        if (isEnabled(Level.DEBUG)) delegate.debug(format(msg, kv));
    }

    @Override
    public void info(String msg, Object... kv) {
        if (isEnabled(Level.INFO)) delegate.info(format(msg, kv));
    }

    @Override
    public void warn(String msg, Object... kv) {
        if (isEnabled(Level.WARN)) delegate.warn(format(msg, kv));
    }

    @Override
    public void error(String msg, Object... kv) {
        if (isEnabled(Level.ERROR)) delegate.error(format(msg, kv));
    }

    @Override
    public PodOsLogger with(Object... kv) {
        Object[] merged = Arrays.copyOf(context, context.length + kv.length);
        System.arraycopy(kv, 0, merged, context.length, kv.length);
        return new Slf4jLogger(delegate, minLevel, merged);
    }

    private String format(String msg, Object[] kv) {
        Object[] all = concat(context, kv);
        if (all.length == 0) return msg;
        StringBuilder sb = new StringBuilder(msg);
        for (int i = 0; i + 1 < all.length; i += 2) {
            sb.append(' ').append(all[i]).append('=').append(all[i + 1]);
        }
        if (all.length % 2 != 0) sb.append(' ').append(all[all.length - 1]);
        return sb.toString();
    }

    private static Object[] concat(Object[] a, Object[] b) {
        if (a.length == 0) return b;
        if (b.length == 0) return a;
        Object[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Creates a logger at INFO level or above using the given SLF4J name.
     */
    public static Slf4jLogger forName(String name) {
        return new Slf4jLogger(name, Level.INFO);
    }

    /**
     * Creates a logger at the given level using the given SLF4J name.
     */
    public static Slf4jLogger forName(String name, Level level) {
        return new Slf4jLogger(name, level);
    }
}
