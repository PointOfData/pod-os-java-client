package com.pointofdata.podos.message;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
