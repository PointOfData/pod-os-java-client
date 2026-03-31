package com.pointofdata.podos.message;

/**
 * Represents a piece of important data for an Event Object.
 * This is a Facet construction extending tagvalue into key/value structure.
 *
 * <p>The {@link #value} field supports: String, Integer, Long, Double, Float,
 * Boolean, byte[] (base64-encoded on wire), Map, List, or any object whose
 * {@code toString()} produces the desired wire representation.
 */
public class Tag {
    /** Count of occurrences. */
    public int frequency;
    /** Tag key/category. */
    public String key = "";
    /**
     * Tag value. Supports String, Integer, Long, Double, Float, Boolean,
     * byte[] (base64-encoded on wire), or any JSON-serializable type.
     */
    public Object value;
    /**
     * Event timestamp; POSIX microseconds formatted as "+%.6f" (e.g. "+1709123456.789012").
     */
    public String timestamp = "";
    /** Tag's Event Object ID. */
    public String id = "";
    /** Tag owner Event Object ID. */
    public String owner = "";
    /** Tag owner unique identifier. */
    public String ownerUniqueId = "";

    public Tag() {}

    public Tag(int frequency, String key, Object value) {
        this.frequency = frequency;
        this.key = key;
        this.value = value;
    }

    /** Returns the value as a String, or null if value is null. */
    public String stringValue() {
        if (value instanceof String) return (String) value;
        if (value == null) return null;
        return String.valueOf(value);
    }

    /** Returns the value as an int, or 0 if not numeric. */
    public int intValue() {
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }

    /** Returns the value as a double, or 0.0 if not numeric. */
    public double doubleValue() {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }

    /** Returns the value as a boolean, or false if not a Boolean. */
    public boolean boolValue() {
        if (value instanceof Boolean) return (Boolean) value;
        return false;
    }

    @Override
    public String toString() {
        return "Tag{freq=" + frequency + ", key='" + key + "', value=" + value + "}";
    }
}
