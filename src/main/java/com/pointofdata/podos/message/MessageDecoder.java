package com.pointofdata.podos.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.pointofdata.podos.message.MessageConstants.*;

/**
 * Decodes raw wire-format byte arrays into {@link Message} objects.
 *
 * <p>Wire format:
 * <pre>
 * [totalLength:9][toLength:9][fromLength:9][headerLength:9]
 * [messageType:9][dataType:9][payloadLength:9]
 * [TO bytes][FROM bytes][HEADER bytes][PAYLOAD bytes]
 * </pre>
 */
public final class MessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(MessageDecoder.class);

    private MessageDecoder() {}

    /**
     * Decodes a raw wire message byte array into a {@link Message}.
     *
     * <p>On decode failure the returned message may be partially populated
     * and {@link Message#processingStatus()} will return {@code "ERROR"}.
     * An {@link IllegalArgumentException} is thrown for unrecoverable parse errors.
     *
     * @param raw the complete wire message bytes (including the 9-byte length prefix)
     * @return decoded {@link Message}
     * @throws IllegalArgumentException if the raw message is too short, too large,
     *                                  or structurally invalid
     */
    public static Message decodeMessage(byte[] raw) {
        Message msg = new Message();

        if (raw == null || raw.length == 0) {
            setResponseError(msg, "empty message");
            throw new IllegalArgumentException("empty message");
        }
        if (raw.length > MAX_MESSAGE_SIZE_BYTES) {
            String err = "message size " + raw.length + " exceeds maximum " + MAX_MESSAGE_SIZE_BYTES;
            setResponseError(msg, err);
            throw new IllegalArgumentException(err);
        }
        if (raw.length < MIN_MESSAGE_SIZE) {
            String err = "message too short: expected at least " + MIN_MESSAGE_SIZE + " bytes, got " + raw.length;
            setResponseError(msg, err);
            throw new IllegalArgumentException(err);
        }

        // -----------------------------------------------------------------------
        // Parse 7 × 9-byte length fields
        // -----------------------------------------------------------------------
        long totalLength   = decodeLengthField(raw, 0);
        long toLength      = decodeLengthField(raw, 9);
        long fromLength    = decodeLengthField(raw, 18);
        long headerLength  = decodeLengthField(raw, 27);
        long messageType   = decodeLengthField(raw, 36);  // decimal
        long dataType      = decodeLengthField(raw, 45);  // decimal
        long payloadLength = decodeLengthField(raw, 54);

        if (totalLength <= 0 || totalLength > MAX_MESSAGE_SIZE_BYTES) {
            String err = "invalid totalLength: " + totalLength;
            setResponseError(msg, err);
            throw new IllegalArgumentException(err);
        }

        // Validate field boundaries
        long toStart     = LENGTHS_SECTION_SIZE;
        long toEnd       = toStart + toLength;
        long fromStart   = toEnd;
        long fromEnd     = fromStart + fromLength;
        long headerStart = fromEnd;
        long headerEnd   = headerStart + headerLength;

        requireBytes(raw, toEnd,     "to",     msg);
        requireBytes(raw, fromEnd,   "from",   msg);
        requireBytes(raw, headerEnd, "header", msg);

        // -----------------------------------------------------------------------
        // Extract To and From
        // -----------------------------------------------------------------------
        msg.to = new String(raw, (int) toStart, (int) toLength, StandardCharsets.UTF_8);

        // Strip routing suffix from From: "client@gw|gw,client,timestamp" → "client@gw"
        String fromRaw = new String(raw, (int) fromStart, (int) fromLength, StandardCharsets.UTF_8);
        int pipeIdx = fromRaw.indexOf('|');
        msg.from = (pipeIdx >= 0) ? fromRaw.substring(0, pipeIdx) : fromRaw;

        // -----------------------------------------------------------------------
        // Parse header map
        // -----------------------------------------------------------------------
        String headerStr = new String(raw, (int) headerStart, (int) headerLength, StandardCharsets.UTF_8);
        Map<String, String> headerMap = decodeHeader(headerStr);

        // -----------------------------------------------------------------------
        // Parse messageType field (positions 36–44, decimal format)
        // -----------------------------------------------------------------------
        String messageTypeStr = new String(raw, 36, 9, StandardCharsets.US_ASCII).trim();
        int messageTypeInt;
        try {
            messageTypeInt = Integer.parseInt(messageTypeStr);
        } catch (NumberFormatException e) {
            String err = "failed to parse messageType: " + messageTypeStr;
            setResponseError(msg, err);
            throw new IllegalArgumentException(err, e);
        }

        // -----------------------------------------------------------------------
        // Transform header map into Message struct fields
        // -----------------------------------------------------------------------
        transformHeaderMapToMessageStruct(headerMap, msg);

        // -----------------------------------------------------------------------
        // Determine Intent
        // -----------------------------------------------------------------------
        String command = headerMap.getOrDefault("_type",
                         headerMap.getOrDefault("_command",
                         headerMap.getOrDefault("_db_cmd", "")));

        Optional<Intent> intentOpt = IntentTypes.INSTANCE.intentFromMessageTypeAndCommand(messageTypeInt, command);
        if (!intentOpt.isPresent()) {
            intentOpt = IntentTypes.INSTANCE.intentFromMessageTypeInt(messageTypeInt);
        }
        if (intentOpt.isPresent()) {
            msg.intent = intentOpt.get();
        } else {
            log.warn("unknown messageType={} command={}", messageTypeInt, command);
            msg.intent = new Intent("Unknown", "", messageTypeInt);
        }

        // -----------------------------------------------------------------------
        // Parse dataType field (positions 45–53, decimal format)
        // -----------------------------------------------------------------------
        String dataTypeStr = new String(raw, 45, 9, StandardCharsets.US_ASCII).trim();
        try {
            int dataTypeInt = Integer.parseInt(dataTypeStr);
            if (msg.payload == null) msg.payload = new PayloadFields();
            msg.payload.dataType = DataType.fromInt(dataTypeInt);
        } catch (NumberFormatException e) {
            log.warn("failed to parse dataType: {}", dataTypeStr);
        }

        // -----------------------------------------------------------------------
        // Parse payload
        // -----------------------------------------------------------------------
        if (payloadLength > 0) {
            long payloadStart = headerEnd;
            long payloadEnd   = payloadStart + payloadLength;
            requireBytes(raw, payloadEnd, "payload", msg);
            byte[] payloadBytes = Arrays.copyOfRange(raw, (int) payloadStart, (int) payloadEnd);

            if (msg.payload == null) msg.payload = new PayloadFields();
            String mime = msg.payload.mimeType;
            if ("application/octet-stream".equals(mime)) {
                msg.payload.data = payloadBytes;
            } else {
                msg.payload.data = new String(payloadBytes, StandardCharsets.UTF_8);
            }

            // Dispatch per-intent payload parsing
            parsePayloadForIntent(msg, headerMap);
        }

        // For GetEvent responses: tags may be in the header even with no payload
        if ((payloadLength == 0) &&
                ("GetEvent".equals(msg.intent.name) || "GetEventResponse".equals(msg.intent.name))) {
            String mime = msg.payload != null ? msg.payload.mimeType : "";
            if (!"application/octet-stream".equals(mime)) {
                parseGetEventResponse(msg, headerMap);
            }
        }

        return msg;
    }

    // =========================================================================
    // Header transformation
    // =========================================================================

    private static void transformHeaderMapToMessageStruct(Map<String, String> hm, Message msg) {
        ResponseFields resp  = ensureResponse(msg);
        EventFields    event = ensureEvent(msg);
        PayloadFields  pay   = ensurePayload(msg);

        resp.status  = hm.getOrDefault("_status", "");
        resp.message = hm.getOrDefault("_msg", "");

        parseLong(hm, "_total_event_hits",      v -> resp.totalEvents = (int) v);
        parseLong(hm, "_count",                 v -> { if (resp.totalEvents == 0) resp.totalEvents = (int) v; });
        parseLong(hm, "_total_link_requests_found", v -> { if (resp.totalEvents == 0) resp.totalEvents = (int) v; });
        parseLong(hm, "_links_ok",              v -> resp.storageSuccessCount = (int) v);
        parseLong(hm, "_links_with_errors",     v -> resp.storageErrorCount = (int) v);
        parseLong(hm, "_start_result",          v -> resp.startResult = (int) v);
        parseLong(hm, "_end_result",            v -> resp.endResult = (int) v);
        parseLong(hm, "_returned_event_hits",   v -> resp.returnedEvents = (int) v);
        parseLong(hm, "_set_link_count",        v -> resp.linkCount = (int) v);
        parseLong(hm, "_link_count",            v -> { if (resp.linkCount == 0) resp.linkCount = (int) v; });
        parseLong(hm, "_tag_count",             v -> resp.tagCount = (int) v);

        if (hm.containsKey("link_event")) resp.linkId = hm.get("link_event");
        if (hm.containsKey("_msg_id"))    msg.messageId = hm.get("_msg_id");

        decodeEventFields(hm, event);

        // Payload MIME and type
        if (hm.containsKey("mime"))       pay.mimeType = hm.get("mime");
        else if (hm.containsKey("_mimetype")) pay.mimeType = hm.get("_mimetype");
        if (hm.containsKey("data_type")) {
            try { pay.dataType = DataType.fromInt(Integer.parseInt(hm.get("data_type"))); }
            catch (NumberFormatException ignored) {}
        }
        if (hm.containsKey("_datasize")) {
            try { pay.dataSize = Integer.parseInt(hm.get("_datasize")); }
            catch (NumberFormatException ignored) {}
        }

        msg.payload  = pay;
        msg.event    = event;
        msg.response = resp;
    }

    private static void decodeEventFields(Map<String, String> em, EventFields ev) {
        ev.id = forceAsciiStr(em.getOrDefault("_event_id", em.getOrDefault("event_id", "")));
        ev.localId = em.getOrDefault("local_id", em.getOrDefault("_event_local_id", ""));
        ev.uniqueId = em.getOrDefault("unique_id",
                      em.getOrDefault("_unique_id",
                      em.getOrDefault("tag:1:_unique_id", "")));

        // Type
        ev.type = em.getOrDefault("event_type",
                  em.getOrDefault("_type",
                  em.getOrDefault("type", "")));

        // Owner
        ev.owner = em.getOrDefault("_owner_id",
                   em.getOrDefault("owner",
                   em.getOrDefault("_event_owner", "")));

        // DateTime
        DateTimeObject dt = new DateTimeObject();
        dt.year        = parseIntField(em, "event_year",  "_event_year");
        dt.month       = parseIntField(em, "event_mon",   "_event_month");
        dt.day         = parseIntField(em, "event_day",   "_event_day");
        dt.hour        = parseIntField(em, "event_hour",  "_event_hour");
        dt.minute      = parseIntField(em, "event_min",   "_event_min");
        dt.second      = parseIntField(em, "event_sec",   "_event_sec");
        dt.microsecond = parseIntField(em, "event_usec",  "_event_usec");
        ev.dateTime = dt;

        // Timestamp
        ev.timestamp = em.getOrDefault("timestamp", em.getOrDefault("_timestamp", ""));

        // Hits
        if (em.containsKey("_hits")) {
            try { ev.hits = Integer.parseInt(em.get("_hits")); }
            catch (NumberFormatException ignored) {}
        }

        // Location (concatenate _coordinate_01 through _coordinate_09)
        StringBuilder loc = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            String key = String.format("_coordinate_0%d", i);
            String alt = String.format("coordinate_0%d", i);
            String coord = em.getOrDefault(key, em.getOrDefault(alt, null));
            if (coord != null) loc.append(coord).append('|');
        }
        if (loc.length() > 0) {
            ev.location = loc.substring(0, loc.length() - 1); // trim trailing |
        }
        ev.locationSeparator = "|";
    }

    // =========================================================================
    // Header parsing
    // =========================================================================

    private static Map<String, String> decodeHeader(String headerStr) {
        Map<String, String> map = new HashMap<>();
        String[] parts = headerStr.split("\t", -1);
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key   = part.substring(0, eq).trim();
                String value = part.substring(eq + 1).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    // =========================================================================
    // Payload parsers
    // =========================================================================

    private static void parsePayloadForIntent(Message msg, Map<String, String> headerMap) {
        if (msg.intent == null) return;
        String intentName = msg.intent.name;
        switch (intentName) {
            case "GetEvent":
            case "GetEventResponse": {
                String mime = msg.payload != null ? msg.payload.mimeType : "";
                if (!"application/octet-stream".equals(mime) || payloadContainsLinkRecords(msg.payload)) {
                    parseGetEventResponse(msg, headerMap);
                }
                break;
            }
            case "GetEventsForTags":
            case "GetEventsForTagsResponse":
                msg.response.eventRecords = parseGetEventsForTagsPayload(msg);
                break;
            case "StoreBatchEvents":
            case "StoreBatchEventsResponse": {
                StoreBatchEventRecord record = parseStoreBatchEventsPayload(msg);
                if (record != null) msg.response.storeBatchEventRecord = record;
                break;
            }
            case "StoreBatchLinks":
            case "StoreBatchLinksResponse": {
                StoreLinkBatchEventRecord record = parseLinkEventBatchPayload(msg);
                if (record != null) msg.response.storeLinkBatchEventRecord = record;
                break;
            }
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // GetEvent response parser
    // -------------------------------------------------------------------------

    private static void parseGetEventResponse(Message msg, Map<String, String> headerMap) {
        List<TagOutput> tags  = new ArrayList<>();
        List<LinkFields> links = new ArrayList<>();

        if (msg.response == null) return;

        // Tags come from response header as event_tag:N:freq=key=value fields
        tags = parseEventTagHeaders(headerMap);

        // Links come from payload
        String payloadStr = getPayloadString(msg.payload);

        if (!payloadStr.isEmpty()) {
            Map<String, LinkFields> linkMap         = new LinkedHashMap<>();
            Map<String, List<TagOutput>> linkTagsMap   = new HashMap<>();
            Map<String, List<TagOutput>> targetTagsMap = new HashMap<>();

            for (String line : payloadStr.split("\n", -1)) {
                line = trimNulls(line).trim();
                if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;

                if (line.startsWith("_link=")) {
                    LinkFields lk = parseGetEventLinkLine(line, headerMap);
                    if (lk != null) linkMap.put(lk.id, lk);
                } else if (line.startsWith("_linktag\t") || line.equals("_linktag")) {
                    Map.Entry<String, TagOutput> e = parseGetEventLinkTagLine(line);
                    if (e != null) linkTagsMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                } else if (line.startsWith("_target_event_tag\t") || line.equals("_target_event_tag")) {
                    Map.Entry<String, TagOutput> e = parseGetEventTargetTagLine(line);
                    if (e != null) targetTagsMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                } else {
                    Map<String, String> rec = parseTabDelimited(line);
                    if (rec.containsKey("_link")) {
                        LinkFields lk = parseGetEventLinkLine(line, headerMap);
                        if (lk != null) linkMap.put(lk.id, lk);
                    }
                }
            }
            for (LinkFields lk : linkMap.values()) {
                lk.tags = linkTagsMap.getOrDefault(lk.id, new ArrayList<>());
                if (!lk.eventB.isEmpty()) {
                    lk.targetTags = targetTagsMap.getOrDefault(lk.eventB, new ArrayList<>());
                }
                // Populate uniqueIdA from parent event's uniqueId (links are stored on the source event)
                if (lk.uniqueIdA.isEmpty() && msg.event != null && !msg.event.uniqueId.isEmpty()) {
                    lk.uniqueIdA = msg.event.uniqueId;
                }
                links.add(lk);
            }
        }

        if (msg.event != null) {
            msg.event.tags = tags;
            msg.event.links = links;
            ensureResponse(msg).eventRecords.clear();
            ensureResponse(msg).eventRecords.add(copyEvent(msg.event));
        }
    }

    private static List<TagOutput> parseEventTagHeaders(Map<String, String> hm) {
        List<TagOutput> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : hm.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("event_tag:")) continue;
            String[] parts = key.split(":");
            if (parts.length < 3) continue;
            TagOutput tag = new TagOutput();
            try { tag.frequency = Integer.parseInt(parts[2]); }
            catch (NumberFormatException e) { tag.frequency = 1; }
            String val = entry.getValue();
            int eq = val.indexOf('=');
            if (eq > 0) {
                tag.key   = val.substring(0, eq);
                tag.value = val.substring(eq + 1);
            } else {
                tag.value = val;
            }
            results.add(tag);
        }
        return results;
    }

    private static LinkFields parseGetEventLinkLine(String line, Map<String, String> headerMap) {
        Map<String, String> rec = parseTabDelimited(line);
        String linkId = rec.get("_link");
        if (linkId == null || linkId.isEmpty()) return null;
        LinkFields lk = new LinkFields();
        lk.id = linkId;
        lk.eventB      = rec.getOrDefault("target_event", "");
        lk.uniqueIdB   = rec.getOrDefault("target_unique_id", "");
        lk.uniqueId    = rec.getOrDefault("unique_id", "");
        lk.category    = rec.getOrDefault("category", "");
        if (rec.containsKey("strength")) {
            try { lk.strengthB = Double.parseDouble(rec.get("strength")); }
            catch (NumberFormatException ignored) {}
        }
        if (headerMap.containsKey("event_id"))  lk.eventA = headerMap.get("event_id");
        else if (headerMap.containsKey("_event_id")) lk.eventA = headerMap.get("_event_id");
        return lk;
    }

    private static Map.Entry<String, TagOutput> parseGetEventLinkTagLine(String line) {
        Map<String, String> rec = parseTabDelimited(line);
        String linkId = rec.get("event_id");
        if (linkId == null || linkId.isEmpty()) return null;
        TagOutput tag = parseTagFromRecord(rec);
        return tag == null ? null : new AbstractMap.SimpleImmutableEntry<>(linkId, tag);
    }

    private static Map.Entry<String, TagOutput> parseGetEventTargetTagLine(String line) {
        Map<String, String> rec = parseTabDelimited(line);
        String targetId = rec.get("event_id");
        if (targetId == null || targetId.isEmpty()) return null;
        TagOutput tag = parseTagFromRecord(rec);
        return tag == null ? null : new AbstractMap.SimpleImmutableEntry<>(targetId, tag);
    }

    private static TagOutput parseTagFromRecord(Map<String, String> rec) {
        TagOutput tag = new TagOutput();
        if (rec.containsKey("freq")) {
            try { tag.frequency = Integer.parseInt(rec.get("freq")); }
            catch (NumberFormatException ignored) {}
        }
        String val = rec.get("value");
        if (val != null) {
            int eq = val.indexOf('=');
            if (eq > 0) {
                tag.key   = val.substring(0, eq);
                tag.value = val.substring(eq + 1);
            } else {
                tag.value = val;
            }
        }
        return tag;
    }

    // -------------------------------------------------------------------------
    // GetEventsForTags response parser (single-pass O(N))
    // -------------------------------------------------------------------------

    private static List<EventFields> parseGetEventsForTagsPayload(Message msg) {
        String payloadStr = getPayloadString(msg.payload);
        if (payloadStr.isEmpty()) return new ArrayList<>();

        String[] lines = payloadStr.split("\n", -1);

        // Check for brief hits response
        for (String line : lines) {
            line = trimNulls(line);
            if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;
            if (line.startsWith("_brief_hit=")) {
                parseBriefHitLines(lines, msg);
                return new ArrayList<>();
            }
            break;
        }

        int estimated = Math.max(16, lines.length / 10);
        Map<String, EventFields>        eventsMap    = new LinkedHashMap<>(estimated);
        Map<String, LinkFields>         linksMap     = new HashMap<>(estimated * 2);
        Map<String, List<String>>       linksBySource= new HashMap<>(estimated);
        Map<String, List<TagOutput>>    linkTagsMap  = new HashMap<>(estimated * 2);
        Map<String, List<TagOutput>>    targetTagsMap= new HashMap<>(estimated * 2);

        for (String line : lines) {
            line = trimNulls(line);
            if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;

            if (line.startsWith("_event_id=")) {
                parseEventIdLine(line, msg, eventsMap);
            } else if (line.startsWith("_link=")) {
                parseLinkLine(line, linksMap, linksBySource);
            } else if (line.startsWith("_linktag=")) {
                parseLinkTagLine(line, linkTagsMap);
            } else if (line.startsWith("_targettag=")) {
                parseTargetTagLine(line, targetTagsMap);
            }
        }

        // Assembly
        List<EventFields> results = new ArrayList<>(eventsMap.size());
        for (Map.Entry<String, EventFields> entry : eventsMap.entrySet()) {
            String eventId = entry.getKey();
            EventFields event = entry.getValue();
            List<String> linkIds = linksBySource.getOrDefault(eventId, new ArrayList<>());
            List<LinkFields> links = new ArrayList<>(linkIds.size());
            for (String linkId : linkIds) {
                LinkFields lk = linksMap.get(linkId);
                if (lk == null) continue;
                lk.tags = linkTagsMap.getOrDefault(linkId, new ArrayList<>());
                if (!lk.eventB.isEmpty()) {
                    lk.targetTags = targetTagsMap.getOrDefault(lk.eventB, new ArrayList<>());
                }
                // Populate uniqueIdA from parent event's uniqueId (links are stored on the source event)
                if (lk.uniqueIdA.isEmpty() && !event.uniqueId.isEmpty()) {
                    lk.uniqueIdA = event.uniqueId;
                }
                links.add(lk);
            }
            event.links = links;
            results.add(event);
        }
        return results;
    }

    private static void parseBriefHitLines(String[] lines, Message msg) {
        for (String line : lines) {
            line = trimNulls(line);
            if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;
            if (!line.startsWith("_brief_hit=")) continue;
            Map<String, String> rec = parseTabDelimited(line);
            String hitId  = rec.get("_brief_hit");
            int    hits   = 0;
            if (rec.containsKey("_hits")) {
                try { hits = Integer.parseInt(rec.get("_hits")); }
                catch (NumberFormatException ignored) {}
            }
            if (hitId != null) {
                ensureResponse(msg).briefHits.add(new BriefHitRecord(hitId, hits));
            }
        }
    }

    private static void parseEventIdLine(String line, Message msg,
                                          Map<String, EventFields> eventsMap) {
        Map<String, String> rec = parseTabDelimited(line);
        String eventId = rec.get("_event_id");
        if (eventId == null || eventId.isEmpty()) return;
        EventFields ev = new EventFields();
        ev.id = eventId;
        decodeEventFields(rec, ev);
        if (rec.containsKey("_datasize")) {
            try { ev.payloadData.dataSize = Integer.parseInt(rec.get("_datasize")); }
            catch (NumberFormatException ignored) {}
        }
        if (rec.containsKey("_mimetype")) ev.payloadData.mimeType = rec.get("_mimetype");
        // Inline tags: tag:freq:key=value
        for (Map.Entry<String, String> e : rec.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("tag:")) {
                String[] parts = k.split(":", 3);
                if (parts.length == 3) {
                    int freq = 0;
                    try { freq = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                    TagOutput tag = new TagOutput();
                    tag.frequency = freq;
                    tag.key   = parts[2];
                    tag.value = e.getValue();
                    ev.tags.add(tag);
                }
            }
        }
        // Extract unique_id from tags
        for (TagOutput tag : ev.tags) {
            if ("_unique_id".equals(tag.key) || "unique_id".equals(tag.key)) {
                ev.uniqueId = tag.value;
                break;
            }
        }
        eventsMap.put(eventId, ev);
    }

    private static void parseLinkLine(String line,
                                       Map<String, LinkFields> linksMap,
                                       Map<String, List<String>> linksBySource) {
        Map<String, String> rec = parseTabDelimited(line);
        String linkId = rec.get("_link");
        if (linkId == null || linkId.isEmpty()) return;
        LinkFields lk = new LinkFields();
        lk.id        = linkId;
        lk.eventA    = rec.getOrDefault("source", "");
        lk.eventB    = rec.getOrDefault("target", "");
        lk.uniqueId  = rec.getOrDefault("unique_id", "");
        lk.uniqueIdA = rec.getOrDefault("source_unique_id", "");
        lk.uniqueIdB = rec.getOrDefault("target_unique_id", "");
        lk.category  = rec.getOrDefault("category", "");
        if (rec.containsKey("strength")) {
            try { lk.strengthB = Double.parseDouble(rec.get("strength")); }
            catch (NumberFormatException ignored) {}
        }
        linksMap.put(linkId, lk);
        if (!lk.eventA.isEmpty()) {
            linksBySource.computeIfAbsent(lk.eventA, k -> new ArrayList<>()).add(linkId);
        }
    }

    private static void parseLinkTagLine(String line, Map<String, List<TagOutput>> linkTagsMap) {
        Map<String, String> rec = parseTabDelimited(line);
        String linkId = rec.get("_linktag");
        if (linkId == null || linkId.isEmpty()) return;
        TagOutput tag = new TagOutput();
        if (rec.containsKey("freq")) {
            try { tag.frequency = Integer.parseInt(rec.get("freq")); }
            catch (NumberFormatException ignored) {}
        }
        String val = rec.get("value");
        if (val != null) {
            int eq = val.indexOf('=');
            if (eq > 0) { tag.key = val.substring(0, eq); tag.value = val.substring(eq + 1); }
            else tag.value = val;
        }
        linkTagsMap.computeIfAbsent(linkId, k -> new ArrayList<>()).add(tag);
    }

    private static void parseTargetTagLine(String line, Map<String, List<TagOutput>> targetTagsMap) {
        Map<String, String> rec = parseTabDelimited(line);
        String targetId = rec.get("_targettag");
        if (targetId == null || targetId.isEmpty()) return;
        TagOutput tag = new TagOutput();
        if (rec.containsKey("freq")) {
            try { tag.frequency = Integer.parseInt(rec.get("freq")); }
            catch (NumberFormatException ignored) {}
        }
        String val = rec.get("value");
        if (val != null) {
            int eq = val.indexOf('=');
            if (eq > 0) { tag.key = val.substring(0, eq); tag.value = val.substring(eq + 1); }
            else tag.value = val;
        }
        targetTagsMap.computeIfAbsent(targetId, k -> new ArrayList<>()).add(tag);
    }

    // -------------------------------------------------------------------------
    // StoreBatchEvents payload parser
    // -------------------------------------------------------------------------

    private static StoreBatchEventRecord parseStoreBatchEventsPayload(Message msg) {
        String payloadStr = getPayloadString(msg.payload);
        if (payloadStr.isEmpty()) return null;
        StoreBatchEventRecord result = new StoreBatchEventRecord();
        for (String line : payloadStr.split("\n", -1)) {
            line = trimNulls(line);
            if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;
            Map<String, String> rec = parseTabDelimitedSplit(line);
            if (result.status.isEmpty() && rec.containsKey("_status"))
                result.status = rec.get("_status");
            if (result.message.isEmpty() && rec.containsKey("_msg"))
                result.message = rec.get("_msg");
            if (result.eventCount == 0 && rec.containsKey("_count")) {
                try { result.eventCount = Integer.parseInt(rec.get("_count")); }
                catch (NumberFormatException ignored) {}
            }
            EventFields ev = new EventFields();
            decodeEventFields(rec, ev);
            result.eventResults.add(ev);
        }
        if (result.eventCount == 0) result.eventCount = result.eventResults.size();
        return result;
    }

    // -------------------------------------------------------------------------
    // StoreBatchLinks payload parser
    // -------------------------------------------------------------------------

    private static StoreLinkBatchEventRecord parseLinkEventBatchPayload(Message msg) {
        String payloadStr = getPayloadString(msg.payload);
        if (payloadStr.isEmpty()) return null;
        StoreLinkBatchEventRecord result = new StoreLinkBatchEventRecord();
        for (String line : payloadStr.split("\n", -1)) {
            line = trimNulls(line);
            if (line.isEmpty() || line.equals("\u000F") || line.equals("\u0000")) continue;
            Map<String, String> rec = new HashMap<>();
            for (String field : line.split("\t", -1)) {
                if (field.isEmpty()) continue;
                int eq = field.indexOf('=');
                if (eq > 0) rec.put(field.substring(0, eq), field.substring(eq + 1));
            }
            if (result.status.isEmpty()) result.status  = rec.getOrDefault("_status", "");
            if (result.message.isEmpty()) result.message = rec.getOrDefault("_status_info", rec.getOrDefault("_msg", ""));
            if (result.totalLinkRequestsFound == 0 && rec.containsKey("_total_link_requests_found")) {
                try { result.totalLinkRequestsFound = Integer.parseInt(rec.get("_total_link_requests_found")); }
                catch (NumberFormatException ignored) {}
            }
            if (result.linksOk == 0 && rec.containsKey("_links_ok")) {
                try { result.linksOk = Integer.parseInt(rec.get("_links_ok")); }
                catch (NumberFormatException ignored) {}
            }
            if (result.linksWithErrors == 0 && rec.containsKey("_links_with_errors")) {
                try { result.linksWithErrors = Integer.parseInt(rec.get("_links_with_errors")); }
                catch (NumberFormatException ignored) {}
            }
            LinkFields lk = new LinkFields();
            lk.uniqueId      = rec.getOrDefault("unique_id", "");
            lk.id            = rec.getOrDefault("event_id", "");
            lk.ownerUniqueId = rec.getOrDefault("owner_unique_id", "");
            lk.ownerId       = rec.containsKey("owner_id") ? rec.get("owner_id") : rec.getOrDefault("owner_event_id", "");
            lk.owner         = rec.getOrDefault("owner", "");
            lk.timestamp     = rec.getOrDefault("timestamp", "");
            lk.location      = rec.getOrDefault("loc", "");
            lk.locationSeparator = rec.getOrDefault("loc_delim", "");
            lk.type          = rec.getOrDefault("type", "");
            lk.eventA        = rec.getOrDefault("event_id_a", "");
            lk.eventB        = rec.getOrDefault("event_id_b", "");
            lk.uniqueIdA     = rec.getOrDefault("unique_id_a", "");
            lk.uniqueIdB     = rec.getOrDefault("unique_id_b", "");
            lk.category      = rec.getOrDefault("category", "");
            if (rec.containsKey("strength_a")) {
                try { lk.strengthA = Double.parseDouble(rec.get("strength_a")); }
                catch (NumberFormatException ignored) {}
            }
            if (rec.containsKey("strength_b")) {
                try { lk.strengthB = Double.parseDouble(rec.get("strength_b")); }
                catch (NumberFormatException ignored) {}
            }
            result.linkResults.add(lk);
        }
        return result;
    }

    // =========================================================================
    // Wire-level helpers
    // =========================================================================

    /** Decodes a 9-byte length field at the given offset. Accepts hex (x+8digits) or decimal. */
    private static long decodeLengthField(byte[] data, int offset) {
        // Find end of non-null bytes
        int end = offset + 9;
        while (end > offset && (data[end - 1] == 0 || data[end - 1] == ' ')) end--;
        if (end <= offset) return 0;
        String s = new String(data, offset, end - offset, StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) return 0;
        if (s.charAt(0) == 'x') return Long.parseLong(s.substring(1), 16);
        return Long.parseLong(s, 10);
    }

    private static void requireBytes(byte[] data, long required, String field, Message msg) {
        if (data.length < required) {
            String err = "message too short for " + field + ": need " + required + " bytes, got " + data.length;
            setResponseError(msg, err);
            throw new IllegalArgumentException(err);
        }
    }

    private static boolean payloadContainsLinkRecords(PayloadFields p) {
        if (p == null || p.data == null) return false;
        if (p.data instanceof String) return ((String) p.data).startsWith("_link=");
        if (p.data instanceof byte[]) {
            byte[] b = (byte[]) p.data;
            return b.length >= 6 && b[0] == '_' && b[1] == 'l' && b[2] == 'i'
                    && b[3] == 'n' && b[4] == 'k' && b[5] == '=';
        }
        return false;
    }

    private static String getPayloadString(PayloadFields p) {
        if (p == null || p.data == null) return "";
        if (p.data instanceof String) return (String) p.data;
        if (p.data instanceof byte[]) return new String((byte[]) p.data, StandardCharsets.UTF_8);
        return "";
    }

    private static String trimNulls(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\u0000') end--;
        return (end < s.length()) ? s.substring(0, end) : s;
    }

    private static Map<String, String> parseTabDelimited(String line) {
        Map<String, String> map = new HashMap<>();
        for (String field : line.split("\t", -1)) {
            if (field.isEmpty()) continue;
            int eq = field.indexOf('=');
            if (eq > 0) map.put(field.substring(0, eq), field.substring(eq + 1));
        }
        return map;
    }

    private static Map<String, String> parseTabDelimitedSplit(String line) {
        Map<String, String> map = new HashMap<>();
        for (String field : line.split("\t", -1)) {
            if (field.isEmpty()) continue;
            String[] parts = field.split("=", 2);
            if (parts.length == 2) map.put(parts[0], parts[1]);
        }
        return map;
    }

    private static String forceAsciiStr(String s) {
        return MessageUtils.forceAscii(s);
    }

    private static int parseIntField(Map<String, String> m, String primary, String alt) {
        String v = m.containsKey(primary) ? m.get(primary) : m.get(alt);
        if (v == null) return 0;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return 0; }
    }

    private static void parseLong(Map<String, String> m, String key, java.util.function.LongConsumer consumer) {
        String v = m.get(key);
        if (v != null) {
            try { consumer.accept(Long.parseLong(v)); }
            catch (NumberFormatException ignored) {}
        }
    }

    private static EventFields copyEvent(EventFields src) {
        EventFields copy = new EventFields();
        copy.id              = src.id;
        copy.uniqueId        = src.uniqueId;
        copy.localId         = src.localId;
        copy.owner           = src.owner;
        copy.ownerUniqueId   = src.ownerUniqueId;
        copy.timestamp       = src.timestamp;
        copy.dateTime        = src.dateTime;
        copy.location        = src.location;
        copy.locationSeparator = src.locationSeparator;
        copy.type            = src.type;
        copy.tags            = src.tags;
        copy.links           = src.links;
        copy.payloadData     = src.payloadData;
        copy.status          = src.status;
        copy.hits            = src.hits;
        return copy;
    }

    // =========================================================================
    // Ensure helpers
    // =========================================================================

    private static ResponseFields ensureResponse(Message msg) {
        if (msg.response == null) msg.response = new ResponseFields();
        return msg.response;
    }

    private static EventFields ensureEvent(Message msg) {
        if (msg.event == null) msg.event = new EventFields();
        return msg.event;
    }

    private static PayloadFields ensurePayload(Message msg) {
        if (msg.payload == null) msg.payload = new PayloadFields();
        return msg.payload;
    }

    private static void setResponseError(Message msg, String errMsg) {
        if (msg.response == null) msg.response = new ResponseFields();
        msg.response.status = "ERROR";
        msg.response.message = errMsg;
    }
}
