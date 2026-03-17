package com.pointofdata.podos.message;

import java.util.List;

import static com.pointofdata.podos.message.MessageUtils.forceAscii;
import static com.pointofdata.podos.message.MessageUtils.serializeTagValue;

/**
 * Constructs the wire header strings for all Pod-OS message intents.
 *
 * <p>All headers are tab-separated {@code key=value} pairs.
 * Mirrors Go's {@code ConstructHeader} dispatcher and all per-intent header builder functions.
 */
public final class HeaderBuilder {

    private HeaderBuilder() {}

    /**
     * Constructs the appropriate header for the given message and intent.
     * Equivalent to Go's {@code ConstructHeader}.
     */
    public static String constructHeader(Message msg, Intent intent, String conversationUuid) {
        if (intent == null || intent.name == null) return "";
        switch (intent.name) {
            case "GatewayId":         return gatewayIdentifyConnectionHeader(msg);
            case "GatewayStreamOn":   return gatewayStreamOnHeader(msg);
            case "ActorStreamOff":    return gatewayStreamOffHeader(msg);
            case "ActorEcho":         return actorEchoHeader(msg);
            case "StoreEvent":        return storeEventMessageHeader(msg);
            case "StoreData":         return storeDataMessageHeader(msg);
            case "LinkEvent":         return linkEventsMessageHeader(msg);
            case "UnlinkEvent":       return unlinkEventsMessageHeader(msg);
            case "GetEvent":          return getEventMessageHeader(msg);
            case "GetEventsForTags":  return getEventsForTagMessageHeader(msg);
            case "StoreBatchEvents":  return storeBatchEventsMessageHeader(msg);
            case "StoreBatchTags":
            case "BatchStoreTags":    return storeBatchTagsMessageHeader(msg);
            case "StoreBatchLinks":   return batchLinkEventsMessageHeader(msg);
            case "ActorRequest":      return actorRequestHeader(msg);
            default:                  return "";
        }
    }

    // =========================================================================
    // Per-intent header builders — verified faithful to Go counterparts
    // =========================================================================

    /**
     * Constructs the GatewayId (ID) header.
     * Required fields: clientName. Optional: passcode + userName, messageId.
     */
    public static String gatewayIdentifyConnectionHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        String passcode = msg.passcode != null ? msg.passcode : "";
        String userName = msg.userName != null ? msg.userName : "";
        if (!passcode.isEmpty() && !userName.isEmpty()) {
            h.append("id:passcode=").append(passcode).append('\t');
            h.append("id:user=").append(userName).append('\t');
        }
        h.append("id:name=").append(msg.clientName);
        if (msg.messageId != null && !msg.messageId.isEmpty()) {
            h.append('\t').append("_msg_id=").append(msg.messageId);
        }
        return h.toString();
    }

    /** Constructs the GatewayStreamOn header. */
    public static String gatewayStreamOnHeader(Message msg) {
        if (msg.messageId != null && !msg.messageId.isEmpty()) {
            return "_msg_id=" + msg.messageId;
        }
        return "";
    }

    /** Constructs the GatewayStreamOff header. */
    public static String gatewayStreamOffHeader(Message msg) {
        if (msg.messageId != null && !msg.messageId.isEmpty()) {
            return "_msg_id=" + msg.messageId;
        }
        return "";
    }

    /** Constructs the ActorEcho header. */
    public static String actorEchoHeader(Message msg) {
        String id = msg.messageId != null ? msg.messageId : "";
        return "_msg_id=" + id;
    }

    /** Constructs the ActorRequest header (sends _type=status). */
    public static String actorRequestHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_type=status\t");
        if (msg.messageId != null && !msg.messageId.isEmpty()) {
            h.append("_msg_id=").append(msg.messageId);
        }
        return h.toString();
    }

    /**
     * Constructs the StoreEvent (_db_cmd=store) header.
     * Tags are written as {@code tag_NNNN=freq:key=value}.
     */
    public static String storeEventMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=store\t");
        EventFields ev = msg.event;
        if (ev != null) {
            if (notEmpty(ev.uniqueId)) h.append("unique_id=").append(ev.uniqueId).append('\t');
            if (notEmpty(ev.id))       h.append("event_id=").append(forceAscii(ev.id)).append('\t');
            if (notEmpty(ev.owner))    h.append("owner=").append(ev.owner).append('\t');
            if (notEmpty(ev.timestamp)) {
                h.append("timestamp=").append(ev.timestamp).append('\t');
            } else {
                h.append("timestamp=").append(MessageUtils.getTimestamp()).append('\t');
            }
            h.append("loc_delim=").append(ev.locationSeparator).append('\t');
            h.append("loc=").append(ev.location).append('\t');
            if (notEmpty(ev.type)) {
                h.append("type=").append(ev.type).append('\t');
            } else {
                h.append("type=store event\t");
            }
        } else {
            h.append("timestamp=").append(MessageUtils.getTimestamp()).append('\t');
            h.append("loc_delim=|\t");
            h.append("loc=\t");
            h.append("type=store event\t");
        }
        h.append("mime=").append(msg.payloadMimeType()).append('\t');

        List<Tag> tags = msg.tags();
        if (tags != null && !tags.isEmpty()) {
            for (int i = 0; i < tags.size(); i++) {
                Tag tag = tags.get(i);
                String tagName = String.format("tag_%04d", i + 1);
                String tagValue = tag.frequency + ":" + tag.key + "=" + serializeTagValue(tag.value);
                h.append(tagName).append('=').append(tagValue).append('\t');
            }
        }
        if (notEmpty(msg.messageId)) {
            h.append("_msg_id=").append(msg.messageId);
        }
        return h.toString();
    }

    /**
     * Constructs the StoreData (_db_cmd=store_data) header.
     * Stores data directly in the Evolutionary Neural Memory database.
     * Required: Event.uniqueId OR Event.id, Event.timestamp, Event.location,
     * Event.locationSeparator, Payload.data, Payload.mimeType.
     */
    public static String storeDataMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=store_data\t");
        EventFields ev = msg.event;
        if (ev != null) {
            if (notEmpty(ev.uniqueId)) {
                h.append("unique_id=").append(ev.uniqueId).append('\t');
            } else if (notEmpty(ev.id)) {
                h.append("event_id=").append(forceAscii(ev.id)).append('\t');
            }
            if (notEmpty(ev.timestamp)) {
                h.append("timestamp=").append(ev.timestamp).append('\t');
            } else {
                h.append("timestamp=").append(MessageUtils.getTimestamp()).append('\t');
            }
            h.append("loc_delim=").append(ev.locationSeparator).append('\t');
            h.append("loc=").append(ev.location).append('\t');
        } else {
            h.append("timestamp=").append(MessageUtils.getTimestamp()).append('\t');
            h.append("loc_delim=|\t");
            h.append("loc=\t");
        }
        h.append("mime=").append(msg.payloadMimeType()).append('\t');
        if (notEmpty(msg.messageId)) {
            h.append("_msg_id=").append(msg.messageId);
        }
        return h.toString();
    }

    /** Constructs the StoreBatchEvents (_db_cmd=store_batch) header. */
    public static String storeBatchEventsMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=store_batch\t");
        if (notEmpty(msg.messageId)) {
            h.append("_msg_id=").append(msg.messageId).append('\t');
        }
        return h.toString();
    }

    /**
     * Constructs the StoreBatchTags (_db_cmd=tag_store_batch) header.
     * Required: Event.id OR Event.uniqueId, Event.owner OR Event.ownerUniqueId.
     */
    public static String storeBatchTagsMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=tag_store_batch\t");
        EventFields ev = msg.event;
        if (ev != null) {
            if (notEmpty(ev.uniqueId)) {
                h.append("unique_id=").append(ev.uniqueId).append('\t');
            } else if (notEmpty(ev.id)) {
                h.append("event_id=").append(forceAscii(ev.id)).append('\t');
            }
            if (notEmpty(ev.owner)) {
                h.append("owner=").append(ev.owner).append('\t');
            } else if (notEmpty(ev.ownerUniqueId)) {
                h.append("owner_unique_id=").append(ev.ownerUniqueId).append('\t');
            }
        }
        if (notEmpty(msg.messageId)) {
            h.append("_msg_id=").append(msg.messageId);
        }
        return h.toString();
    }

    /** Constructs the GetEvent (_db_cmd=get) header. */
    public static String getEventMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=get\t");
        EventFields ev = msg.event;
        if (ev != null) {
            if (notEmpty(ev.id))       h.append("event_id=").append(forceAscii(ev.id)).append('\t');
            if (notEmpty(ev.uniqueId)) h.append("unique_id=").append(forceAscii(ev.uniqueId)).append('\t');
        }
        GetEventOptions opts = msg.getEventOpts();
        if (opts != null) {
            if (opts.sendData)      h.append("send_data=Y\t");
            if (opts.localIdOnly)   h.append("local_id_only=Y\t");
            if (opts.getTags)       h.append("get_tags=Y\t");
            if (opts.getLinks)      h.append("get_links=Y\t");
            if (opts.getLinkTags)   h.append("get_link_tags=Y\t");
            if (opts.getTargetTags) h.append("get_target_tags=Y\t");
            if (notEmpty(opts.eventFacetFilter))   h.append("event_facet_filter=").append(opts.eventFacetFilter).append('\t');
            if (notEmpty(opts.linkFacetFilter))    h.append("link_facet_filter=").append(opts.linkFacetFilter).append('\t');
            if (notEmpty(opts.targetFacetFilter))  h.append("target_facet_filter=").append(opts.targetFacetFilter).append('\t');
            if (notEmpty(opts.categoryFilter))     h.append("category_filter=").append(opts.categoryFilter).append('\t');
            if (notEmpty(opts.tagFilter))          h.append("tag_filter=").append(opts.tagFilter).append('\t');
            h.append("tag_format=0\t");
            h.append("request_format=0\t");
            if (opts.firstLink > 0) h.append("first_link=").append(opts.firstLink).append('\t');
            if (opts.linkCount > 0) h.append("link_count=").append(opts.linkCount).append('\t');
        }
        if (notEmpty(msg.messageId)) h.append("_msg_id=").append(msg.messageId);
        return h.toString();
    }

    /** Constructs the GetEventsForTags (_db_cmd=events_for_tag) header. */
    public static String getEventsForTagMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=events_for_tag\t");
        GetEventsForTagsOptions opts = msg.getEventsForTagsOpts();

        boolean bufferResults = false;
        boolean includeTagStats = false;
        boolean invertHitTagFilter = false;
        String hitTagFilter = "";
        String bufferFormat = "0";

        if (opts != null) {
            bufferResults = opts.bufferResults;
            includeTagStats = opts.includeTagStats;
            invertHitTagFilter = opts.invertHitTagFilter;
            hitTagFilter = opts.hitTagFilter;
            if (notEmpty(opts.bufferFormat)) bufferFormat = opts.bufferFormat;
        }

        h.append(bufferResults ? "buffer_results=Y\t" : "buffer_results=N\t");
        if (includeTagStats) h.append("include_tag_stats=Y\t");

        if (opts != null) {
            if (opts.includeBriefHits) h.append("include_brief_hits=Y\t");
            if (opts.getAllData)        h.append("get_all_data=Y\t");
            if (opts.countOnly)        h.append("count_only=Y\t");
            if (opts.getMatchLinks)    h.append("get_match_links=Y\t");
            if (opts.countMatchLinks)  h.append("count_match_links=Y\t");
            if (opts.getLinkTags)      h.append("get_link_tags=Y\t");
            if (opts.getTargetTags)    h.append("get_target_tags=Y\t");
            if (invertHitTagFilter)    h.append("invert_hit_tag_filter=Y\t");
            if (notEmpty(opts.eventPattern))        h.append("event=").append(forceAscii(opts.eventPattern)).append('\t');
            if (notEmpty(opts.eventPatternHigh))    h.append("event_high=").append(forceAscii(opts.eventPatternHigh)).append('\t');
            if (notEmpty(opts.linkTagFilter))       h.append("link_tag_filter=").append(forceAscii(opts.linkTagFilter)).append('\t');
            if (notEmpty(opts.linkedEventsFilter))  h.append("linked_events_tag_filter=").append(forceAscii(opts.linkedEventsFilter)).append('\t');
            if (notEmpty(opts.linkCategory))        h.append("link_category=").append(opts.linkCategory).append('\t');
            if (notEmpty(opts.owner)) {
                h.append("owner=").append(forceAscii(opts.owner)).append('\t');
            } else if (notEmpty(opts.ownerUniqueId)) {
                h.append("owner_unique_id=").append(opts.ownerUniqueId).append('\t');
            }
            if (notEmpty(hitTagFilter)) h.append("hit_tag_filter=").append(forceAscii(hitTagFilter)).append('\t');
            if (opts.firstLink > 0)        h.append("first_link=").append(opts.firstLink).append('\t');
            if (opts.linkCount > 0)        h.append("link_count=").append(opts.linkCount).append('\t');
            if (opts.eventsPerMessage != 0) h.append("events_per_message=").append(opts.eventsPerMessage).append('\t');
            if (opts.startResult > 0)      h.append("start_result=").append(opts.startResult).append('\t');
            if (opts.endResult > 0)        h.append("end_result=").append(opts.endResult).append('\t');
            if (opts.minEventHits > 0)     h.append("min_event_hits=").append(opts.minEventHits).append('\t');
        }
        h.append("buffer_format=").append(notEmpty(bufferFormat) ? bufferFormat : "1").append('\t');
        if (notEmpty(msg.messageId)) h.append("_msg_id=").append(msg.messageId);
        return h.toString();
    }

    /** Constructs the LinkEvent (_db_cmd=link) header. */
    public static String linkEventsMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=link\t");
        EventFields ev = msg.event;
        if (ev != null) {
            if (notEmpty(ev.id)) {
                h.append("event_id=").append(forceAscii(ev.id)).append('\t');
            } else if (notEmpty(ev.uniqueId)) {
                h.append("unique_id=").append(ev.uniqueId).append('\t');
            }
            if (notEmpty(ev.owner)) h.append("owner=").append(ev.owner).append('\t');
        }
        if (notEmpty(msg.messageId)) h.append("_msg_id=").append(msg.messageId).append('\t');

        LinkFields lk = msg.link();
        if (lk != null) {
            if (notEmpty(lk.uniqueIdA) && notEmpty(lk.uniqueIdB)) {
                h.append("unique_id_a=").append(lk.uniqueIdA).append('\t');
                h.append("unique_id_b=").append(lk.uniqueIdB).append('\t');
            } else if (notEmpty(lk.eventA) && notEmpty(lk.eventB)) {
                h.append("event_id_a=").append(forceAscii(lk.eventA)).append('\t');
                h.append("event_id_b=").append(forceAscii(lk.eventB)).append('\t');
            }
            h.append("strength_a=").append(lk.strengthA).append('\t');
            h.append("strength_b=").append(lk.strengthB).append('\t');
            h.append("category=").append(lk.category).append('\t');
            h.append("loc_delim=").append(lk.locationSeparator).append('\t');
            h.append("loc=").append(lk.location).append('\t');
            h.append("type=").append(lk.type).append('\t');
            h.append("mime=").append(msg.payloadMimeType()).append('\t');
            h.append("timestamp=").append(lk.timestamp).append('\t');
            if (notEmpty(lk.ownerId)) {
                h.append("owner_event_id=").append(lk.ownerId).append('\t');
            } else if (notEmpty(lk.ownerUniqueId)) {
                h.append("owner_unique_id=").append(lk.ownerUniqueId).append('\t');
            }
        }
        return h.toString();
    }

    /** Constructs the StoreBatchLinks (_db_cmd=link_batch) header. */
    public static String batchLinkEventsMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=link_batch\t");
        if (notEmpty(msg.messageId)) h.append("_msg_id=").append(msg.messageId);
        return h.toString();
    }

    /** Constructs the UnlinkEvent (_db_cmd=unlink) header. */
    public static String unlinkEventsMessageHeader(Message msg) {
        StringBuilder h = new StringBuilder();
        h.append("_db_cmd=unlink\t");
        LinkFields lk = msg.link();
        if (lk == null && msg.neuralMemory != null) lk = msg.neuralMemory.unlink;
        if (lk != null) {
            if (notEmpty(lk.owner))          h.append("owner=").append(lk.owner).append('\t');
            if (notEmpty(lk.id)) {
                h.append("event_id=").append(forceAscii(lk.id)).append('\t');
            } else if (notEmpty(lk.uniqueId)) {
                h.append("unique_id=").append(lk.uniqueId).append('\t');
            }
            if (notEmpty(lk.locationSeparator)) h.append("loc_delim=").append(lk.locationSeparator).append('\t');
            if (notEmpty(lk.location))          h.append("loc=").append(lk.location).append('\t');
            if (notEmpty(lk.timestamp))         h.append("timestamp=").append(lk.timestamp).append('\t');
        }
        if (notEmpty(msg.messageId)) h.append("_msg_id=").append(msg.messageId).append('\t');
        return h.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
