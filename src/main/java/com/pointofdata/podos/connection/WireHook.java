package com.pointofdata.podos.connection;

/**
 * Observes raw wire frames for every Send and Receive operation.
 * Equivalent to Go's {@code connection.WireHook} interface.
 *
 * <p>{@link #onSend} is called after data is successfully written to the wire;
 * raw contains the complete frame including the 9-byte length prefix.
 *
 * <p>{@link #onReceive} is called after a complete frame body is reassembled;
 * raw contains everything after the 9-byte length prefix.
 *
 * <p>Implementations <em>must not</em> retain the raw array after the call returns
 * and must be safe for concurrent use.
 *
 * <p>Set to {@code null} in {@link com.pointofdata.podos.config.Config} for zero overhead.
 */
public interface WireHook {
    void onSend(byte[] raw);
    void onReceive(byte[] raw);

    /** No-op implementation. */
    WireHook NO_OP = new WireHook() {
        @Override public void onSend(byte[] raw) {}
        @Override public void onReceive(byte[] raw) {}
    };
}
