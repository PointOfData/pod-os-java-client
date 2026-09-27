package com.pointofdata.podos.message;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents a parsed tag from a response payload.
 * Used for tags returned in GetEvent and GetEventsForTags responses.
 */
public class TagOutput {
    public int frequency;
    public String category = "";
    public String key = "";
    public String value = "";
    /** Event key of the event that created the tag ({@link TagOwnerOutput#EVENT_KEY}); empty when unowned. */
    public String owner = "";
    /**
     * Unique ID of the event that created the tag ({@link TagOwnerOutput#UNIQUE_ID}); empty when unowned
     * or the owner has no unique ID.
     */
    public String ownerUniqueId = "";
    /**
     * Tag storage time as POSIX {@code "ssssssssss.uuuuuu"} UTC (GetEvent {@code tag_format=1},
     * GetEventsForTags {@code buffer_format=1}).
     */
    public String timestamp = "";
    /** Database tag counter (GetEvent {@code event_tag:nnnnnnnnn}); 0 when not reported. */
    public int tagNumber;
    /** ID of the target tag; used to identify the target tag in the response. */
    public String targetTagId = "";

    public TagOutput() {}

    public TagOutput(int frequency, String category, String key, String value) {
        this.frequency = frequency;
        this.category = category;
        this.key = key;
        this.value = value;
    }

    /** Parses {@link #timestamp} into an {@link Instant}; empty when the timestamp is empty or malformed. */
    public Optional<Instant> time() {
        return TagFormats.parsePosixTimestamp(timestamp);
    }

    @Override
    public String toString() {
        return "TagOutput{freq=" + frequency + ", key='" + key + "', value='" + value + "'}";
    }
}
