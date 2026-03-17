package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains fields for link operations between events.
 * Used for LinkEvent, UnlinkEvent, StoreBatchLinks, and their response variants.
 */
public class LinkFields {
    /** Developer-provided unique ID for the link event. */
    public String uniqueId = "";
    /** AIP-generated unique ID; must be ASCII encoded. */
    public String id = "";
    /** Local machine ID for the event. */
    public String localId = "";
    /** Owner ID; indicates what entity created the event (default "$sys"). */
    public String owner = "";
    /** POSIX timestamp in microseconds ("+%.6f"). */
    public String timestamp = "";
    /** Parsed date/time. */
    public DateTimeObject dateTime = new DateTimeObject();
    /** Location specification (e.g., "TERRA|47.6|-122.5"). */
    public String location = "";
    /** Location segment delimiter (default "|"). */
    public String locationSeparator = "";
    /** Required if uniqueIdA is not provided. */
    public String eventA = "";
    /** Required if uniqueIdB is not provided. */
    public String eventB = "";
    /** Required if eventA is not provided. */
    public String uniqueIdA = "";
    /** Required if eventB is not provided. */
    public String uniqueIdB = "";
    /** Link strength A→B, required. */
    public double strengthA;
    /** Link strength B→A, required. */
    public double strengthB;
    /** Link category, required. */
    public String category = "";
    /** Developer-defined event type string. */
    public String type = "";
    /** Owner unique ID; required if ownerID is not provided. */
    public String ownerUniqueId = "";
    /** Owner ID; required if ownerUniqueId is not provided. */
    public String ownerId = "";
    /** Tags for this link; populated from _linktag records. */
    public List<TagOutput> tags = new ArrayList<>();
    /** Tags describing the target event; populated from _targettag records. */
    public List<TagOutput> targetTags = new ArrayList<>();
    /** Status of the link; used in StoreBatchLinks response. */
    public String status = "";
    /** Message of the link; used in StoreBatchLinks response. */
    public String message = "";

    public LinkFields() {}
}
