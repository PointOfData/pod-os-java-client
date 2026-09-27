package com.pointofdata.podos.message;

/**
 * Selects whether, and how, the owner of each returned tag is reported in GetEvent
 * ({@code tag_format=1}) and GetEventsForTags ({@code buffer_format=1}) responses.
 */
public enum TagOwnerOutput {
    /** Do not report tag owners (default). */
    NONE(""),
    /** Report the full event key of the event that created each tag ({@link TagOutput#owner}). */
    EVENT_KEY("event_key"),
    /** Report the unique ID of the event that created each tag ({@link TagOutput#ownerUniqueId}). */
    UNIQUE_ID("unique_id");

    private final String value;

    TagOwnerOutput(String value) {
        this.value = value;
    }

    /** Returns the canonical string value ({@code ""}, {@code "event_key"}, or {@code "unique_id"}). */
    public String value() {
        return value;
    }
}
