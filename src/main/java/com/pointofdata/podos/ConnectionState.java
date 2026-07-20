package com.pointofdata.podos;

/**
 * Represents the current state of a {@link PodOsClient}'s connection.
 * Mirrors Go's {@code ConnectionState} type.
 */
public enum ConnectionState {
    /** Connection is active (emitted after successful reconnect). */
    CONNECTED("connected"),
    /** Connection was lost (error is the cause). */
    DISCONNECTED("disconnected"),
    /** Reconnect attempt starting (error is the trigger that caused disconnect). */
    RECONNECTING("reconnecting"),
    /** All reconnect attempts exhausted (error is the last failure). */
    RECONNECT_FAILED("reconnect_failed");

    private final String label;

    ConnectionState(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
