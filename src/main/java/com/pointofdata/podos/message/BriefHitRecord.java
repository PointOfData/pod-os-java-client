package com.pointofdata.podos.message;

/**
 * Represents a brief hit result from GetEventsForTags with include_brief_hits=Y.
 */
public class BriefHitRecord {
    /** The event ID. */
    public String eventId = "";
    /** Total number of search term match hits. */
    public int totalHits;

    public BriefHitRecord() {}

    public BriefHitRecord(String eventId, int totalHits) {
        this.eventId = eventId;
        this.totalHits = totalHits;
    }
}
