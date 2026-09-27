package com.pointofdata.podos.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Extended tag formats: GetEvent tag_format=1 and GetEventsForTags buffer_format=1 with tag owners.
 *
 * <p>Fixtures in {@code src/test/resources/tag_format} are raw Pod-OS response frames captured live
 * from a kind cluster.
 */
@DisplayName("Tag formats — tag_format / buffer_format / tag owners")
class TagFormatTest {

    static final String OWNER_KEY   = "+1790266973.206042\u0001TERRA\u000247.6\u0002-122.5";
    static final String TARGET_KEY  = "+1790266973.420335\u0001TERRA\u000247.6\u0002-122.5";
    static final String OWNED_KEY   = "+1790266973.723095\u0001TERRA\u000247.6\u0002-122.5";
    static final String OWNER_UID   = "tagprobe-owner-dlnoodm3eox6";
    static final String TARGET_UID  = "tagprobe-target-dlnoodm3eox6";
    static final String OWNED_UID   = "tagprobe-owned-dlnoodm3eox6";
    static final String PROBE_VALUE = "dlnoodm3eox6";

    static Message decodeFixture(String name) throws IOException {
        try (InputStream in = TagFormatTest.class.getResourceAsStream("/tag_format/" + name + ".bin")) {
            assertNotNull(in, "fixture " + name + " not found on classpath");
            return MessageDecoder.decodeMessage(in.readAllBytes());
        }
    }

    static List<TagOutput> findTag(List<TagOutput> tags, String key, String value) {
        List<TagOutput> out = new ArrayList<>();
        for (TagOutput tag : tags) {
            if (tag.key.equals(key) && (value.isEmpty() || tag.value.equals(value))) out.add(tag);
        }
        return out;
    }

    static EventFields eventByKey(Message msg, String key) {
        for (EventFields ev : msg.response.eventRecords) {
            if (ev.id.equals(key)) return ev;
        }
        fail("event " + key + " not found in " + msg.response.eventRecords.size() + " records");
        return null;
    }

    static TagOutput tag(int tagNumber, int freq, String timestamp, String owner, String key, String value) {
        TagOutput t = new TagOutput(freq, "", key, value);
        t.tagNumber = tagNumber;
        t.timestamp = timestamp;
        t.owner = owner;
        return t;
    }

    static void assertTagEquals(TagOutput want, TagOutput got) {
        assertNotNull(got);
        assertEquals(want.tagNumber, got.tagNumber, "tagNumber");
        assertEquals(want.frequency, got.frequency, "frequency");
        assertEquals(want.timestamp, got.timestamp, "timestamp");
        assertEquals(want.owner, got.owner, "owner");
        assertEquals(want.key, got.key, "key");
        assertEquals(want.value, got.value, "value");
    }

    // =========================================================================
    // Decoding fixtures
    // =========================================================================

    @Nested
    @DisplayName("GetEvent decode")
    class GetEventDecode {

        @Test
        @DisplayName("tag_format=0: tags ordered by tag number, no timestamp or owner")
        void tagFormat0() throws IOException {
            Message msg = decodeFixture("get_event_tag_format_0");
            List<TagOutput> tags = msg.event.tags;
            assertEquals(36, tags.size());
            for (int i = 0; i < tags.size(); i++) {
                assertEquals(i + 1, tags.get(i).tagNumber, "tags must be ordered by tag number");
                assertEquals("", tags.get(i).timestamp);
                assertEquals("", tags.get(i).owner);
            }
            List<TagOutput> color = findTag(tags, "color", "red");
            assertEquals(1, color.size());
            assertEquals(3, color.get(0).frequency);
            assertEquals(18, color.get(0).tagNumber);
            List<TagOutput> size = findTag(tags, "size", "");
            assertEquals(2, size.size());
            assertEquals("a:b=c", size.get(0).value);
        }

        @ParameterizedTest
        @ValueSource(strings = {"get_event_tag_format_1", "get_event_tag_format_1_output_tag_owner_y"})
        @DisplayName("tag_format=1: timestamps parsed, owners omitted by this Pod-OS build")
        void tagFormat1(String fixture) throws IOException {
            Message msg = decodeFixture(fixture);
            List<TagOutput> tags = msg.event.tags;
            assertEquals(36, tags.size());
            assertEquals(1, msg.response.eventRecords.size());
            assertEquals(36, msg.response.eventRecords.get(0).tags.size());

            List<TagOutput> size = findTag(tags, "size", "a:b=c");
            assertEquals(2, size.size());
            assertEquals(19, size.get(0).tagNumber);
            assertEquals(5, size.get(0).frequency);
            assertEquals("1790266974.325930", size.get(0).timestamp);
            assertEquals("1790266974.325980", size.get(1).timestamp);
            assertEquals(Optional.of(Instant.ofEpochSecond(1790266974L, 325_930_000L)), size.get(0).time());

            List<TagOutput> w = findTag(tags, "$W", "");
            assertEquals(1, w.size());
            assertEquals("TERRA\u000247.6\u0002-122.5", w.get(0).value);
            assertEquals(1, w.get(0).tagNumber);

            for (TagOutput t : tags) assertEquals("", t.owner, "unexpected owner on " + t.key);
        }
    }

    @Nested
    @DisplayName("GetEventsForTags decode")
    class GetEventsForTagsDecode {

        @Test
        @DisplayName("buffer_format=0: duplicate inline tags kept, no timestamps")
        void bufferFormat0() throws IOException {
            Message msg = decodeFixture("events_for_tag_buffer_format_0");
            assertEquals(2, msg.response.eventRecords.size());
            EventFields target = eventByKey(msg, TARGET_KEY);
            assertEquals(21, target.tags.size());
            assertEquals(TARGET_UID, target.uniqueId);
            assertEquals(2, findTag(target.tags, "size", "a:b=c").size());
            for (TagOutput t : target.tags) assertEquals("", t.timestamp);
        }

        @Test
        @DisplayName("buffer_format=1: _event_tag lines attached to their events")
        void bufferFormat1() throws IOException {
            Message msg = decodeFixture("events_for_tag_buffer_format_1");
            assertEquals(2, msg.response.eventRecords.size());
            EventFields target = eventByKey(msg, TARGET_KEY);
            EventFields owned = eventByKey(msg, OWNED_KEY);
            assertEquals(21, target.tags.size());
            assertEquals(18, owned.tags.size());
            assertEquals(TARGET_UID, target.uniqueId);
            assertEquals(OWNED_UID, owned.uniqueId);

            List<TagOutput> color = findTag(target.tags, "color", "red");
            assertEquals(1, color.size());
            assertEquals(3, color.get(0).frequency);
            assertEquals("1790266973.722260", color.get(0).timestamp);
            assertEquals("", color.get(0).owner);

            List<TagOutput> probe = findTag(owned.tags, "probe_sfx", PROBE_VALUE);
            assertEquals(1, probe.size());
            assertEquals(4, probe.get(0).frequency);
            assertEquals("1790266974.024690", probe.get(0).timestamp);
        }

        @Test
        @DisplayName("buffer_format=1 + get_tag_owner: owners repaired and normalized")
        void bufferFormat1OwnerEventKey() throws IOException {
            Message msg = decodeFixture("events_for_tag_buffer_format_1_get_tag_owner");
            EventFields target = eventByKey(msg, TARGET_KEY);
            EventFields owned = eventByKey(msg, OWNED_KEY);
            assertEquals(21, target.tags.size());
            assertEquals(18, owned.tags.size());

            List<TagOutput> color = findTag(target.tags, "color", "red");
            assertEquals(1, color.size());
            assertEquals("", color.get(0).owner, "$sys-owned color tag owner must be empty");
            for (TagOutput s : findTag(target.tags, "size", "a:b=c")) assertEquals(OWNER_KEY, s.owner);

            List<TagOutput> uid = findTag(target.tags, "_unique_id", "");
            assertEquals(1, uid.size());
            assertEquals(TARGET_KEY, uid.get(0).owner);

            List<TagOutput> w = findTag(owned.tags, "$W", "");
            assertEquals(1, w.size());
            assertEquals(OWNER_KEY, w.get(0).owner);
            assertEquals("1790266974.024420", w.get(0).timestamp);

            List<TagOutput> all = new ArrayList<>(target.tags);
            all.addAll(owned.tags);
            for (TagOutput t : all) {
                assertFalse(t.owner.contains("_event_tag"), "owner not separated from next record: " + t.owner);
                assertFalse(t.timestamp.contains("owner"), "timestamp contains owner: " + t.timestamp);
            }
        }

        @Test
        @DisplayName("buffer_format=1 + get_tag_owner_unique_id: applyTagOwnerOutput moves owners")
        void bufferFormat1OwnerUniqueId() throws IOException {
            Message req = new Message();
            req.intent = IntentTypes.INSTANCE.GetEventsForTags;
            req.neuralMemory = new NeuralMemoryFields();
            req.neuralMemory.getEventsForTags = new GetEventsForTagsOptions();
            req.neuralMemory.getEventsForTags.bufferFormat = "1";
            req.neuralMemory.getEventsForTags.tagOwnerOutput = TagOwnerOutput.UNIQUE_ID;

            Message msg = decodeFixture("events_for_tag_buffer_format_1_get_tag_owner_unique_id");
            TagFormats.applyTagOwnerOutput(req, msg);

            EventFields target = eventByKey(msg, TARGET_KEY);
            EventFields owned = eventByKey(msg, OWNED_KEY);
            List<TagOutput> color = findTag(target.tags, "color", "red");
            assertEquals(1, color.size());
            assertEquals("", color.get(0).owner, "NULL owner not normalized");
            assertEquals("", color.get(0).ownerUniqueId, "NULL owner not normalized");
            for (TagOutput s : findTag(target.tags, "size", "a:b=c")) {
                assertEquals(OWNER_UID, s.ownerUniqueId);
                assertEquals("", s.owner);
            }
            List<TagOutput> uid = findTag(owned.tags, "_unique_id", "");
            assertEquals(1, uid.size());
            assertEquals(OWNED_UID, uid.get(0).ownerUniqueId);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nested
    @DisplayName("Helpers")
    class Helpers {

        @Test
        @DisplayName("applyTagOwnerOutput is a no-op for EVENT_KEY")
        void applyNoopForEventKey() {
            Message req = new Message();
            req.intent = IntentTypes.INSTANCE.GetEvent;
            req.neuralMemory = new NeuralMemoryFields();
            req.neuralMemory.getEvent = new GetEventOptions();
            req.neuralMemory.getEvent.tagOwnerOutput = TagOwnerOutput.EVENT_KEY;

            Message resp = new Message();
            resp.event = new EventFields();
            TagOutput t = new TagOutput(1, "", "k", "v");
            t.owner = OWNER_KEY;
            resp.event.tags.add(t);
            TagFormats.applyTagOwnerOutput(req, resp);
            assertEquals(OWNER_KEY, resp.event.tags.get(0).owner);
            assertEquals("", resp.event.tags.get(0).ownerUniqueId);
        }

        @Test
        @DisplayName("applyTagOwnerOutput is idempotent when event.tags and eventRecords share a list")
        void applySharedListIdempotent() {
            Message req = new Message();
            req.intent = IntentTypes.INSTANCE.GetEvent;
            req.neuralMemory = new NeuralMemoryFields();
            req.neuralMemory.getEvent = new GetEventOptions();
            req.neuralMemory.getEvent.tagFormat = 1;
            req.neuralMemory.getEvent.tagOwnerOutput = TagOwnerOutput.UNIQUE_ID;

            Message resp = new Message();
            resp.event = new EventFields();
            TagOutput t = new TagOutput(1, "", "k", "v");
            t.owner = OWNER_UID;
            resp.event.tags.add(t);
            resp.response = new ResponseFields();
            EventFields rec = new EventFields();
            rec.tags = resp.event.tags;
            resp.response.eventRecords.add(rec);

            TagFormats.applyTagOwnerOutput(req, resp);
            assertEquals("", t.owner);
            assertEquals(OWNER_UID, t.ownerUniqueId);
        }

        @Test
        @DisplayName("parseEventTagHeader handles format 0 and format 1 variants")
        void parseEventTagHeaderFormats() {
            assertTagEquals(tag(17, 4, "", "", "k", "v"),
                    TagFormats.parseEventTagHeader("event_tag:000000017:4", "k=v"));
            assertTagEquals(tag(2, 3, "1790266974.325930", "", "k", "a=b"),
                    TagFormats.parseEventTagHeader("event_tag:000000002:3:1790266974.325930", "k=a=b"));
            assertTagEquals(tag(3, 1, "1790266974.000001", OWNER_KEY, "k", "v"),
                    TagFormats.parseEventTagHeader("event_tag:000000003:1:1790266974.000001:" + OWNER_KEY, "k=v"));
            assertTagEquals(tag(4, 1, "1790266974.000001", "urn:a:b", "k", "v"),
                    TagFormats.parseEventTagHeader("event_tag:000000004:1:1790266974.000001:urn:a:b", "k=v"));
            assertTagEquals(tag(5, 1, "1790266974.000001", "", "k", "v"),
                    TagFormats.parseEventTagHeader("event_tag:000000005:1:1790266974.000001:NULL", "k=v"));
            assertTagEquals(tag(6, 1, "1790266974.000001", "", "k", "v"),
                    TagFormats.parseEventTagHeader(
                            "event_tag:000000006:1:1790266974.000001:+0000000000.000000\u0001000000.000000", "k=v"));
            assertNull(TagFormats.parseEventTagHeader("unique_id", "x"), "non-tag header parsed as tag");
        }

        @Test
        @DisplayName("normalizeTagOwnerLines keeps documented form and repairs server form")
        void normalizeTagOwnerLines() {
            String spec = "_event_tag=E\ttag_freq=1\ttag_value=a=b\ttag_timestamp=1.000001\towner=O1\n"
                    + "_event_tag=E\ttag_freq=2\ttag_value=c=d\ttag_timestamp=1.000002\towner=O2\n";
            assertEquals(spec, TagFormats.normalizeTagOwnerLines(spec));

            String server = "_event_id=E\n_event_tag=E\ttag_freq=1\ttag_value=a=b\ttag_timestamp=1.000001\n"
                    + "\towner=O1_event_tag=E\ttag_freq=2\ttag_value=c=d\ttag_timestamp=1.000002\n\towner=O2\n\n";
            String want = "_event_id=E\n_event_tag=E\ttag_freq=1\ttag_value=a=b\ttag_timestamp=1.000001\towner=O1\n"
                    + "_event_tag=E\ttag_freq=2\ttag_value=c=d\ttag_timestamp=1.000002\towner=O2\n\n";
            assertEquals(want, TagFormats.normalizeTagOwnerLines(server));
        }

        @Test
        @DisplayName("parsePosixTimestamp")
        void parsePosixTimestamp() {
            assertEquals(Optional.of(Instant.ofEpochSecond(1790266974L, 325_930_000L)),
                    TagFormats.parsePosixTimestamp("1790266974.325930"));
            assertEquals(Optional.of(Instant.ofEpochSecond(1790266974L, 500_000_000L)),
                    TagFormats.parsePosixTimestamp("+1790266974.5"));
            assertEquals(Optional.of(Instant.ofEpochSecond(1790266974L)),
                    TagFormats.parsePosixTimestamp("1790266974"));
            assertEquals(Optional.empty(), TagFormats.parsePosixTimestamp(""));
            assertEquals(Optional.empty(), TagFormats.parsePosixTimestamp("abc"));
            assertEquals(Optional.empty(), new TagOutput().time());
        }
    }

    // =========================================================================
    // Header encoding
    // =========================================================================

    @Nested
    @DisplayName("Header encoding")
    class HeaderEncoding {

        private String getEventHeader(GetEventOptions opts) {
            Message msg = new Message();
            msg.event = new EventFields();
            msg.event.id = "e1";
            msg.neuralMemory = new NeuralMemoryFields();
            msg.neuralMemory.getEvent = opts;
            return HeaderBuilder.getEventMessageHeader(msg);
        }

        private String eventsForTagsHeader(GetEventsForTagsOptions opts) {
            Message msg = new Message();
            msg.neuralMemory = new NeuralMemoryFields();
            msg.neuralMemory.getEventsForTags = opts;
            return HeaderBuilder.getEventsForTagMessageHeader(msg);
        }

        @Test
        @DisplayName("GetEvent honors tagFormat and emits output_tag_owner")
        void getEventTagFormat() {
            GetEventOptions o = new GetEventOptions();
            o.getTags = true;
            String h = getEventHeader(o);
            assertTrue(h.contains("tag_format=0\t"), h);
            assertFalse(h.contains("output_tag_owner"), h);

            o.tagFormat = 1;
            assertTrue(getEventHeader(o).contains("tag_format=1\t"));

            o.tagOwnerOutput = TagOwnerOutput.EVENT_KEY;
            assertTrue(getEventHeader(o).contains("output_tag_owner=Y\t"));

            o.tagOwnerOutput = TagOwnerOutput.UNIQUE_ID;
            assertTrue(getEventHeader(o).contains("output_tag_owner=N\t"));
        }

        @Test
        @DisplayName("GetEventsForTags emits get_tag_owner flags")
        void eventsForTagsTagOwner() {
            GetEventsForTagsOptions o = new GetEventsForTagsOptions();
            o.bufferFormat = "1";
            String h = eventsForTagsHeader(o);
            assertTrue(h.contains("buffer_format=1\t"), h);
            assertFalse(h.contains("get_tag_owner"), h);

            o.tagOwnerOutput = TagOwnerOutput.EVENT_KEY;
            assertTrue(eventsForTagsHeader(o).contains("get_tag_owner=Y\t"));

            o.tagOwnerOutput = TagOwnerOutput.UNIQUE_ID;
            assertTrue(eventsForTagsHeader(o).contains("get_tag_owner_unique_id=Y\t"));
        }

        @Test
        @DisplayName("GetEventsForTags buffer_format defaults to 0")
        void eventsForTagsBufferFormatDefault() {
            assertTrue(eventsForTagsHeader(new GetEventsForTagsOptions()).contains("buffer_format=0\t"));
            assertTrue(eventsForTagsHeader(null).contains("buffer_format=0\t"));
        }

        @Test
        @DisplayName("GetEventsForTags encodes link_tag_pattern and get_eo_count")
        void eventsForTagsLinkTagPatternAndEoCount() {
            GetEventsForTagsOptions o = new GetEventsForTagsOptions();
            o.linkTagFilter = "role=.*";
            o.getEventObjectCount = true;
            String h = eventsForTagsHeader(o);
            assertTrue(h.contains("link_tag_pattern=role=.*\t"), h);
            assertFalse(h.contains("link_tag_filter"), h);
            assertTrue(h.contains("get_eo_count=Y\t"), h);
            assertFalse(eventsForTagsHeader(new GetEventsForTagsOptions()).contains("get_eo_count"));
        }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class Validation {

        private Message getEvent(GetEventOptions opts) {
            Message msg = new Message();
            msg.to = "test@zeroth.pod-os.com";
            msg.from = "c@zeroth.pod-os.com";
            msg.intent = IntentTypes.INSTANCE.GetEvent;
            msg.event = new EventFields();
            msg.event.id = "e1";
            msg.neuralMemory = new NeuralMemoryFields();
            msg.neuralMemory.getEvent = opts;
            return msg;
        }

        private Message eventsForTags(GetEventsForTagsOptions opts) {
            Message msg = new Message();
            msg.to = "test@zeroth.pod-os.com";
            msg.from = "c@zeroth.pod-os.com";
            msg.intent = IntentTypes.INSTANCE.GetEventsForTags;
            msg.payload = new PayloadFields("clause_type:S\tboolean:or\tlow:k=v", "");
            msg.neuralMemory = new NeuralMemoryFields();
            msg.neuralMemory.getEventsForTags = opts;
            return msg;
        }

        private GetEventOptions geOpts(boolean getTags, Integer tagFormat, TagOwnerOutput owner) {
            GetEventOptions o = new GetEventOptions();
            o.getTags = getTags;
            o.tagFormat = tagFormat;
            o.tagOwnerOutput = owner;
            return o;
        }

        private GetEventsForTagsOptions eftOpts(String bufferFormat, TagOwnerOutput owner) {
            GetEventsForTagsOptions o = new GetEventsForTagsOptions();
            o.bufferResults = true;
            o.bufferFormat = bufferFormat;
            o.tagOwnerOutput = owner;
            return o;
        }

        private void assertValid(Message msg) {
            assumeTrue(MessageValidator.isEnabled(), "Set PODOS_VALIDATE=1 when running tests");
            ValidationErrors errs = MessageValidator.validate(msg);
            assertTrue(errs == null || errs.isEmpty(), () -> "unexpected validation errors: " + errs.list());
            SocketMessage socket = MessageEncoder.encodeMessage(msg, "conv");
            ValidationErrors raw = MessageValidator.validateRawMessage(socket.messageBytes);
            assertTrue(raw == null || raw.isEmpty(), () -> "unexpected wire validation errors: " + raw.list());
        }

        private void assertIssue(Message msg, String field, String severity, String rule) {
            assumeTrue(MessageValidator.isEnabled(), "Set PODOS_VALIDATE=1 when running tests");
            ValidationErrors errs = MessageValidator.validate(msg);
            assertNotNull(errs);
            for (ValidationError e : errs) {
                if (field.equals(e.field()) && severity.equals(e.severity()) && rule.equals(e.rule())) {
                    assertFalse(e.fix().isEmpty(), "error lacks fix");
                    assertFalse(e.exampleCode().isEmpty(), "error lacks example code");
                    return;
                }
            }
            fail("want " + severity + "/" + rule + " on " + field + ", got: " + errs.list());
        }

        @Test
        @DisplayName("valid tag_format=1 with owner")
        void validTagFormat1WithOwner() {
            assertValid(getEvent(geOpts(true, 1, TagOwnerOutput.EVENT_KEY)));
        }

        @Test
        @DisplayName("tag_format=2 is rejected")
        void tagFormat2() {
            assertIssue(getEvent(geOpts(true, 2, TagOwnerOutput.NONE)),
                    "NeuralMemory.GetEvent.TagFormat", "error", ValidationError.RULE_FORMAT);
        }

        @Test
        @DisplayName("owner without tag_format=1 is an error")
        void ownerWithoutTagFormat1() {
            assertIssue(getEvent(geOpts(true, null, TagOwnerOutput.UNIQUE_ID)),
                    "NeuralMemory.GetEvent.TagOwnerOutput", "error", ValidationError.RULE_SEMANTIC);
        }

        @Test
        @DisplayName("tag_format=1 without getTags warns")
        void tagFormat1WithoutGetTags() {
            assertIssue(getEvent(geOpts(false, 1, TagOwnerOutput.NONE)),
                    "NeuralMemory.GetEvent.TagFormat", "warn", ValidationError.RULE_SEMANTIC);
        }

        @Test
        @DisplayName("valid buffer_format=1 with owner")
        void validBufferFormat1WithOwner() {
            assertValid(eventsForTags(eftOpts("1", TagOwnerOutput.UNIQUE_ID)));
        }

        @Test
        @DisplayName("buffer_format=2 is rejected")
        void bufferFormat2() {
            assertIssue(eventsForTags(eftOpts("2", TagOwnerOutput.NONE)),
                    "NeuralMemory.GetEventsForTags.BufferFormat", "error", ValidationError.RULE_FORMAT);
        }

        @Test
        @DisplayName("owner without buffer_format=1 warns")
        void ownerWithoutBufferFormat1() {
            assertIssue(eventsForTags(eftOpts("", TagOwnerOutput.EVENT_KEY)),
                    "NeuralMemory.GetEventsForTags.TagOwnerOutput", "warn", ValidationError.RULE_SEMANTIC);
        }

        @Test
        @DisplayName("raw get_tag_owner=yes is a header_value error")
        void rawGetTagOwnerInvalid() {
            assumeTrue(MessageValidator.isEnabled(), "Set PODOS_VALIDATE=1 when running tests");
            assertTrue(hasWireIssue(rewriteHeader(eventsForTags(eftOpts("1", TagOwnerOutput.NONE)),
                    "buffer_format=1\t", "buffer_format=1\tget_tag_owner=yes\t"),
                    "get_tag_owner", ValidationError.RULE_HEADER_VALUE));
        }

        @Test
        @DisplayName("raw tag_format / output_tag_owner checks")
        void rawGetEventChecks() {
            assumeTrue(MessageValidator.isEnabled(), "Set PODOS_VALIDATE=1 when running tests");
            Message msg = getEvent(geOpts(true, 1, TagOwnerOutput.NONE));
            assertTrue(hasWireIssue(rewriteHeader(msg, "tag_format=1\t", "tag_format=7\t"),
                    "tag_format", ValidationError.RULE_HEADER_VALUE));
            assertTrue(hasWireIssue(rewriteHeader(msg, "tag_format=1\t", "tag_format=1\toutput_tag_owner=x\t"),
                    "output_tag_owner", ValidationError.RULE_HEADER_VALUE));
            assertTrue(hasWireIssue(rewriteHeader(msg, "tag_format=1\t", "tag_format=0\toutput_tag_owner=Y\t"),
                    "output_tag_owner", ValidationError.RULE_SEMANTIC));
        }

        @Test
        @DisplayName("raw get_tag_owner with buffer_format=0 warns")
        void rawOwnerWithoutBufferFormat1() {
            assumeTrue(MessageValidator.isEnabled(), "Set PODOS_VALIDATE=1 when running tests");
            assertTrue(hasWireIssue(rewriteHeader(eventsForTags(eftOpts("0", TagOwnerOutput.NONE)),
                    "buffer_format=0\t", "buffer_format=0\tget_tag_owner_unique_id=Y\t"),
                    "get_tag_owner / get_tag_owner_unique_id", ValidationError.RULE_SEMANTIC));
        }

        private boolean hasWireIssue(byte[] raw, String wireField, String rule) {
            ValidationErrors errs = MessageValidator.validateRawMessage(raw);
            if (errs == null) return false;
            for (ValidationError e : errs) {
                if (wireField.equals(e.wireField()) && rule.equals(e.rule())) return true;
            }
            return false;
        }

        /** Replaces {@code from} with {@code to} in the encoded header and fixes the length prefixes. */
        private byte[] rewriteHeader(Message msg, String from, String to) {
            String raw = new String(MessageEncoder.encodeMessage(msg, "conv").messageBytes, StandardCharsets.ISO_8859_1);
            assertTrue(raw.contains(from), "encoded message lacks " + from);
            int delta = to.length() - from.length();
            raw = raw.replaceFirst(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to));
            long total = Long.parseLong(raw.substring(1, 9), 16) + delta;
            long header = Long.parseLong(raw.substring(28, 36), 16) + delta;
            raw = String.format("x%08x", total) + raw.substring(9, 27) + String.format("x%08x", header) + raw.substring(36);
            return raw.getBytes(StandardCharsets.ISO_8859_1);
        }
    }
}
