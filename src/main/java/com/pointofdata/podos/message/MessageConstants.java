package com.pointofdata.podos.message;

/**
 * Constants for the Pod-OS message wire protocol.
 */
public final class MessageConstants {

    private MessageConstants() {}

    /**
     * Maximum allowed message size: 2 GiB.
     * Any message larger than this is rejected by both encoder and decoder.
     */
    public static final long MAX_MESSAGE_SIZE_BYTES = 2L * 1024L * 1024L * 1024L;

    /**
     * Minimum valid wire message size: 7 length fields × 9 bytes each = 63 bytes.
     */
    public static final int MIN_MESSAGE_SIZE = 63;

    /**
     * Each length field in the wire format is exactly 9 bytes.
     */
    public static final int LENGTH_FIELD_SIZE = 9;

    /**
     * Number of length fields at the start of every message.
     */
    public static final int NUM_LENGTH_FIELDS = 7;

    /**
     * Total bytes consumed by all length fields at the start of every message
     * (7 × 9 = 63).
     */
    public static final int LENGTHS_SECTION_SIZE = NUM_LENGTH_FIELDS * LENGTH_FIELD_SIZE;
}
