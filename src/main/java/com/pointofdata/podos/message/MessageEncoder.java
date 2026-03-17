package com.pointofdata.podos.message;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.pointofdata.podos.message.MessageConstants.*;
import static com.pointofdata.podos.message.MessageUtils.*;

/**
 * Encodes {@link Message} objects into raw wire-format byte arrays.
 *
 * <p>Wire format:
 * <pre>
 * [totalLength:9][toLength:9][fromLength:9][headerLength:9]
 * [messageType:9][dataType:9][payloadLength:9]
 * [TO bytes][FROM bytes][HEADER bytes][PAYLOAD bytes]
 * </pre>
 *
 * <p>All 9-byte length fields use hex encoding: {@code x} + 8 hex digits.
 * messageType and dataType use zero-padded 9-digit decimal.
 */
public final class MessageEncoder {

    private MessageEncoder() {}

    /**
     * Encodes a {@link Message} into a {@link SocketMessage} ready for transmission.
     *
     * @param msg              the message to encode (must not be null)
     * @param conversationUuid a UUID identifying this conversation (for routing)
     * @return encoded {@link SocketMessage}
     * @throws IllegalArgumentException if msg is null or addresses are invalid
     * @throws IllegalStateException    if the encoded message exceeds max size
     */
    public static SocketMessage encodeMessage(Message msg, String conversationUuid) {
        if (msg == null) {
            throw new IllegalArgumentException("message cannot be nil");
        }
        validateAddresses(msg);

        // Build the wire header
        String header = HeaderBuilder.constructHeader(msg, msg.intent, conversationUuid);

        // Build payload bytes
        byte[] payloadBytes = buildPayloadBytes(msg);

        // Validate payload size
        if (payloadBytes != null && payloadBytes.length > MAX_MESSAGE_SIZE_BYTES) {
            throw new IllegalStateException(
                    "payload size " + payloadBytes.length + " exceeds maximum " + MAX_MESSAGE_SIZE_BYTES);
        }
        if (payloadBytes == null) payloadBytes = new byte[0];

        byte[] toBytes     = forceAscii(msg.to).getBytes(StandardCharsets.US_ASCII);
        byte[] fromBytes   = forceAscii(msg.from).getBytes(StandardCharsets.US_ASCII);
        byte[] headerBytes = forceAscii(header).getBytes(StandardCharsets.US_ASCII);

        // 9-byte length fields (hex-encoded for variable-length fields, decimal for type fields)
        String totalLengthEnc   = encodeLengthHex(
                9 + toBytes.length
                + 9 + fromBytes.length
                + 9 + headerBytes.length
                + 9   // messageType
                + 9   // dataType
                + 9 + payloadBytes.length
                + 9); // totalLength field itself

        // Recompute total length correctly
        int contentLen = toBytes.length + fromBytes.length + headerBytes.length + payloadBytes.length;
        // 9 bytes for each of the 7 length fields = 63 bytes, plus the content
        int totalLength = 63 + contentLen;
        if (totalLength > MAX_MESSAGE_SIZE_BYTES) {
            throw new IllegalStateException(
                    "encoded message size " + totalLength + " exceeds maximum " + MAX_MESSAGE_SIZE_BYTES);
        }

        String totalLengthField   = encodeLengthHex(totalLength);
        String toLengthField      = encodeLengthHex(toBytes.length);
        String fromLengthField    = encodeLengthHex(fromBytes.length);
        String headerLengthField  = encodeLengthHex(headerBytes.length);
        String messageTypeField   = encodeLengthDecimal(msg.intent != null ? msg.intent.messageType : 0);
        String dataTypeField      = encodeLengthDecimal(msg.payload != null ? msg.payload.dataType.getValue() : 0);
        String payloadLengthField = encodeLengthHex(payloadBytes.length);

        // Assemble full message
        byte[] prefixBytes = (totalLengthField + toLengthField + fromLengthField + headerLengthField
                + messageTypeField + dataTypeField + payloadLengthField)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] result = new byte[prefixBytes.length + toBytes.length + fromBytes.length
                + headerBytes.length + payloadBytes.length];
        int pos = 0;
        System.arraycopy(prefixBytes, 0, result, pos, prefixBytes.length); pos += prefixBytes.length;
        System.arraycopy(toBytes,     0, result, pos, toBytes.length);     pos += toBytes.length;
        System.arraycopy(fromBytes,   0, result, pos, fromBytes.length);   pos += fromBytes.length;
        System.arraycopy(headerBytes, 0, result, pos, headerBytes.length); pos += headerBytes.length;
        System.arraycopy(payloadBytes,0, result, pos, payloadBytes.length);

        return new SocketMessage(result, header);
    }

    // -------------------------------------------------------------------------
    // Payload builders
    // -------------------------------------------------------------------------

    private static byte[] buildPayloadBytes(Message msg) {
        if (msg.intent == null) return new byte[0];
        String intentName = msg.intent.name;
        Object payloadData = msg.payloadData();

        if ("GatewayId".equals(intentName) || "GatewayStreamOn".equals(intentName)) {
            return new byte[0];
        }

        if ("StoreBatchEvents".equals(intentName)) {
            return buildStoreBatchEventsPayload(msg, payloadData);
        }
        if ("StoreBatchLinks".equals(intentName)) {
            return buildStoreBatchLinksPayload(msg, payloadData);
        }
        if ("StoreBatchTags".equals(intentName)) {
            return buildStoreBatchTagsPayload(msg, payloadData);
        }

        // Generic payload
        if (payloadData == null) return new byte[0];
        if (payloadData instanceof String) {
            return ((String) payloadData).getBytes(StandardCharsets.UTF_8);
        }
        if (payloadData instanceof byte[]) {
            return (byte[]) payloadData;
        }
        if (payloadData instanceof List) {
            // Join as string
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) payloadData) {
                sb.append(item);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    @SuppressWarnings("unchecked")
    private static byte[] buildStoreBatchEventsPayload(Message msg, Object payloadData) {
        if (payloadData instanceof List) {
            List<?> list = (List<?>) payloadData;
            if (!list.isEmpty() && list.get(0) instanceof BatchEventSpec) {
                String formatted = formatBatchEventsPayload((List<BatchEventSpec>) list);
                return formatted.getBytes(StandardCharsets.UTF_8);
            }
            // String list — join
            StringBuilder sb = new StringBuilder();
            for (Object item : list) sb.append(item);
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (payloadData instanceof String) {
            return ((String) payloadData).getBytes(StandardCharsets.UTF_8);
        }
        // Also check NeuralMemory.batchEvents
        if (msg.neuralMemory != null && !msg.neuralMemory.batchEvents.isEmpty()) {
            String formatted = formatBatchEventsPayload(msg.neuralMemory.batchEvents);
            return formatted.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    @SuppressWarnings("unchecked")
    private static byte[] buildStoreBatchLinksPayload(Message msg, Object payloadData) {
        if (payloadData instanceof List) {
            List<?> list = (List<?>) payloadData;
            if (!list.isEmpty() && list.get(0) instanceof BatchLinkEventSpec) {
                String formatted = formatBatchLinkEventsPayload((List<BatchLinkEventSpec>) list);
                return formatted.getBytes(StandardCharsets.UTF_8);
            }
        }
        if (payloadData instanceof String) {
            return ((String) payloadData).getBytes(StandardCharsets.UTF_8);
        }
        if (msg.neuralMemory != null && !msg.neuralMemory.batchLinks.isEmpty()) {
            String formatted = formatBatchLinkEventsPayload(msg.neuralMemory.batchLinks);
            return formatted.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    @SuppressWarnings("unchecked")
    private static byte[] buildStoreBatchTagsPayload(Message msg, Object payloadData) {
        if (payloadData instanceof List) {
            List<?> list = (List<?>) payloadData;
            if (!list.isEmpty() && list.get(0) instanceof Tag) {
                String formatted = formatBatchTagsPayload((List<Tag>) list);
                return formatted.getBytes(StandardCharsets.UTF_8);
            }
        }
        if (payloadData instanceof String) {
            return ((String) payloadData).getBytes(StandardCharsets.UTF_8);
        }
        if (msg.neuralMemory != null && !msg.neuralMemory.tags.isEmpty()) {
            String formatted = formatBatchTagsPayload(msg.neuralMemory.tags);
            return formatted.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    // =========================================================================
    // Public static batch formatters (mirroring Go FormatBatch* functions)
    // =========================================================================

    /**
     * Formats a list of {@link BatchEventSpec} into the StoreBatchEvents payload format.
     * Each event is a tab-separated line of {@code key=value} pairs.
     * Tags are appended as {@code tag_N=freq:key=value}.
     * Records are newline-separated.
     */
    public static String formatBatchEventsPayload(List<BatchEventSpec> events) {
        if (events == null || events.isEmpty()) return "";
        StringBuilder result = new StringBuilder(events.size() * 200);
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) result.append('\n');
            BatchEventSpec spec = events.get(i);
            appendEventFields(result, spec.event);
            if (spec.tags != null && !spec.tags.isEmpty()) {
                appendTagsForBatchPayload(result, spec.tags);
            }
        }
        return result.toString();
    }

    /**
     * Formats a list of {@link BatchLinkEventSpec} into the StoreBatchLinks payload format.
     * Each record is a tab-separated line of {@code key=value} pairs.
     * Records are newline-separated.
     */
    public static String formatBatchLinkEventsPayload(List<BatchLinkEventSpec> events) {
        if (events == null || events.isEmpty()) return "";
        StringBuilder result = new StringBuilder(events.size() * 200);
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) result.append('\n');
            BatchLinkEventSpec spec = events.get(i);
            appendEventFields(result, spec.event);
            appendLinkFields(result, spec.link);
        }
        return result.toString();
    }

    /**
     * Formats a list of {@link Tag} into the StoreBatchTags payload format.
     * Each tag is a line: {@code frequency=key=value}.
     * Records are newline-separated.
     */
    public static String formatBatchTagsPayload(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder result = new StringBuilder(tags.size() * 50);
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) result.append('\n');
            Tag tag = tags.get(i);
            result.append(tag.frequency).append('=').append(tag.key).append('=')
                  .append(MessageUtils.serializeTagValue(tag.value));
        }
        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Field formatters
    // -------------------------------------------------------------------------

    private static void appendEventFields(StringBuilder sb, EventFields ev) {
        if (ev == null) return;
        boolean first = sb.length() == 0 || sb.charAt(sb.length() - 1) == '\n';
        appendField(sb, "unique_id",  ev.uniqueId,         !first);
        appendField(sb, "event_id",   forceAscii(ev.id),   true);
        appendField(sb, "owner",      ev.owner,             true);
        appendField(sb, "timestamp",  ev.timestamp,         true);
        appendField(sb, "loc_delim",  ev.locationSeparator, true);
        appendField(sb, "loc",        ev.location,          true);
        appendField(sb, "type",       ev.type,              true);
    }

    private static void appendLinkFields(StringBuilder sb, LinkFields lk) {
        if (lk == null) return;
        appendField(sb, "unique_id",    lk.uniqueId,         true);
        appendField(sb, "event_id_a",   forceAscii(lk.eventA), true);
        appendField(sb, "event_id_b",   forceAscii(lk.eventB), true);
        appendField(sb, "unique_id_a",  lk.uniqueIdA,        true);
        appendField(sb, "unique_id_b",  lk.uniqueIdB,        true);
        if (lk.strengthA != 0) appendField(sb, "strength_a", String.valueOf(lk.strengthA), true);
        if (lk.strengthB != 0) appendField(sb, "strength_b", String.valueOf(lk.strengthB), true);
        appendField(sb, "category",     lk.category,         true);
        appendField(sb, "owner",        lk.owner,            true);
        appendField(sb, "timestamp",    lk.timestamp,        true);
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean tab) {
        if (value == null || value.isEmpty()) return;
        if (tab) sb.append('\t');
        sb.append(key).append('=').append(value);
    }

    /** Appends tags as {@code \ttag_N=freq:key=value} to the builder. */
    private static void appendTagsForBatchPayload(StringBuilder sb, List<Tag> tags) {
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            sb.append('\t').append("tag_").append(i)
              .append('=').append(tag.frequency).append(':').append(tag.key)
              .append('=').append(MessageUtils.serializeTagValue(tag.value));
        }
    }

    // -------------------------------------------------------------------------
    // Address validation
    // -------------------------------------------------------------------------

    private static void validateAddresses(Message msg) {
        if (msg.to == null || !msg.to.contains("@")) {
            throw new IllegalArgumentException(
                    "To address must be in the format <ActorName>@<GatewayName>. Got: " + msg.to);
        }
        String[] toParts = msg.to.split("@", 2);
        if (toParts[0].isEmpty()) {
            throw new IllegalArgumentException("Actor name in To address cannot be empty");
        }
        if (toParts[1].isEmpty()) {
            throw new IllegalArgumentException("Gateway name in To address cannot be empty");
        }
        if (msg.from == null || !msg.from.contains("@")) {
            throw new IllegalArgumentException(
                    "From address must be in the format <ActorName>@<GatewayName>. Got: " + msg.from);
        }
        String[] fromParts = msg.from.split("@", 2);
        if (fromParts[0].isEmpty()) {
            throw new IllegalArgumentException("Client name in From address cannot be empty");
        }
        if (fromParts[1].isEmpty()) {
            throw new IllegalArgumentException("Gateway name in From address cannot be empty");
        }
    }
}
