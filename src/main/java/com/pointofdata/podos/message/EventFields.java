package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains fields describing an Event Object.
 * Used for StoreEvent, GetEvent, GetEventsForTags, and their response variants.
 */
public class EventFields {
    /** Developer-provided unique ID for the event. */
    public String uniqueId = "";
    /** AIP-generated unique ID with time and location; must be ASCII encoded. */
    public String id = "";
    /** Local machine ID for the event. */
    public String localId = "";
    /** Owner ID; indicates what entity created the event (default "$sys"). */
    public String owner = "";
    /**
     * Owner unique ID; required if owner is not provided.
     * Logically different from the Event owner IDs.
     */
    public String ownerUniqueId = "";
    /**
     * Event timestamp; POSIX microseconds formatted as "+%.6f"
     * (e.g. "+1709123456.789012").
     */
    public String timestamp = "";
    /** Parsed date/time components. */
    public DateTimeObject dateTime = new DateTimeObject();
    /** Location specification (e.g., "TERRA|47.6|-122.5"). */
    public String location = "";
    /** Location segment delimiter (default "|"). */
    public String locationSeparator = "";
    /** Developer-defined event type string. */
    public String type = "";
    /** Tags for the event; populated from response messages. */
    public List<TagOutput> tags = new ArrayList<>();
    /** Links for the event; populated from response messages. */
    public List<LinkFields> links = new ArrayList<>();
    /** Payload data associated with the event in a response. */
    public PayloadFields payloadData = new PayloadFields();
    /** Status of the event; used in StoreBatchEvents response. */
    public String status = "";
    /** Total search term match hits across this event (from GetEventsForTags response). */
    public int hits;

    public EventFields() {}
}
