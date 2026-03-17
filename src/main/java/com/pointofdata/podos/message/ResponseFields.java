package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains data populated when decoding response messages.
 * These fields are never set by the caller; they are filled by {@link MessageDecoder}.
 */
public class ResponseFields {
    /** Processing status: "OK" or "ERROR". */
    public String status = "";
    /** Status description or error message. */
    public String message = "";
    /** Sum of number of tags in response; used in Get and StoreEvent responses. */
    public int tagCount;
    /** Sum of total number of links found; used in Get and GetEventsForTags responses. */
    public int linkCount;
    /** Link ID returned by LinkEventResponse (link_event header field). */
    public String linkId = "";
    /** Parsed event datetime. */
    public DateTimeObject dateTime = new DateTimeObject();
    /** Total number of events found or stored. */
    public int totalEvents;
    /** Number of events returned in response. */
    public int returnedEvents;
    /** Paging: first result index. */
    public int startResult;
    /** Paging: last result index. */
    public int endResult;
    /** Number of errors encountered during storage operations. */
    public int storageErrorCount;
    /** Number of successfully stored events. */
    public int storageSuccessCount;

    /** Parsed event results; used in GetEventsForTags and GetEvent responses. */
    public List<EventFields> eventRecords = new ArrayList<>();
    /** Parsed link event results; used in StoreBatchLinks response. */
    public StoreLinkBatchEventRecord storeLinkBatchEventRecord = new StoreLinkBatchEventRecord();
    /** Parsed event results; used in StoreBatchEvents response. */
    public StoreBatchEventRecord storeBatchEventRecord = new StoreBatchEventRecord();

    /** Number of different matching tag values; used in GetEventsForTags response. */
    public int matchTermCount;
    /** Whether response is buffered; used in GetEventsForTags response. */
    public boolean isBuffered;

    /** Brief hit records when include_brief_hits=Y. */
    public List<BriefHitRecord> briefHits = new ArrayList<>();

    public ResponseFields() {}
}
