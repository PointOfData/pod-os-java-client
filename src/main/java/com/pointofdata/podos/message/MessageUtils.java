package com.pointofdata.podos.message;

import java.time.Instant;

/**
 * Utility methods for Pod-OS message handling.
 */
public final class MessageUtils {

    private MessageUtils() {}

    /**
     * Returns the current POSIX timestamp in microseconds as a string.
     * Format: {@code +%.6f} (e.g. {@code +1709123456.789012}).
     * Negative timestamps (before epoch) omit the {@code +} sign.
     */
    public static String getTimestamp() {
        return getTimestampFromInstant(Instant.now());
    }

    /**
     * Returns a POSIX timestamp for the given {@link Instant}.
     * Format: {@code +%.6f} (e.g. {@code +1709123456.789012}).
     */
    public static String getTimestampFromInstant(Instant instant) {
        long epochSecond = instant.getEpochSecond();
        int nanoAdjustment = instant.getNano();
        long microsecond = nanoAdjustment / 1000;

        // Format as +seconds.microseconds
        String sign = epochSecond >= 0 ? "+" : "";
        return String.format("%s%d.%06d", sign, epochSecond, microsecond);
    }

    /**
     * Forces the given string to be ASCII-only, dropping any non-ASCII characters.
     * Equivalent to Go's {@code forceASCII(s string)} function.
     */
    public static String forceAscii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 127) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Serializes a tag value of any type to its wire string representation.
     * Supports: String, Integer, Long, Short, Byte, Double, Float, Boolean,
     * byte[] (base64), and complex types (via toString or JSON).
     */
    public static String serializeTagValue(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return String.valueOf(value);
        }
        if (value instanceof Float) {
            return String.valueOf((float) (Float) value);
        }
        if (value instanceof Double) {
            return String.valueOf((double) (Double) value);
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        }
        if (value instanceof byte[]) {
            return java.util.Base64.getEncoder().encodeToString((byte[]) value);
        }
        // Complex types: attempt JSON serialization via toString
        // For full JSON support, callers can pre-serialize complex values to String
        try {
            // Try simple Java serialization as fallback
            return value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Encodes a length value as a 9-byte hex-prefixed string: {@code x} + 8 hex digits.
     * Example: 123 → {@code x0000007b}
     */
    public static String encodeLengthHex(int length) {
        return String.format("x%08x", length);
    }

    /**
     * Encodes an integer as a zero-padded 9-digit decimal string.
     * Used for messageType and dataType fields.
     */
    public static String encodeLengthDecimal(int value) {
        return String.format("%09d", value);
    }

    /**
     * Decodes a 9-byte length prefix field from the wire.
     * Accepts either {@code x} + 8 hex digits (hex format) or 9 decimal digits.
     *
     * @param field exactly 9 bytes from the wire
     * @return decoded length value
     * @throws NumberFormatException if the format is invalid
     */
    public static long decodeLengthField(byte[] field, int offset) {
        // Trim null bytes and whitespace
        int end = offset + 9;
        while (end > offset && (field[end - 1] == 0 || field[end - 1] == ' ')) {
            end--;
        }
        if (end <= offset) return 0;

        String s = new String(field, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) return 0;

        if (s.charAt(0) == 'x') {
            return Long.parseLong(s.substring(1), 16);
        }
        return Long.parseLong(s, 10);
    }

    /**
     * Validates that a 9-byte length prefix is in a valid format.
     * Valid: {@code x} + 8 hex digits, OR 9 decimal digits.
     */
    public static boolean isValidLengthPrefix(byte[] data, int offset) {
        if (data.length < offset + 9) return false;
        byte first = data[offset];
        if (first == 'x') {
            for (int i = offset + 1; i < offset + 9; i++) {
                byte b = data[i];
                if (!((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F'))) {
                    return false;
                }
            }
            return true;
        }
        for (int i = offset; i < offset + 9; i++) {
            byte b = data[i];
            if (b < '0' || b > '9') return false;
        }
        return true;
    }
}
