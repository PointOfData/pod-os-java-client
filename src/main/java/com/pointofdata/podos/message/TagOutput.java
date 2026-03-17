package com.pointofdata.podos.message;

/**
 * Represents a parsed tag from a response payload.
 * Used for tags returned in GetEvent and GetEventsForTags responses.
 */
public class TagOutput {
    public int frequency;
    public String category = "";
    public String key = "";
    public String value = "";
    public String owner = "";
    public String timestamp = "";
    /** ID of the target tag; used to identify the target tag in the response. */
    public String targetTagId = "";

    public TagOutput() {}

    public TagOutput(int frequency, String category, String key, String value) {
        this.frequency = frequency;
        this.category = category;
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "TagOutput{freq=" + frequency + ", key='" + key + "', value='" + value + "'}";
    }
}
