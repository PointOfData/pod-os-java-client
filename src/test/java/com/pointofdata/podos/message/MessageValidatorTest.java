package com.pointofdata.podos.message;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("MessageValidator — Owner/Id semantics")
class MessageValidatorTest {

    @BeforeAll
    static void requireValidationEnabled() {
        assumeTrue(MessageValidator.isEnabled(),
                "Set PODOS_VALIDATE=1 when running tests (configured in pom.xml surefire)");
    }

    private static Message envelopeMessage(Intent intent) {
        Message msg = new Message();
        msg.to = "mem@zeroth.pod-os.com";
        msg.from = "test@zeroth.pod-os.com";
        msg.intent = intent;
        return msg;
    }

    private static boolean hasRule(ValidationErrors errs, String rule) {
        if (errs == null) return false;
        for (ValidationError e : errs) {
            if (rule.equals(e.rule())) return true;
        }
        return false;
    }

    private static boolean hasErrorSeverity(ValidationErrors errs) {
        if (errs == null) return false;
        for (ValidationError e : errs) {
            if (e.isError()) return true;
        }
        return false;
    }

    @Test
    @DisplayName("StoreBatchTags rejects $sys owner")
    void storeBatchTagsRejectsSysOwner() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreBatchTags);
        msg.event = new EventFields();
        msg.event.id = "event-id";
        msg.event.owner = "$sys";
        msg.neuralMemory = new NeuralMemoryFields();
        msg.neuralMemory.tags = Arrays.asList(new Tag(1, "k", "v"));

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("StoreBatchTags accepts valid owner")
    void storeBatchTagsValidOwner() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreBatchTags);
        msg.event = new EventFields();
        msg.event.id = "event-id";
        msg.event.owner = "user-event-id";
        msg.neuralMemory = new NeuralMemoryFields();
        msg.neuralMemory.tags = Arrays.asList(new Tag(1, "k", "v"));

        ValidationErrors errs = MessageValidator.validate(msg);
        assertFalse(hasErrorSeverity(errs));
    }

    @Test
    @DisplayName("StoreData rejects $sys target Id")
    void storeDataRejectsSysTarget() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreData);
        msg.event = new EventFields();
        msg.event.id = "$sys";
        msg.event.ownerUniqueId = "user-001";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("GetEvent warns when owner set")
    void getEventOwnerNotUsed() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.GetEvent);
        msg.event = new EventFields();
        msg.event.id = "2024.01.15...";
        msg.event.owner = "$sys";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("StoreEvent warns when Event.id set at create")
    void storeEventWarnsIdAtCreate() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreEvent);
        msg.event = new EventFields();
        msg.event.owner = "$sys";
        msg.event.id = "should-not-set";
        msg.event.location = "TERRA";
        msg.event.locationSeparator = "|";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("StoreBatchTags accepts UniqueId path")
    void storeBatchTagsValidUniqueIdPath() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreBatchTags);
        msg.event = new EventFields();
        msg.event.uniqueId = "my-uid";
        msg.event.owner = "user-event-id";
        msg.neuralMemory = new NeuralMemoryFields();
        msg.neuralMemory.tags = Arrays.asList(new Tag(1, "category", "value1"));

        ValidationErrors errs = MessageValidator.validate(msg);
        assertFalse(hasErrorSeverity(errs));
    }

    @Test
    @DisplayName("StoreBatchTags rejects $sys target Id")
    void storeBatchTagsRejectsSysTarget() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreBatchTags);
        msg.event = new EventFields();
        msg.event.id = "$sys";
        msg.event.owner = "user-event-id";
        msg.neuralMemory = new NeuralMemoryFields();
        msg.neuralMemory.tags = Arrays.asList(new Tag(1, "k", "v"));

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("StoreData accepts valid UniqueId + OwnerUniqueId")
    void storeDataValidUniqueIdOwner() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreData);
        msg.event = new EventFields();
        msg.event.uniqueId = "target-uid";
        msg.event.ownerUniqueId = "user-001";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertFalse(hasErrorSeverity(errs));
    }

    @Test
    @DisplayName("StoreData missing target Id")
    void storeDataMissingTargetId() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreData);
        msg.event = new EventFields();
        msg.event.ownerUniqueId = "user-001";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_ONE_OF_REQUIRED));
    }

    @Test
    @DisplayName("StoreData missing owner")
    void storeDataMissingOwner() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreData);
        msg.event = new EventFields();
        msg.event.uniqueId = "target-uid";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_ONE_OF_REQUIRED));
    }

    @Test
    @DisplayName("StoreData rejects $sys owner")
    void storeDataRejectsSysOwner() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.StoreData);
        msg.event = new EventFields();
        msg.event.uniqueId = "target-uid";
        msg.event.owner = "$sys";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasRule(errs, ValidationError.RULE_SEMANTIC));
    }

    @Test
    @DisplayName("LinkEvent rejects NaN strength and accepts a negative strength")
    void linkEventStrengthMustBeFiniteAndNonZero() {
        Message msg = envelopeMessage(IntentTypes.INSTANCE.LinkEvent);
        msg.neuralMemory = new NeuralMemoryFields();
        msg.neuralMemory.link = new LinkFields();
        msg.neuralMemory.link.strengthA = Double.NaN;
        msg.neuralMemory.link.strengthB = -1.0;
        msg.neuralMemory.link.category = "related";

        ValidationErrors errs = MessageValidator.validate(msg);
        assertNotNull(errs);
        assertTrue(hasWireField(errs, "strength_a"));
        assertFalse(hasWireField(errs, "strength_b"));
    }

    @Test
    @DisplayName("StoreBatchLinks encoding rejects a zero strength and writes a negative one")
    void batchLinkStrengthEncoding() {
        BatchLinkEventSpec zero = new BatchLinkEventSpec();
        zero.link = new LinkFields();
        zero.link.strengthA = 0.0;
        zero.link.strengthB = 1.0;
        zero.link.category = "related";
        assertThrows(IllegalArgumentException.class,
                () -> MessageEncoder.formatBatchLinkEventsPayload(List.of(zero)));

        BatchLinkEventSpec negative = new BatchLinkEventSpec();
        negative.link = new LinkFields();
        negative.link.strengthA = -1.5;
        negative.link.strengthB = Double.POSITIVE_INFINITY;
        negative.link.category = "related";
        assertThrows(IllegalArgumentException.class,
                () -> MessageEncoder.formatBatchLinkEventsPayload(List.of(negative)));

        negative.link.strengthB = 2.0;
        String payload = MessageEncoder.formatBatchLinkEventsPayload(List.of(negative));
        assertTrue(payload.contains("strength_a=-1.5"));
        assertTrue(payload.contains("strength_b=2.0"));
    }

    @Test
    @DisplayName("StoreBatchLinks payload emits owner_event_id")
    void batchLinkPayloadEmitsOwnerEventId() {
        BatchLinkEventSpec spec = new BatchLinkEventSpec();
        spec.event.uniqueId = "lk1";
        spec.link = new LinkFields();
        spec.link.uniqueIdA = "a";
        spec.link.uniqueIdB = "b";
        spec.link.strengthA = 0.8;
        spec.link.strengthB = 0.2;
        spec.link.category = "related";
        spec.link.ownerId = "$sys";
        spec.link.timestamp = "+1";
        spec.link.location = "TERRA|0|0|0";
        spec.link.locationSeparator = "|";
        String payload = MessageEncoder.formatBatchLinkEventsPayload(List.of(spec));
        assertTrue(payload.contains("unique_id=lk1"));
        assertTrue(payload.contains("owner_event_id=$sys"));
        assertTrue(payload.contains("timestamp=+1"));
        assertTrue(payload.contains("loc=TERRA|0|0|0"));
        assertTrue(payload.contains("loc_delim=|"));
        assertTrue(payload.startsWith("unique_id=lk1\t"));
        assertFalse(payload.contains("\tunique_id="));
    }

    @Test
    @DisplayName("LinkEvent wire header rejects blank and non-finite strengths")
    void linkEventWireStrength() {
        assertTrue(hasWireField(validateLinkHeader("strength_a=NaN"), "strength_a"));
        assertTrue(hasWireField(validateLinkHeader("strength_a="), "strength_a"));
        assertFalse(hasWireField(validateLinkHeader("strength_a=-2.0"), "strength_a"));
    }

    private static ValidationErrors validateLinkHeader(String strengthField) {
        String header = "_db_cmd=link\t" + strengthField
                + "\tstrength_b=-1.0\tcategory=related\tunique_id_a=a\tunique_id_b=b"
                + "\towner_event_id=owner\ttimestamp=+1";
        ValidationErrors errs = MessageValidator.validateRawMessage(rawMessage(header));
        assertNotNull(errs);
        return errs;
    }

    private static byte[] rawMessage(String header) {
        String to = "mem@gateway.example.com";
        String from = "client@gateway.example.com";
        int totalLen = 63 + to.length() + from.length() + header.length();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%9d", totalLen));
        sb.append(String.format("%9d", to.length()));
        sb.append(String.format("%9d", from.length()));
        sb.append(String.format("%9d", header.length()));
        sb.append(String.format("%9d", 1000));
        sb.append(String.format("%9d", 0));
        sb.append(String.format("%9d", 0));
        sb.append(to).append(from).append(header);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean hasWireField(ValidationErrors errs, String wireField) {
        for (ValidationError e : errs) {
            if (wireField.equals(e.wireField())) return true;
        }
        return false;
    }
}
