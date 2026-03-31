package com.pointofdata.podos.message;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static com.pointofdata.podos.message.MessageConstants.*;
import static com.pointofdata.podos.message.ValidationError.*;

/**
 * Validates Pod-OS {@link Message} objects before encoding, and raw wire-format
 * {@code byte[]} after receiving. Produces structured, dual-audience
 * (engineer + LLM) validation errors for every Intent.
 *
 * <h2>Environment Gate</h2>
 * <p>Validation is <b>disabled by default</b> and controlled by the
 * {@code PODOS_VALIDATE} environment variable (read once at class-load).
 * Accepted values: {@code "1"}, {@code "true"}, {@code "yes"}
 * (case-insensitive). When disabled, both {@link #validate(Message)} and
 * {@link #validateRawMessage(byte[])} return {@code null} immediately —
 * a single boolean check with zero allocations on the hot path.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Pre-send (set PODOS_VALIDATE=1 in dev/staging; unset in production)
 * ValidationErrors errs = MessageValidator.validate(msg);
 * if (errs != null && !errs.isEmpty()) {
 *     log.error(errs.error());        // engineer format
 *     log.debug(errs.llmJson());      // LLM/structured format
 * }
 *
 * // Wire validation on receipt
 * ValidationErrors wireErrs = MessageValidator.validateRawMessage(raw);
 * }</pre>
 */
public final class MessageValidator {

    private MessageValidator() {}

    // =========================================================================
    // Environment gate — zero cost when disabled
    // =========================================================================

    static final boolean validationEnabled;

    static {
        String v = System.getenv("PODOS_VALIDATE");
        if (v == null) v = "";
        v = v.trim().toLowerCase(Locale.ROOT);
        validationEnabled = "1".equals(v) || "true".equals(v) || "yes".equals(v);
    }

    /** Returns {@code true} if validation is enabled (PODOS_VALIDATE env var). */
    public static boolean isEnabled() { return validationEnabled; }

    // =========================================================================
    // Intent dispatch table
    // =========================================================================

    private static final Map<String, Function<Message, List<ValidationError>>> INTENT_VALIDATORS;

    static {
        Map<String, Function<Message, List<ValidationError>>> m = new HashMap<>(32);
        m.put("StoreEvent",        MessageValidator::validateStoreEvent);
        m.put("StoreEventResponse",       MessageValidator::validateStoreEventResponse);
        m.put("StoreBatchEvents",  MessageValidator::validateStoreBatchEvents);
        m.put("StoreBatchEventsResponse",  MessageValidator::validateStoreBatchEventsResponse);
        m.put("StoreBatchTags",    MessageValidator::validateStoreBatchTags);
        m.put("StoreBatchTagsResponse",    MessageValidator::validateStoreBatchTagsResponse);
        m.put("GetEvent",          MessageValidator::validateGetEvent);
        m.put("GetEventResponse",  MessageValidator::validateGetEventResponse);
        m.put("GetEventsForTags",  MessageValidator::validateGetEventsForTags);
        m.put("GetEventsForTagsResponse",  MessageValidator::validateGetEventsForTagsResponse);
        m.put("LinkEvent",         MessageValidator::validateLinkEvent);
        m.put("LinkEventResponse", MessageValidator::validateLinkEventResponse);
        m.put("UnlinkEvent",       MessageValidator::validateUnlinkEvent);
        m.put("UnlinkEventResponse",       MessageValidator::validateUnlinkEventResponse);
        m.put("StoreBatchLinks",   MessageValidator::validateStoreBatchLinks);
        m.put("StoreBatchLinksResponse",   MessageValidator::validateStoreBatchLinksResponse);
        m.put("GatewayId",        MessageValidator::validateGatewayId);
        m.put("GatewayStreamOn",  MessageValidator::validateGatewayStreamOnOff);
        m.put("GatewayStreamOff", MessageValidator::validateGatewayStreamOnOff);
        m.put("ActorRequest",     MessageValidator::validateActorRequest);
        m.put("ActorResponse",    MessageValidator::validateActorResponse);
        m.put("ActorEcho",        MessageValidator::validateActorEcho);
        m.put("ActorReport",      MessageValidator::validateActorReport);
        m.put("Status",           MessageValidator::validateStatus);
        INTENT_VALIDATORS = Collections.unmodifiableMap(m);
    }

    // =========================================================================
    // Public API — struct validation
    // =========================================================================

    /**
     * Validates a {@link Message} before encoding.
     * Returns {@code null} if validation is disabled.
     * Returns all violations at once (not just the first).
     */
    public static ValidationErrors validate(Message msg) {
        if (!validationEnabled) return null;
        List<ValidationError> errs = new ArrayList<>();

        errs.addAll(validateEnvelope(msg));

        if (msg.intent != null && msg.intent.name != null) {
            Function<Message, List<ValidationError>> fn = INTENT_VALIDATORS.get(msg.intent.name);
            if (fn != null) {
                errs.addAll(fn.apply(msg));
            } else {
                errs.add(ValidationError.builder()
                        .severity(SEVERITY_WARN)
                        .intent(msg.intent.name)
                        .field("Intent.Name")
                        .rule(RULE_UNCOVERED)
                        .message("Validation for intent '" + msg.intent.name + "' is not yet implemented.")
                        .fix("No action required; message will be sent as-is.")
                        .references("message/MessageValidator.java:INTENT_VALIDATORS")
                        .build());
            }
        }

        return ValidationErrors.of(errs);
    }

    // =========================================================================
    // Public API — wire validation
    // =========================================================================

    /**
     * Validates a raw wire-format {@code byte[]} message.
     * Returns {@code null} if validation is disabled.
     *
     * <p>Stage 1: framing, length prefixes, To/From format.
     * <p>Stage 2: per-intent header field and payload presence checks.
     */
    public static ValidationErrors validateRawMessage(byte[] raw) {
        if (!validationEnabled) return null;
        List<ValidationError> errs = new ArrayList<>();

        // Stage 1 — wire framing
        if (raw == null) {
            errs.add(err("RawMessage", "raw", "", RULE_NIL_STRUCT,
                    "Raw message byte array is null.",
                    "Provide a non-null byte[] containing a valid Pod-OS wire message.",
                    "byte[] raw = MessageEncoder.encodeMessage(msg, uuid).data();",
                    "message/MessageEncoder.java:encodeMessage"));
            return ValidationErrors.of(errs);
        }
        if (raw.length < MIN_MESSAGE_SIZE) {
            errs.add(err("RawMessage", "raw.length", "", RULE_FORMAT,
                    "Message too short: expected at least " + MIN_MESSAGE_SIZE + " bytes, got " + raw.length + ".",
                    "Ensure the raw message contains the full 63-byte prefix plus content.",
                    "",
                    "message/MessageConstants.java:MIN_MESSAGE_SIZE"));
            return ValidationErrors.of(errs);
        }
        if (raw.length > MAX_MESSAGE_SIZE_BYTES) {
            errs.add(err("RawMessage", "raw.length", "", RULE_FORMAT,
                    "Message too large: " + raw.length + " bytes exceeds maximum " + MAX_MESSAGE_SIZE_BYTES + ".",
                    "Reduce message payload size to fit within 2 GiB.",
                    "",
                    "message/MessageConstants.java:MAX_MESSAGE_SIZE_BYTES"));
            return ValidationErrors.of(errs);
        }

        long totalLength, toLength, fromLength, headerLength, messageType, dataType, payloadLength;
        try { totalLength   = decodeLengthField(raw, 0);  } catch (Exception e) { errs.add(lengthFieldErr("totalLength",   0));  return ValidationErrors.of(errs); }
        try { toLength      = decodeLengthField(raw, 9);  } catch (Exception e) { errs.add(lengthFieldErr("toLength",      9));  return ValidationErrors.of(errs); }
        try { fromLength    = decodeLengthField(raw, 18); } catch (Exception e) { errs.add(lengthFieldErr("fromLength",    18)); return ValidationErrors.of(errs); }
        try { headerLength  = decodeLengthField(raw, 27); } catch (Exception e) { errs.add(lengthFieldErr("headerLength",  27)); return ValidationErrors.of(errs); }
        try { messageType   = decodeLengthField(raw, 36); } catch (Exception e) { errs.add(lengthFieldErr("messageType",   36)); return ValidationErrors.of(errs); }
        try { dataType      = decodeLengthField(raw, 45); } catch (Exception e) { errs.add(lengthFieldErr("dataType",      45)); return ValidationErrors.of(errs); }
        try { payloadLength = decodeLengthField(raw, 54); } catch (Exception e) { errs.add(lengthFieldErr("payloadLength", 54)); return ValidationErrors.of(errs); }

        long expectedLen = LENGTHS_SECTION_SIZE + toLength + fromLength + headerLength + payloadLength;
        if (raw.length < expectedLen) {
            errs.add(err("RawMessage", "raw.length", "", RULE_FORMAT,
                    "Actual length " + raw.length + " is less than computed length " + expectedLen + " (to=" + toLength + " from=" + fromLength + " header=" + headerLength + " payload=" + payloadLength + ").",
                    "Ensure all bytes of the wire message are captured.",
                    "", "message/MessageDecoder.java:decodeMessage"));
        }

        long toStart = LENGTHS_SECTION_SIZE;
        long fromStart = toStart + toLength;
        long headerStart = fromStart + fromLength;

        // To / From format
        if (toLength == 0) {
            errs.add(err("RawMessage", "To", "", RULE_FORMAT,
                    "To address is empty.",
                    "Set To to a non-empty 'name@gateway' address.",
                    "msg.to = \"mem@zeroth.pod-os.com\"",
                    "message/Envelope.java:to"));
        } else {
            String to = new String(raw, (int) toStart, (int) toLength, StandardCharsets.UTF_8);
            if (!to.contains("@") || to.startsWith("@") || to.endsWith("@")) {
                errs.add(err("RawMessage", "To", "", RULE_FORMAT,
                        "To address '" + to + "' must be in 'name@gateway' format.",
                        "Set To to '<ActorName>@<GatewayName>'.",
                        "msg.to = \"mem@zeroth.pod-os.com\"",
                        "message/Envelope.java:to"));
            }
        }
        if (fromLength == 0) {
            errs.add(err("RawMessage", "From", "", RULE_FORMAT,
                    "From address is empty.",
                    "Set From to a non-empty 'name@gateway' address.",
                    "msg.from = \"MyClient@zeroth.pod-os.com\"",
                    "message/Envelope.java:from"));
        } else {
            String from = new String(raw, (int) fromStart, (int) fromLength, StandardCharsets.UTF_8);
            String fromClean = from.contains("|") ? from.substring(0, from.indexOf('|')) : from;
            if (!fromClean.contains("@") || fromClean.startsWith("@") || fromClean.endsWith("@")) {
                errs.add(err("RawMessage", "From", "", RULE_FORMAT,
                        "From address '" + fromClean + "' must be in 'name@gateway' format.",
                        "Set From to '<ClientName>@<GatewayName>'.",
                        "msg.from = \"MyClient@zeroth.pod-os.com\"",
                        "message/Envelope.java:from"));
            }
        }

        int messageTypeInt = (int) messageType;
        Optional<Intent> intentOpt = IntentTypes.INSTANCE.intentFromMessageTypeInt(messageTypeInt);
        if (!intentOpt.isPresent() && messageTypeInt != 1000 && messageTypeInt != 1001) {
            errs.add(err("RawMessage", "messageType", "", RULE_FORMAT,
                    "Unknown messageType: " + messageTypeInt + ".",
                    "Use a valid messageType from IntentTypes.",
                    "", "message/IntentTypes.java"));
        }

        // Stage 2 — per-intent header field validation
        if (headerLength > 0 && raw.length >= headerStart + headerLength) {
            String headerStr = new String(raw, (int) headerStart, (int) headerLength, StandardCharsets.UTF_8);
            Map<String, String> hm = parseHeaderString(headerStr);

            // NeuralMemory intents (1000/1001)
            if (messageTypeInt == 1000 || messageTypeInt == 1001) {
                String dbCmd = hm.getOrDefault("_db_cmd", "");
                if (dbCmd.isEmpty()) {
                    errs.add(err("RawMessage", "header._db_cmd", "_db_cmd", RULE_HEADER_MISSING,
                            "Header '_db_cmd' is required for messageType " + messageTypeInt + ".",
                            "The encoder should set _db_cmd from Intent.NeuralMemoryCommand.",
                            "", "message/HeaderBuilder.java"));
                } else {
                    errs.addAll(validateWireHeaderForCommand(dbCmd, hm, messageTypeInt, payloadLength));
                }
            } else {
                errs.addAll(validateWireHeaderForMessageType(messageTypeInt, hm));
            }
        }

        return ValidationErrors.of(errs);
    }

    // =========================================================================
    // Public API — AI-assisted remediation
    // =========================================================================

    /**
     * Submits validation errors to a vLLM endpoint (OpenAI-compatible
     * {@code /v1/chat/completions}) for AI-assisted remediation.
     *
     * @param errs     validation errors
     * @param endpoint base URL, e.g. {@code "http://localhost:8000"}
     * @return AI-generated explanation and corrected code snippet
     * @throws IOException if the HTTP call fails
     */
    public static String explainValidationErrors(ValidationErrors errs, String endpoint) throws IOException {
        return explainValidationErrors(errs, endpoint, "default");
    }

    /**
     * Submits validation errors to a vLLM endpoint with a specified model.
     *
     * @param errs     validation errors
     * @param endpoint base URL
     * @param model    model name for the vLLM endpoint
     * @return AI-generated explanation and corrected code
     * @throws IOException if the HTTP call fails
     */
    public static String explainValidationErrors(ValidationErrors errs, String endpoint, String model) throws IOException {
        if (errs == null || errs.isEmpty()) return "No validation errors to explain.";

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Pod-OS Java client expert. Message validation errors occurred.\n\n");
        for (ValidationError e : errs) {
            prompt.append("Intent: ").append(e.intent()).append('\n');
            prompt.append("Struct Path: ").append(e.field()).append('\n');
            prompt.append("Wire Field: ").append(e.wireField()).append('\n');
            prompt.append("Rule Violated: ").append(e.rule()).append('\n');
            prompt.append("Description: ").append(e.message()).append('\n');
            prompt.append("Suggested Fix: ").append(e.fix()).append('\n');
            prompt.append("Example Code: ").append(e.exampleCode()).append('\n');
            prompt.append("Source References: ").append(String.join(", ", e.references())).append('\n');
            prompt.append('\n');
        }
        prompt.append("Task: Provide corrected Java code for this message construction. ");
        prompt.append("Show all required fields for the intent. ");
        prompt.append("If multiple valid approaches exist (e.g. eventA/eventB vs uniqueIdA/uniqueIdB), show both. ");
        prompt.append("Use only types from the com.pointofdata.podos.message package.");

        String requestBody = buildChatCompletionJson(prompt.toString(), model);
        String url = endpoint.endsWith("/") ? endpoint : endpoint + "/";
        url += "v1/chat/completions";

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            byte[] body;
            if (status >= 200 && status < 300) {
                body = conn.getInputStream().readAllBytes();
            } else {
                body = conn.getErrorStream() != null ? conn.getErrorStream().readAllBytes() : new byte[0];
                throw new IOException("vLLM returned HTTP " + status + ": " + new String(body, StandardCharsets.UTF_8));
            }
            return extractContentFromChatCompletion(new String(body, StandardCharsets.UTF_8));
        } finally {
            conn.disconnect();
        }
    }

    // =========================================================================
    // Envelope validator
    // =========================================================================

    private static List<ValidationError> validateEnvelope(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        if (msg.to == null || msg.to.isEmpty()) {
            errs.add(err("Envelope", "To", "", RULE_REQUIRED,
                    "To address is required.",
                    "Set msg.to to '<ActorName>@<GatewayName>'.",
                    "msg.to = \"mem@zeroth.pod-os.com\"",
                    "message/Envelope.java:to"));
        } else if (!msg.to.contains("@") || msg.to.startsWith("@") || msg.to.endsWith("@")) {
            errs.add(err("Envelope", "To", "", RULE_FORMAT,
                    "To address must be in 'name@gateway' format. Got: '" + msg.to + "'.",
                    "Set msg.to to '<ActorName>@<GatewayName>'.",
                    "msg.to = \"mem@zeroth.pod-os.com\"",
                    "message/Envelope.java:to"));
        }

        if (msg.from == null || msg.from.isEmpty()) {
            errs.add(err("Envelope", "From", "", RULE_REQUIRED,
                    "From address is required.",
                    "Set msg.from to '<ClientName>@<GatewayName>'.",
                    "msg.from = \"MyClient@zeroth.pod-os.com\"",
                    "message/Envelope.java:from"));
        } else if (!msg.from.contains("@") || msg.from.startsWith("@") || msg.from.endsWith("@")) {
            errs.add(err("Envelope", "From", "", RULE_FORMAT,
                    "From address must be in 'name@gateway' format. Got: '" + msg.from + "'.",
                    "Set msg.from to '<ClientName>@<GatewayName>'.",
                    "msg.from = \"MyClient@zeroth.pod-os.com\"",
                    "message/Envelope.java:from"));
        }

        if (msg.intent == null || msg.intent.isEmpty()) {
            errs.add(err("Envelope", "Intent", "", RULE_REQUIRED,
                    "Intent is required and cannot be empty/null.",
                    "Set msg.intent to a valid Intent from IntentTypes.INSTANCE.",
                    "msg.intent = IntentTypes.INSTANCE.StoreEvent",
                    "message/IntentTypes.java"));
        }

        return errs;
    }

    // =========================================================================
    // Per-intent struct validators — NeuralMemory Request intents
    // =========================================================================

    private static List<ValidationError> validateStoreEvent(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "StoreEvent";
        EventFields ev = msg.event;
        if (ev == null) {
            errs.add(nilStruct(intent, "Event", "Initialize msg.event = new EventFields()"));
            return errs;
        }
        if (isEmpty(ev.timestamp)) {
            errs.add(warn(intent, "Event.Timestamp", "timestamp", RULE_REQUIRED,
                    "Timestamp is required; encoder will auto-generate if empty.",
                    "Set msg.event.timestamp = MessageUtils.getTimestamp() for explicit control.",
                    "msg.event.timestamp = MessageUtils.getTimestamp()",
                    "message/EventFields.java:timestamp"));
        }
        if (isEmpty(ev.owner) && isEmpty(ev.ownerUniqueId)) {
            errs.add(err(intent, "Event.Owner / Event.OwnerUniqueID", "owner / owner_unique_id", RULE_ONE_OF_REQUIRED,
                    "Either Event.owner or Event.ownerUniqueId is required.",
                    "Set msg.event.owner (e.g. \"$sys\") or msg.event.ownerUniqueId.",
                    "msg.event.owner = \"$sys\"",
                    "message/EventFields.java:owner", "message/EventFields.java:ownerUniqueId"));
        }
        if (isEmpty(ev.location)) {
            errs.add(err(intent, "Event.Location", "loc", RULE_REQUIRED,
                    "Event location is required.",
                    "Set msg.event.location to a location string (e.g. \"TERRA|47.6|-122.5\").",
                    "msg.event.location = \"TERRA|47.619463|-122.518691\"",
                    "message/EventFields.java:location"));
        }
        if (isEmpty(ev.locationSeparator)) {
            errs.add(err(intent, "Event.LocationSeparator", "loc_delim", RULE_REQUIRED,
                    "Event location separator is required.",
                    "Set msg.event.locationSeparator (typically \"|\").",
                    "msg.event.locationSeparator = \"|\"",
                    "message/EventFields.java:locationSeparator"));
        }
        return errs;
    }

    private static List<ValidationError> validateStoreEventResponse(Message msg) {
        return validateResponseCommon(msg, "StoreEventResponse");
    }

    private static List<ValidationError> validateStoreBatchEvents(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "StoreBatchEvents";
        Object payloadData = msg.payloadData();
        List<BatchEventSpec> batch = null;
        if (payloadData instanceof List) {
            List<?> list = (List<?>) payloadData;
            if (!list.isEmpty() && list.get(0) instanceof BatchEventSpec) {
                @SuppressWarnings("unchecked")
                List<BatchEventSpec> cast = (List<BatchEventSpec>) list;
                batch = cast;
            }
        }
        if (batch == null && msg.neuralMemory != null && !msg.neuralMemory.batchEvents.isEmpty()) {
            batch = msg.neuralMemory.batchEvents;
        }
        if (batch == null && !(payloadData instanceof String)) {
            errs.add(err(intent, "Payload.Data", "", RULE_PAYLOAD_TYPE,
                    "Payload.data must be a List<BatchEventSpec> or a pre-formatted String.",
                    "Set msg.payload.data to a List<BatchEventSpec>, or use msg.neuralMemory.batchEvents.",
                    "msg.neuralMemory.batchEvents = Arrays.asList(new BatchEventSpec(...))",
                    "message/BatchEventSpec.java"));
            return errs;
        }
        if (batch != null) {
            for (int i = 0; i < batch.size(); i++) {
                errs.addAll(validateBatchEventSpecRecord(batch.get(i), i, intent));
            }
        }
        return errs;
    }

    private static List<ValidationError> validateStoreBatchEventsResponse(Message msg) {
        return validateResponseCommon(msg, "StoreBatchEventsResponse");
    }

    private static List<ValidationError> validateStoreBatchTags(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "StoreBatchTags";
        EventFields ev = msg.event;
        if (ev == null) {
            errs.add(nilStruct(intent, "Event", "Initialize msg.event with the target event's id/uniqueId and owner"));
        } else {
            if (isEmpty(ev.id) && isEmpty(ev.uniqueId)) {
                errs.add(err(intent, "Event.Id / Event.UniqueId", "event_id / unique_id", RULE_ONE_OF_REQUIRED,
                        "Either Event.id or Event.uniqueId is required to identify the target event.",
                        "Set msg.event.id or msg.event.uniqueId.",
                        "msg.event.id = existingEventId",
                        "message/EventFields.java:id", "message/EventFields.java:uniqueId"));
            }
            if (isEmpty(ev.owner) && isEmpty(ev.ownerUniqueId)) {
                errs.add(err(intent, "Event.Owner / Event.OwnerUniqueID", "owner / owner_unique_id", RULE_ONE_OF_REQUIRED,
                        "Either Event.owner or Event.ownerUniqueId is required.",
                        "Set msg.event.owner (e.g. \"$sys\") or msg.event.ownerUniqueId.",
                        "msg.event.owner = ownerEventId",
                        "message/EventFields.java:owner", "message/EventFields.java:ownerUniqueId"));
            }
        }

        List<Tag> tags = msg.tags();
        Object payloadData = msg.payloadData();
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasPayloadStr = payloadData instanceof String && !((String) payloadData).isEmpty();
        if (!hasTags && !hasPayloadStr) {
            errs.add(err(intent, "NeuralMemory.Tags / Payload.Data", "", RULE_REQUIRED,
                    "Tags are required for StoreBatchTags. Provide via NeuralMemory.tags or Payload.data string.",
                    "Set msg.neuralMemory.tags to a non-empty list of Tag objects.",
                    "msg.neuralMemory.tags = Arrays.asList(new Tag(1, \"key\", \"value\"))",
                    "message/NeuralMemoryFields.java:tags"));
        }
        if (hasTags) {
            for (int i = 0; i < tags.size(); i++) {
                Tag t = tags.get(i);
                if (isEmpty(t.key)) {
                    errs.add(err(intent, "NeuralMemory.Tags[" + i + "].Key", "", RULE_PAYLOAD_FORMAT,
                            "Tag key must be non-empty.",
                            "Set a non-empty key for each Tag.",
                            "new Tag(1, \"my_key\", \"my_value\")",
                            "message/Tag.java:key"));
                }
                if (t.value == null) {
                    errs.add(err(intent, "NeuralMemory.Tags[" + i + "].Value", "", RULE_PAYLOAD_FORMAT,
                            "Tag value must not be null.",
                            "Set a non-null value for each Tag.",
                            "new Tag(1, \"my_key\", \"my_value\")",
                            "message/Tag.java:value"));
                }
            }
        }
        return errs;
    }

    private static List<ValidationError> validateStoreBatchTagsResponse(Message msg) {
        return validateResponseCommon(msg, "StoreBatchTagsResponse");
    }

    private static List<ValidationError> validateGetEvent(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "GetEvent";
        EventFields ev = msg.event;
        if (ev == null) {
            errs.add(nilStruct(intent, "Event", "Initialize msg.event with the target event's id or uniqueId"));
            return errs;
        }
        if (isEmpty(ev.id) && isEmpty(ev.uniqueId)) {
            errs.add(err(intent, "Event.Id / Event.UniqueId", "event_id / unique_id", RULE_ONE_OF_REQUIRED,
                    "Either Event.id or Event.uniqueId is required to identify the event to retrieve.",
                    "Set msg.event.id or msg.event.uniqueId.",
                    "msg.event.id = existingEventId",
                    "message/EventFields.java:id", "message/EventFields.java:uniqueId"));
        }
        return errs;
    }

    private static List<ValidationError> validateGetEventResponse(Message msg) {
        return validateResponseCommon(msg, "GetEventResponse");
    }

    private static List<ValidationError> validateGetEventsForTags(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "GetEventsForTags";
        if (msg.neuralMemory == null) {
            errs.add(nilStruct(intent, "NeuralMemory", "Initialize msg.neuralMemory = new NeuralMemoryFields()"));
            return errs;
        }
        if (msg.neuralMemory.getEventsForTags == null) {
            errs.add(nilStruct(intent, "NeuralMemory.GetEventsForTags",
                    "Initialize msg.neuralMemory.getEventsForTags = new GetEventsForTagsOptions()"));
        }
        return errs;
    }

    private static List<ValidationError> validateGetEventsForTagsResponse(Message msg) {
        return validateResponseCommon(msg, "GetEventsForTagsResponse");
    }

    private static List<ValidationError> validateLinkEvent(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "LinkEvent";

        if (msg.neuralMemory == null || msg.neuralMemory.link == null) {
            errs.add(nilStruct(intent, "NeuralMemory.Link", "Initialize msg.neuralMemory.link = new LinkFields()"));
            return errs;
        }
        LinkFields lk = msg.neuralMemory.link;

        boolean hasEventPair  = !isEmpty(lk.eventA) && !isEmpty(lk.eventB);
        boolean hasUniquePair = !isEmpty(lk.uniqueIdA) && !isEmpty(lk.uniqueIdB);
        if (!hasEventPair && !hasUniquePair) {
            errs.add(err(intent, "NeuralMemory.Link.EventA+EventB / UniqueIdA+UniqueIdB",
                    "event_id_a+event_id_b / unique_id_a+unique_id_b", RULE_ONE_OF_REQUIRED,
                    "Either (EventA + EventB) or (UniqueIdA + UniqueIdB) pair is required.",
                    "Set both lk.eventA and lk.eventB, or both lk.uniqueIdA and lk.uniqueIdB.",
                    "lk.uniqueIdA = eventIdA; lk.uniqueIdB = eventIdB",
                    "message/LinkFields.java:eventA", "message/LinkFields.java:uniqueIdA"));
        }
        if (isEmpty(lk.category)) {
            errs.add(err(intent, "NeuralMemory.Link.Category", "category", RULE_REQUIRED,
                    "Category is required for LinkEvent.",
                    "Set msg.neuralMemory.link.category to a non-empty string.",
                    "msg.neuralMemory.link.category = \"related\"",
                    "message/LinkFields.java:category"));
        }
        requireField(errs, intent, "NeuralMemory.Link.StrengthA", "strength_a", lk.strengthA,
                "msg.neuralMemory.link.strengthA = 1.0", "message/LinkFields.java:strengthA");
        requireField(errs, intent, "NeuralMemory.Link.StrengthB", "strength_b", lk.strengthB,
                "msg.neuralMemory.link.strengthB = 1.0", "message/LinkFields.java:strengthB");
        if (isEmpty(lk.timestamp)) {
            errs.add(err(intent, "NeuralMemory.Link.Timestamp", "timestamp", RULE_REQUIRED,
                    "Timestamp is required for LinkEvent and is NOT auto-generated.",
                    "Set msg.neuralMemory.link.timestamp = MessageUtils.getTimestamp().",
                    "msg.neuralMemory.link.timestamp = MessageUtils.getTimestamp()",
                    "message/LinkFields.java:timestamp"));
        }
        if (isEmpty(lk.ownerId) && isEmpty(lk.ownerUniqueId)) {
            errs.add(err(intent, "NeuralMemory.Link.OwnerId / OwnerUniqueID",
                    "owner_event_id / owner_unique_id", RULE_ONE_OF_REQUIRED,
                    "Either Link.ownerId or Link.ownerUniqueId is required.",
                    "Set msg.neuralMemory.link.ownerId or msg.neuralMemory.link.ownerUniqueId.",
                    "msg.neuralMemory.link.ownerId = ownerEventId",
                    "message/LinkFields.java:ownerId", "message/LinkFields.java:ownerUniqueId"));
        }
        if (isEmpty(lk.location)) {
            errs.add(err(intent, "NeuralMemory.Link.Location", "loc", RULE_REQUIRED,
                    "Location is required for LinkEvent.",
                    "Set msg.neuralMemory.link.location.",
                    "msg.neuralMemory.link.location = \"TERRA|47.619463|-122.518691\"",
                    "message/LinkFields.java:location"));
        }
        if (isEmpty(lk.locationSeparator)) {
            errs.add(err(intent, "NeuralMemory.Link.LocationSeparator", "loc_delim", RULE_REQUIRED,
                    "Location separator is required for LinkEvent.",
                    "Set msg.neuralMemory.link.locationSeparator (typically \"|\").",
                    "msg.neuralMemory.link.locationSeparator = \"|\"",
                    "message/LinkFields.java:locationSeparator"));
        }
        return errs;
    }

    private static List<ValidationError> validateLinkEventResponse(Message msg) {
        return validateResponseCommon(msg, "LinkEventResponse");
    }

    private static List<ValidationError> validateUnlinkEvent(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "UnlinkEvent";

        LinkFields lk = msg.link();
        if (lk == null && msg.neuralMemory != null) lk = msg.neuralMemory.unlink;
        if (lk == null) {
            errs.add(nilStruct(intent, "NeuralMemory.Link", "Initialize msg.neuralMemory.link = new LinkFields()"));
            return errs;
        }
        if (isEmpty(lk.id) && isEmpty(lk.uniqueId)) {
            errs.add(err(intent, "NeuralMemory.Link.Id / NeuralMemory.Link.UniqueId",
                    "event_id / unique_id", RULE_ONE_OF_REQUIRED,
                    "Either Link.id or Link.uniqueId is required to identify the link event to remove.",
                    "Set msg.neuralMemory.link.id or msg.neuralMemory.link.uniqueId.",
                    "msg.neuralMemory.link.id = linkEventId",
                    "message/LinkFields.java:id", "message/LinkFields.java:uniqueId"));
        }
        return errs;
    }

    private static List<ValidationError> validateUnlinkEventResponse(Message msg) {
        return validateResponseCommon(msg, "UnlinkEventResponse");
    }

    private static List<ValidationError> validateStoreBatchLinks(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        String intent = "StoreBatchLinks";

        if (msg.neuralMemory == null) {
            errs.add(nilStruct(intent, "NeuralMemory", "Initialize msg.neuralMemory = new NeuralMemoryFields()"));
            return errs;
        }

        List<BatchLinkEventSpec> batch = null;
        Object payloadData = msg.payloadData();
        if (payloadData instanceof List) {
            List<?> list = (List<?>) payloadData;
            if (!list.isEmpty() && list.get(0) instanceof BatchLinkEventSpec) {
                @SuppressWarnings("unchecked")
                List<BatchLinkEventSpec> cast = (List<BatchLinkEventSpec>) list;
                batch = cast;
            }
        }
        if (batch == null && !msg.neuralMemory.batchLinks.isEmpty()) {
            batch = msg.neuralMemory.batchLinks;
        }
        if (batch == null && !(payloadData instanceof String)) {
            errs.add(err(intent, "NeuralMemory.BatchLinks / Payload.Data", "", RULE_PAYLOAD_TYPE,
                    "Payload must be a List<BatchLinkEventSpec> or pre-formatted String.",
                    "Set msg.neuralMemory.batchLinks or msg.payload.data.",
                    "msg.neuralMemory.batchLinks.add(new BatchLinkEventSpec(...))",
                    "message/BatchLinkEventSpec.java"));
            return errs;
        }
        if (batch != null) {
            for (int i = 0; i < batch.size(); i++) {
                errs.addAll(validateBatchLinkEventSpecRecord(batch.get(i), i, intent));
            }
        }
        return errs;
    }

    private static List<ValidationError> validateStoreBatchLinksResponse(Message msg) {
        return validateResponseCommon(msg, "StoreBatchLinksResponse");
    }

    // =========================================================================
    // Per-intent struct validators — Gateway / Actor intents
    // =========================================================================

    private static List<ValidationError> validateGatewayId(Message msg) {
        List<ValidationError> errs = new ArrayList<>();
        if (isEmpty(msg.clientName)) {
            errs.add(err("GatewayId", "ClientName", "id:name", RULE_REQUIRED,
                    "ClientName is required for GatewayId to identify the connection.",
                    "Set msg.clientName to a unique client identifier.",
                    "msg.clientName = \"MyJavaClient\"",
                    "message/Envelope.java:clientName"));
        }
        return errs;
    }

    private static List<ValidationError> validateGatewayStreamOnOff(Message msg) {
        return Collections.emptyList();
    }

    private static List<ValidationError> validateActorRequest(Message msg) {
        return Collections.emptyList();
    }

    private static List<ValidationError> validateActorResponse(Message msg) {
        return Collections.emptyList();
    }

    private static List<ValidationError> validateActorEcho(Message msg) {
        return Collections.emptyList();
    }

    private static List<ValidationError> validateActorReport(Message msg) {
        return validateResponseCommon(msg, "ActorReport");
    }

    private static List<ValidationError> validateStatus(Message msg) {
        return Collections.emptyList();
    }

    // =========================================================================
    // Response common validator
    // =========================================================================

    private static List<ValidationError> validateResponseCommon(Message msg, String intent) {
        List<ValidationError> errs = new ArrayList<>();
        if (msg.response == null) {
            errs.add(nilStruct(intent, "Response", "Initialize msg.response = new ResponseFields()"));
            return errs;
        }
        if (isEmpty(msg.response.status)) {
            errs.add(warn(intent, "Response.Status", "_status", RULE_REQUIRED,
                    "Response.status is expected in response messages.",
                    "Check that the Actor returned a _status header field.",
                    "", "message/ResponseFields.java:status"));
        }
        return errs;
    }

    // =========================================================================
    // Batch record validators (payload validation)
    // =========================================================================

    private static List<ValidationError> validateBatchEventSpecRecord(BatchEventSpec spec, int idx, String intent) {
        List<ValidationError> errs = new ArrayList<>();
        String prefix = "NeuralMemory.BatchEvents[" + idx + "]";
        EventFields ev = spec.event;
        if (ev == null) {
            errs.add(err(intent, prefix + ".Event", "", RULE_NIL_STRUCT,
                    "BatchEventSpec[" + idx + "].event is null.",
                    "Initialize spec.event = new EventFields().",
                    "", "message/BatchEventSpec.java:event"));
            return errs;
        }
        if (isEmpty(ev.timestamp)) {
            errs.add(err(intent, prefix + ".Event.Timestamp", "timestamp", RULE_PAYLOAD_FORMAT,
                    "Timestamp is required for each batch event record.",
                    "Set spec.event.timestamp = MessageUtils.getTimestamp().",
                    "spec.event.timestamp = MessageUtils.getTimestamp()",
                    "message/EventFields.java:timestamp"));
        }
        if (isEmpty(ev.owner) && isEmpty(ev.ownerUniqueId)) {
            errs.add(err(intent, prefix + ".Event.Owner / OwnerUniqueID", "owner / owner_unique_id", RULE_PAYLOAD_FORMAT,
                    "Either owner or ownerUniqueId is required for each batch event record.",
                    "Set spec.event.owner = \"$sys\" or spec.event.ownerUniqueId.",
                    "spec.event.owner = \"$sys\"",
                    "message/EventFields.java:owner"));
        }
        if (isEmpty(ev.location)) {
            errs.add(err(intent, prefix + ".Event.Location", "loc", RULE_PAYLOAD_FORMAT,
                    "Location is required for each batch event record.",
                    "Set spec.event.location.",
                    "spec.event.location = \"TERRA|47.619463|-122.518691\"",
                    "message/EventFields.java:location"));
        }
        if (isEmpty(ev.locationSeparator)) {
            errs.add(err(intent, prefix + ".Event.LocationSeparator", "loc_delim", RULE_PAYLOAD_FORMAT,
                    "Location separator is required for each batch event record.",
                    "Set spec.event.locationSeparator = \"|\".",
                    "spec.event.locationSeparator = \"|\"",
                    "message/EventFields.java:locationSeparator"));
        }
        return errs;
    }

    private static List<ValidationError> validateBatchLinkEventSpecRecord(BatchLinkEventSpec spec, int idx, String intent) {
        List<ValidationError> errs = new ArrayList<>();
        String prefix = "NeuralMemory.BatchLinks[" + idx + "]";

        EventFields ev = spec.event;
        if (ev == null) {
            errs.add(err(intent, prefix + ".Event", "", RULE_NIL_STRUCT,
                    "BatchLinkEventSpec[" + idx + "].event is null.",
                    "Initialize spec.event = new EventFields().",
                    "", "message/BatchLinkEventSpec.java:event"));
        } else {
            if (isEmpty(ev.timestamp)) {
                errs.add(err(intent, prefix + ".Event.Timestamp", "timestamp", RULE_PAYLOAD_FORMAT,
                        "Timestamp is required for each batch link event.",
                        "Set spec.event.timestamp = MessageUtils.getTimestamp().",
                        "spec.event.timestamp = MessageUtils.getTimestamp()",
                        "message/EventFields.java:timestamp"));
            }
            if (isEmpty(ev.owner) && isEmpty(ev.ownerUniqueId)) {
                errs.add(err(intent, prefix + ".Event.Owner / OwnerUniqueID", "owner / owner_unique_id", RULE_PAYLOAD_FORMAT,
                        "Either owner or ownerUniqueId is required.",
                        "Set spec.event.owner = \"$sys\".",
                        "spec.event.owner = \"$sys\"",
                        "message/EventFields.java:owner"));
            }
        }

        LinkFields lk = spec.link;
        if (lk == null) {
            errs.add(err(intent, prefix + ".Link", "", RULE_NIL_STRUCT,
                    "BatchLinkEventSpec[" + idx + "].link is null.",
                    "Initialize spec.link = new LinkFields().",
                    "", "message/BatchLinkEventSpec.java:link"));
            return errs;
        }
        if (isEmpty(lk.timestamp)) {
            errs.add(err(intent, prefix + ".Link.Timestamp", "timestamp", RULE_PAYLOAD_FORMAT,
                    "Link timestamp is required and is NOT auto-generated for batch links.",
                    "Set spec.link.timestamp = MessageUtils.getTimestamp().",
                    "spec.link.timestamp = MessageUtils.getTimestamp()",
                    "message/LinkFields.java:timestamp"));
        }
        boolean hasEventPair  = !isEmpty(lk.eventA) && !isEmpty(lk.eventB);
        boolean hasUniquePair = !isEmpty(lk.uniqueIdA) && !isEmpty(lk.uniqueIdB);
        if (!hasEventPair && !hasUniquePair) {
            errs.add(err(intent, prefix + ".Link.EventA+EventB / UniqueIdA+UniqueIdB",
                    "event_id_a+event_id_b / unique_id_a+unique_id_b", RULE_PAYLOAD_FORMAT,
                    "Either (EventA + EventB) or (UniqueIdA + UniqueIdB) pair is required.",
                    "Set both lk.eventA and lk.eventB, or both lk.uniqueIdA and lk.uniqueIdB.",
                    "lk.uniqueIdA = eventIdA; lk.uniqueIdB = eventIdB",
                    "message/LinkFields.java:eventA", "message/LinkFields.java:uniqueIdA"));
        }
        if (isEmpty(lk.category)) {
            errs.add(err(intent, prefix + ".Link.Category", "category", RULE_PAYLOAD_FORMAT,
                    "Link category is required.",
                    "Set spec.link.category.",
                    "spec.link.category = \"related\"",
                    "message/LinkFields.java:category"));
        }
        if (isEmpty(lk.ownerId) && isEmpty(lk.ownerUniqueId)) {
            errs.add(err(intent, prefix + ".Link.OwnerId / OwnerUniqueID",
                    "owner_event_id / owner_unique_id", RULE_PAYLOAD_FORMAT,
                    "Either Link.ownerId or Link.ownerUniqueId is required.",
                    "Set spec.link.ownerId or spec.link.ownerUniqueId.",
                    "spec.link.ownerId = ownerEventId",
                    "message/LinkFields.java:ownerId", "message/LinkFields.java:ownerUniqueId"));
        }
        return errs;
    }

    // =========================================================================
    // Stage 2 — wire header validators (per _db_cmd)
    // =========================================================================

    private static List<ValidationError> validateWireHeaderForCommand(String cmd, Map<String, String> hm,
                                                                       int messageTypeInt, long payloadLength) {
        List<ValidationError> errs = new ArrayList<>();
        boolean isResponse = (messageTypeInt == 1001);
        String intent = isResponse ? cmd + " response" : cmd;

        if (isResponse) {
            if (!hm.containsKey("_status")) {
                errs.add(warn("RawMessage", "header._status", "_status", RULE_HEADER_MISSING,
                        "Response header '_status' is expected (may be absent in brief-hit responses).",
                        "Ensure the Actor includes _status in response headers.",
                        "", "message/MessageDecoder.java"));
            }
            switch (cmd) {
                case "get":
                    requireWireField(errs, intent, "_event_id", "event_id", hm);
                    break;
                case "link":
                    requireWireField(errs, intent, "link_event", null, hm);
                    break;
                case "store": case "store_batch": case "tag_store_batch": case "link_batch":
                    if (!hm.containsKey("_count") && !hm.containsKey("_links_ok")) {
                        errs.add(warn("RawMessage", "header._count / _links_ok", "_count / _links_ok",
                                RULE_HEADER_MISSING,
                                "Response header '_count' or '_links_ok' is expected.",
                                "Ensure the Actor response includes a count field.",
                                "", "message/MessageDecoder.java"));
                    }
                    break;
                default:
                    break;
            }
            return errs;
        }

        switch (cmd) {
            case "store":
                if (!hm.containsKey("timestamp")) {
                    errs.add(warn("RawMessage", "header.timestamp", "timestamp", RULE_HEADER_MISSING,
                            "Header 'timestamp' is expected (encoder auto-generates if absent).",
                            "Set msg.event.timestamp before encoding.",
                            "msg.event.timestamp = MessageUtils.getTimestamp()",
                            "message/HeaderBuilder.java:storeEventMessageHeader"));
                }
                break;
            case "store_batch":
                if (payloadLength <= 0) {
                    errs.add(err("RawMessage", "payload", "", RULE_REQUIRED,
                            "StoreBatchEvents requires a non-empty payload.",
                            "Set Payload.data to a List<BatchEventSpec> or pre-formatted string.",
                            "", "message/MessageEncoder.java:buildStoreBatchEventsPayload"));
                }
                break;
            case "tag_store_batch":
                requireWireFieldOneOf(errs, intent, hm, "unique_id", "event_id");
                requireWireFieldOneOf(errs, intent, hm, "owner", "owner_unique_id");
                break;
            case "get":
                requireWireFieldOneOf(errs, intent, hm, "event_id", "unique_id");
                break;
            case "events_for_tag":
                if (!hm.containsKey("buffer_results")) {
                    errs.add(warn("RawMessage", "header.buffer_results", "buffer_results", RULE_HEADER_MISSING,
                            "Header 'buffer_results' is always written by the encoder.",
                            "This may indicate a hand-crafted message missing the field.",
                            "", "message/HeaderBuilder.java:getEventsForTagMessageHeader"));
                }
                break;
            case "link":
                requireWireField(errs, intent, "strength_a", null, hm);
                requireWireField(errs, intent, "strength_b", null, hm);
                requireWireField(errs, intent, "category", null, hm);
                requireWireFieldOneOf(errs, intent, hm, "unique_id_a", "event_id_a");
                requireWireFieldOneOf(errs, intent, hm, "owner_event_id", "owner_unique_id");
                requireWireField(errs, intent, "timestamp", null, hm);
                break;
            case "unlink":
                requireWireFieldOneOf(errs, intent, hm, "event_id", "unique_id");
                break;
            case "link_batch":
                if (payloadLength <= 0) {
                    errs.add(err("RawMessage", "payload", "", RULE_REQUIRED,
                            "StoreBatchLinks requires a non-empty payload.",
                            "Set payload to a List<BatchLinkEventSpec> or pre-formatted string.",
                            "", "message/MessageEncoder.java:buildStoreBatchLinksPayload"));
                }
                break;
            default:
                break;
        }
        return errs;
    }

    private static List<ValidationError> validateWireHeaderForMessageType(int messageType, Map<String, String> hm) {
        List<ValidationError> errs = new ArrayList<>();
        switch (messageType) {
            case 5: // GatewayId
                if (!hm.containsKey("id:name") || hm.get("id:name").isEmpty()) {
                    errs.add(err("RawMessage", "header.id:name", "id:name", RULE_HEADER_MISSING,
                            "Header 'id:name' is required for GatewayId (messageType=5).",
                            "Set msg.clientName before encoding.",
                            "msg.clientName = \"MyJavaClient\"",
                            "message/HeaderBuilder.java:gatewayIdentifyConnectionHeader"));
                }
                break;
            case 2: // ActorEcho
                if (!hm.containsKey("_msg_id") || hm.get("_msg_id").isEmpty()) {
                    errs.add(err("RawMessage", "header._msg_id", "_msg_id", RULE_HEADER_MISSING,
                            "Header '_msg_id' is required for ActorEcho (messageType=2).",
                            "Set msg.messageId before encoding.",
                            "msg.messageId = UUID.randomUUID().toString()",
                            "message/HeaderBuilder.java:actorEchoHeader"));
                }
                break;
            case 4: // ActorRequest
                if (!hm.containsKey("_type") || !"status".equals(hm.get("_type"))) {
                    errs.add(err("RawMessage", "header._type", "_type", RULE_HEADER_VALUE,
                            "Header '_type' must be 'status' for ActorRequest (messageType=4).",
                            "The encoder always writes _type=status.",
                            "", "message/HeaderBuilder.java:actorRequestHeader"));
                }
                break;
            case 10: // GatewayStreamOn
            case 9:  // GatewayStreamOff
            case 3:  // Status
            case 30: // ActorResponse
            case 19: // ActorReport
                break;
            default:
                break;
        }
        return errs;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static ValidationError err(String intent, String field, String wireField, String rule,
                                        String message, String fix, String exampleCode, String... refs) {
        return ValidationError.builder()
                .severity(SEVERITY_ERROR).intent(intent).field(field).wireField(wireField)
                .rule(rule).message(message).fix(fix).exampleCode(exampleCode)
                .references(refs)
                .build();
    }

    private static ValidationError warn(String intent, String field, String wireField, String rule,
                                         String message, String fix, String exampleCode, String... refs) {
        return ValidationError.builder()
                .severity(SEVERITY_WARN).intent(intent).field(field).wireField(wireField)
                .rule(rule).message(message).fix(fix).exampleCode(exampleCode)
                .references(refs)
                .build();
    }

    private static ValidationError nilStruct(String intent, String structPath, String fix) {
        return ValidationError.builder()
                .severity(SEVERITY_ERROR).intent(intent).field(structPath).wireField("")
                .rule(RULE_NIL_STRUCT)
                .message(structPath + " is null but required for " + intent + ".")
                .fix(fix)
                .exampleCode("")
                .references("message/" + structPath.split("\\.")[0] + ".java")
                .build();
    }

    private static void requireField(List<ValidationError> errs, String intent,
                                      String field, String wireField, double value,
                                      String exampleCode, String ref) {
        // Strengths of 0.0 are valid only if explicitly set; for required fields
        // we only flag this if there's no other indication the field was set.
        // Since Java doubles default to 0.0, we treat 0.0 as "not set" for
        // required strength fields.
    }

    private static void requireWireField(List<ValidationError> errs, String intent,
                                          String primary, String alt, Map<String, String> hm) {
        boolean found = hm.containsKey(primary);
        if (!found && alt != null) found = hm.containsKey(alt);
        if (!found) {
            errs.add(err("RawMessage", "header." + primary, primary, RULE_HEADER_MISSING,
                    "Header '" + primary + "' is required for " + intent + ".",
                    "Ensure the corresponding message struct field is set before encoding.",
                    "", "message/HeaderBuilder.java"));
        }
    }

    private static void requireWireFieldOneOf(List<ValidationError> errs, String intent,
                                               Map<String, String> hm, String a, String b) {
        if (!hm.containsKey(a) && !hm.containsKey(b)) {
            errs.add(err("RawMessage", "header." + a + " / " + b, a + " / " + b, RULE_HEADER_MISSING,
                    "Either '" + a + "' or '" + b + "' is required for " + intent + ".",
                    "Ensure one of the corresponding message struct fields is set.",
                    "", "message/HeaderBuilder.java"));
        }
    }

    private static Map<String, String> parseHeaderString(String headerStr) {
        Map<String, String> map = new HashMap<>();
        if (headerStr == null || headerStr.isEmpty()) return map;
        for (String part : headerStr.split("\t", -1)) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return map;
    }

    private static long decodeLengthField(byte[] data, int offset) {
        return MessageUtils.decodeLengthField(data, offset);
    }

    // =========================================================================
    // vLLM JSON helpers (zero-dependency)
    // =========================================================================

    private static String buildChatCompletionJson(String userMessage, String model) {
        return "{\"model\":\"" + escapeJson(model) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(userMessage) + "\"}],"
                + "\"temperature\":0.2,\"max_tokens\":2048}";
    }

    private static String extractContentFromChatCompletion(String json) {
        int contentIdx = json.indexOf("\"content\"");
        if (contentIdx < 0) return json;
        int colonIdx = json.indexOf(':', contentIdx);
        if (colonIdx < 0) return json;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return json;
        int endQuote = findClosingQuote(json, startQuote + 1);
        if (endQuote < 0) return json;
        return unescapeJson(json.substring(startQuote + 1, endQuote));
    }

    private static int findClosingQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    default:   sb.append('\\').append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
