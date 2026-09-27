package com.pointofdata.podos.message;

/**
 * Options for the GetEvent intent.
 * Retrieves a single Event Object by ID or UniqueId.
 */
public class GetEventOptions {
    /** Return payload data with MIME type in the Response payload section. */
    public boolean sendData;
    /** Return only local ID. */
    public boolean localIdOnly;
    /**
     * Tag output format: 0 (default) = {@code event_tag:n:f}, 1 = {@code event_tag:n:f:ssssssssss.uuuuuu[:owner]}
     * (adds storage timestamp and owner).
     */
    public Integer tagFormat;
    /** Output format (use 0 as default). */
    public int requestFormat;
    /** First link index to retrieve. */
    public int firstLink;
    /** Number of links to return. */
    public int linkCount;
    /** Return tags for event. */
    public boolean getTags;
    /** Send link information in the payload. Takes precedence over sendData. */
    public boolean getLinks;
    /** Return tags for links. */
    public boolean getLinkTags;
    /** Return tags for link targets. */
    public boolean getTargetTags;
    /** Filter event tags by prefix. */
    public String eventFacetFilter = "";
    /** Filter link tags by prefix. */
    public String linkFacetFilter = "";
    /** Filter target tags by prefix. */
    public String targetFacetFilter = "";
    /** Filter by link category. */
    public String categoryFilter = "";
    /** Regex filter for tags. */
    public String tagFilter = "";
    /**
     * Report each tag's owner (requires {@code tagFormat = 1}): {@link TagOwnerOutput#EVENT_KEY} sends
     * {@code output_tag_owner=Y}, {@link TagOwnerOutput#UNIQUE_ID} sends {@code output_tag_owner=N}.
     */
    public TagOwnerOutput tagOwnerOutput = TagOwnerOutput.NONE;

    public GetEventOptions() {}
}
