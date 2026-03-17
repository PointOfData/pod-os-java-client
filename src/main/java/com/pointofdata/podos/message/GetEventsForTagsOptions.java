package com.pointofdata.podos.message;

/**
 * Options for the GetEventsForTags intent.
 * Searches for events matching tag patterns.
 */
public class GetEventsForTagsOptions {
    /** Event key filter (FASTPATTERN). */
    public String eventPattern = "";
    /** Event key filter high range. */
    public String eventPatternHigh = "";
    /** Include only event ID and unique ID. */
    public boolean includeBriefHits;
    /** Get all tag and link data for all matching events (disables include_tag_stats). */
    public boolean getAllData;
    /** First link to retrieve. */
    public int firstLink;
    /** Number of links to retrieve. */
    public int linkCount;
    /** Events per reply message. */
    public int eventsPerMessage;
    /** Paging: first result index. */
    public int startResult;
    /** Paging: last result index. */
    public int endResult;
    /** Minimum tag matches required. */
    public int minEventHits;
    /** Return only match count. */
    public boolean countOnly;
    /** Include the number of links associated with a matching event object. */
    public boolean getMatchLinks;
    /** Return total links per event. */
    public boolean countMatchLinks;
    /** Return tags for links. */
    public boolean getLinkTags;
    /** Return tags for link targets. */
    public boolean getTargetTags;
    /** Regex filter for link tags. */
    public String linkTagFilter = "";
    /** Regex filter for target tags. */
    public String linkedEventsFilter = "";
    /** Restrict link results to this category name. */
    public String linkCategory = "";
    /** Filter all links and tags owned by this event. */
    public String owner = "";
    /** Filter all links and tags owned by this unique ID. */
    public String ownerUniqueId = "";
    /** Request total number of event objects in the database. */
    public boolean getEventObjectCount;
    /** Y: send all results in a single message; N: send individual result messages. */
    public boolean bufferResults;
    /** Y: include statistics for each tag value that resulted in a match hit. */
    public boolean includeTagStats;
    /** Invert the hit tag filter. */
    public boolean invertHitTagFilter;
    /** Filter for result tags. */
    public String hitTagFilter = "";
    /** Output format: "0" = format a, "1" = format b. */
    public String bufferFormat = "";

    public GetEventsForTagsOptions() {}
}
