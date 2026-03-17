package com.pointofdata.podos.message;

/**
 * Represents an encoded wire message ready to be sent over a TCP socket.
 */
public class SocketMessage {
    /** Complete encoded message bytes (including the 7×9-byte length prefix). */
    public final byte[] messageBytes;
    /**
     * The tab-separated key=value wire header produced during encoding.
     * Populated by {@link MessageEncoder} for diagnostic use (debug logging, wire hooks)
     * without re-parsing {@link #messageBytes}.
     */
    public final String header;

    public SocketMessage(byte[] messageBytes, String header) {
        this.messageBytes = messageBytes;
        this.header = header;
    }
}
