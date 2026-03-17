package com.pointofdata.podos.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IntentTypes} — verifies that every declared intent has the
 * correct name, routingMessageType, neuralMemoryCommand, and messageType, and that
 * all lookup methods resolve intents accurately.
 */
@DisplayName("IntentTypes")
class IntentTypesTest {

    private static final IntentTypes IT = IntentTypes.INSTANCE;

    // =========================================================================
    // 1. Singleton
    // =========================================================================

    @Test
    @DisplayName("INSTANCE is a singleton")
    void singleton() {
        assertSame(IntentTypes.INSTANCE, IT);
    }

    // =========================================================================
    // 2. Evolutionary Neural Memory REQUEST intents (MEM_REQ / MessageType 1000)
    // =========================================================================

    @Nested
    @DisplayName("Evolutionary Neural Memory Request intents (MEM_REQ, messageType=1000)")
    class MemReqIntents {

        @Test @DisplayName("StoreEvent")
        void storeEvent() {
            assertIntent(IT.StoreEvent, "StoreEvent", "MEM_REQ", "store", 1000);
        }

        @Test @DisplayName("StoreBatchEvents")
        void storeBatchEvents() {
            assertIntent(IT.StoreBatchEvents, "StoreBatchEvents", "MEM_REQ", "store_batch", 1000);
        }

        @Test @DisplayName("StoreBatchTags")
        void storeBatchTags() {
            assertIntent(IT.StoreBatchTags, "StoreBatchTags", "MEM_REQ", "tag_store_batch", 1000);
        }

        @Test @DisplayName("GetEvent")
        void getEvent() {
            assertIntent(IT.GetEvent, "GetEvent", "MEM_REQ", "get", 1000);
        }

        @Test @DisplayName("GetEventsForTags")
        void getEventsForTags() {
            assertIntent(IT.GetEventsForTags, "GetEventsForTags", "MEM_REQ", "events_for_tag", 1000);
        }

        @Test @DisplayName("LinkEvent")
        void linkEvent() {
            assertIntent(IT.LinkEvent, "LinkEvent", "MEM_REQ", "link", 1000);
        }

        @Test @DisplayName("UnlinkEvent")
        void unlinkEvent() {
            assertIntent(IT.UnlinkEvent, "UnlinkEvent", "MEM_REQ", "unlink", 1000);
        }

        @Test @DisplayName("StoreBatchLinks")
        void storeBatchLinks() {
            assertIntent(IT.StoreBatchLinks, "StoreBatchLinks", "MEM_REQ", "link_batch", 1000);
        }
    }

    // =========================================================================
    // 3. Evolutionary Neural Memory RESPONSE intents (MEM_REPLY / MessageType 1001)
    // =========================================================================

    @Nested
    @DisplayName("Evolutionary Neural Memory Response intents (MEM_REPLY, messageType=1001)")
    class MemReplyIntents {

        @Test @DisplayName("StoreEventResponse")
        void storeEventResponse() {
            assertIntent(IT.StoreEventResponse, "StoreEventResponse", "MEM_REPLY", "store", 1001);
        }

        @Test @DisplayName("StoreBatchEventsResponse")
        void storeBatchEventsResponse() {
            assertIntent(IT.StoreBatchEventsResponse, "StoreBatchEventsResponse", "MEM_REPLY", "store_batch", 1001);
        }

        @Test @DisplayName("StoreBatchTagsResponse")
        void storeBatchTagsResponse() {
            assertIntent(IT.StoreBatchTagsResponse, "StoreBatchTagsResponse", "MEM_REPLY", "tag_store_batch", 1001);
        }

        @Test @DisplayName("GetEventResponse")
        void getEventResponse() {
            assertIntent(IT.GetEventResponse, "GetEventResponse", "MEM_REPLY", "get", 1001);
        }

        @Test @DisplayName("GetEventsForTagsResponse")
        void getEventsForTagsResponse() {
            assertIntent(IT.GetEventsForTagsResponse, "GetEventsForTagsResponse", "MEM_REPLY", "events_for_tags", 1001);
        }

        @Test @DisplayName("LinkEventResponse")
        void linkEventResponse() {
            assertIntent(IT.LinkEventResponse, "LinkEventResponse", "MEM_REPLY", "link", 1001);
        }

        @Test @DisplayName("UnlinkEventResponse")
        void unlinkEventResponse() {
            assertIntent(IT.UnlinkEventResponse, "UnlinkEventResponse", "MEM_REPLY", "unlink", 1001);
        }

        @Test @DisplayName("StoreBatchLinksResponse")
        void storeBatchLinksResponse() {
            assertIntent(IT.StoreBatchLinksResponse, "StoreBatchLinksResponse", "MEM_REPLY", "link_batch", 1001);
        }
    }

    // =========================================================================
    // 4. Gateway / Actor intents
    // =========================================================================

    @Nested
    @DisplayName("Gateway / Actor intents")
    class GatewayActorIntents {

        @Test @DisplayName("ActorEcho")
        void actorEcho() {
            assertIntent(IT.ActorEcho, "ActorEcho", "ECHO", "", 2);
        }

        @Test @DisplayName("ActorHalt")
        void actorHalt() {
            assertIntent(IT.ActorHalt, "ActorHalt", "HALT", "", 99);
        }

        @Test @DisplayName("ActorStart")
        void actorStart() {
            assertIntent(IT.ActorStart, "ActorStart", "START", "", 1);
        }

        @Test @DisplayName("Status")
        void status() {
            assertIntent(IT.Status, "Status", "STATUS", "", 3);
        }

        @Test @DisplayName("GatewayStatus")
        void gatewayStatus() {
            assertIntent(IT.GatewayStatus, "GatewayStatus", "STATUS", "", 3);
        }

        @Test @DisplayName("StatusRequest")
        void statusRequest() {
            assertIntent(IT.StatusRequest, "StatusRequest", "STATUS_REQ", "", 110);
        }

        @Test @DisplayName("ActorRequest")
        void actorRequest() {
            assertIntent(IT.ActorRequest, "ActorRequest", "REQUEST", "", 4);
        }

        @Test @DisplayName("ActorResponse")
        void actorResponse() {
            assertIntent(IT.ActorResponse, "ActorResponse", "REPLY", "", 30);
        }

        @Test @DisplayName("GatewayId")
        void gatewayId() {
            assertIntent(IT.GatewayId, "GatewayId", "ID", "", 5);
        }

        @Test @DisplayName("GatewayDisconnect")
        void gatewayDisconnect() {
            assertIntent(IT.GatewayDisconnect, "GatewayDisconnect", "DISCONNECT", "", 6);
        }

        @Test @DisplayName("GatewaySendNext")
        void gatewaySendNext() {
            assertIntent(IT.GatewaySendNext, "GatewaySendNext", "NEXT", "", 7);
        }

        @Test @DisplayName("GatewayNoSend")
        void gatewayNoSend() {
            assertIntent(IT.GatewayNoSend, "GatewayNoSend", "NO_SEND", "", 8);
        }

        @Test @DisplayName("GatewayStreamOff")
        void gatewayStreamOff() {
            assertIntent(IT.GatewayStreamOff, "GatewayStreamOff", "STREAM_OFF", "", 9);
        }

        @Test @DisplayName("GatewayStreamOn")
        void gatewayStreamOn() {
            assertIntent(IT.GatewayStreamOn, "GatewayStreamOn", "STREAM_ON", "", 10);
        }

        @Test @DisplayName("ActorRecord")
        void actorRecord() {
            assertIntent(IT.ActorRecord, "ActorRecord", "RECORD", "", 11);
        }

        @Test @DisplayName("GatewayBatchStart")
        void gatewayBatchStart() {
            assertIntent(IT.GatewayBatchStart, "GatewayBatchStart", "BATCH_START", "", 12);
        }

        @Test @DisplayName("GatewayBatchEnd")
        void gatewayBatchEnd() {
            assertIntent(IT.GatewayBatchEnd, "GatewayBatchEnd", "BATCH_END", "", 13);
        }
    }

    // =========================================================================
    // 5. Queue intents
    // =========================================================================

    @Nested
    @DisplayName("Queue intents")
    class QueueIntents {

        @Test @DisplayName("QueueNextRequest")
        void queueNextRequest() {
            assertIntent(IT.QueueNextRequest, "QueueNextRequest", "QUEUE_NEXT", "", 14);
        }

        @Test @DisplayName("QueueAllRequest")
        void queueAllRequest() {
            assertIntent(IT.QueueAllRequest, "QueueAllRequest", "QUEUE_ALL", "", 15);
        }

        @Test @DisplayName("QueueCountRequest")
        void queueCountRequest() {
            assertIntent(IT.QueueCountRequest, "QueueCountRequest", "QUEUE_COUNT", "", 16);
        }

        @Test @DisplayName("QueueEmpty")
        void queueEmpty() {
            assertIntent(IT.QueueEmpty, "QueueEmpty", "QUEUE_EMPTY", "", 17);
        }

        @Test @DisplayName("Keepalive")
        void keepalive() {
            assertIntent(IT.Keepalive, "Keepalive", "KEEPALIVE", "", 18);
        }
    }

    // =========================================================================
    // 6. Report intents
    // =========================================================================

    @Nested
    @DisplayName("Report intents")
    class ReportIntents {

        @Test @DisplayName("ActorReport")
        void actorReport() {
            assertIntent(IT.ActorReport, "ActorReport", "REPORT", "", 19);
        }

        @Test @DisplayName("ReportRequest")
        void reportRequest() {
            assertIntent(IT.ReportRequest, "ReportRequest", "REPORT_REQUEST", "", 20);
        }

        @Test @DisplayName("InformationReport")
        void informationReport() {
            assertIntent(IT.InformationReport, "InformationReport", "INFO_REPORT", "", 21);
        }
    }

    // =========================================================================
    // 7. Auth intents
    // =========================================================================

    @Nested
    @DisplayName("Auth intents")
    class AuthIntents {

        @Test @DisplayName("AuthAddUser")
        void authAddUser() {
            assertIntent(IT.AuthAddUser, "AuthAddUser", "AUTH_ADD_USER", "", 100);
        }

        @Test @DisplayName("AuthUpdateUser")
        void authUpdateUser() {
            assertIntent(IT.AuthUpdateUser, "AuthUpdateUser", "AUTH_UPDATE_USER", "", 101);
        }

        @Test @DisplayName("AuthUserList")
        void authUserList() {
            assertIntent(IT.AuthUserList, "AuthUserList", "AUTH_USER_LIST", "", 102);
        }

        @Test @DisplayName("AuthDisableUser")
        void authDisableUser() {
            assertIntent(IT.AuthDisableUser, "AuthDisableUser", "AUTH_DISABLE_USER", "", 103);
        }
    }

    // =========================================================================
    // 8. Routing / user-defined intents
    // =========================================================================

    @Nested
    @DisplayName("Routing and user-defined intents")
    class RoutingIntents {

        @Test @DisplayName("ActorUser")
        void actorUser() {
            assertIntent(IT.ActorUser, "ActorUser", "USER", "", 65536);
        }

        @Test @DisplayName("RouteAnyMessage — messageType is 0 (routing-only)")
        void routeAnyMessage() {
            assertIntent(IT.RouteAnyMessage, "RouteAnyMessage", "ANY", "", 0);
        }

        @Test @DisplayName("RouteUserOnlyMessage — messageType is 0 (routing-only)")
        void routeUserOnlyMessage() {
            assertIntent(IT.RouteUserOnlyMessage, "RouteUserOnlyMessage", "USERONLY", "", 0);
        }
    }

    // =========================================================================
    // 9. intentFromCommand (MEM_REQ lookup)
    // =========================================================================

    @Nested
    @DisplayName("intentFromCommand — NeuralMemory command → request Intent")
    class IntentFromCommand {

        @ParameterizedTest(name = "command \"{0}\" → {1}")
        @MethodSource("memReqCommands")
        void resolves(String command, String expectedName) {
            Optional<Intent> result = IT.intentFromCommand(command);
            assertTrue(result.isPresent(), "Expected intent for command: " + command);
            assertEquals(expectedName, result.get().name);
            assertEquals(1000, result.get().messageType);
        }

        static Stream<Arguments> memReqCommands() {
            return Stream.of(
                Arguments.of("store",           "StoreEvent"),
                Arguments.of("store_batch",     "StoreBatchEvents"),
                Arguments.of("tag_store_batch", "StoreBatchTags"),
                Arguments.of("get",             "GetEvent"),
                Arguments.of("events_for_tag",  "GetEventsForTags"),
                Arguments.of("link",            "LinkEvent"),
                Arguments.of("unlink",          "UnlinkEvent"),
                Arguments.of("link_batch",      "StoreBatchLinks")
            );
        }

        @Test @DisplayName("null command returns empty")
        void nullReturnsEmpty() {
            assertFalse(IT.intentFromCommand(null).isPresent());
        }

        @Test @DisplayName("empty string returns empty")
        void emptyReturnsEmpty() {
            assertFalse(IT.intentFromCommand("").isPresent());
        }

        @Test @DisplayName("unknown command returns empty")
        void unknownReturnsEmpty() {
            assertFalse(IT.intentFromCommand("nonexistent_command").isPresent());
        }
    }

    // =========================================================================
    // 10. intentFromResponseCommand (MEM_REPLY lookup)
    // =========================================================================

    @Nested
    @DisplayName("intentFromResponseCommand — NeuralMemory command → response Intent")
    class IntentFromResponseCommand {

        @ParameterizedTest(name = "command \"{0}\" → {1}")
        @MethodSource("memReplyCommands")
        void resolves(String command, String expectedName) {
            Optional<Intent> result = IT.intentFromResponseCommand(command);
            assertTrue(result.isPresent(), "Expected response intent for command: " + command);
            assertEquals(expectedName, result.get().name);
            assertEquals(1001, result.get().messageType);
        }

        static Stream<Arguments> memReplyCommands() {
            return Stream.of(
                Arguments.of("store",           "StoreEventResponse"),
                Arguments.of("store_batch",     "StoreBatchEventsResponse"),
                Arguments.of("tag_store_batch", "StoreBatchTagsResponse"),
                Arguments.of("get",             "GetEventResponse"),
                Arguments.of("events_for_tag",  "GetEventsForTagsResponse"),
                Arguments.of("events_for_tags", "GetEventsForTagsResponse"),  // both variants
                Arguments.of("link",            "LinkEventResponse"),
                Arguments.of("unlink",          "UnlinkEventResponse"),
                Arguments.of("link_batch",      "StoreBatchLinksResponse")
            );
        }

        @Test @DisplayName("null command returns empty")
        void nullReturnsEmpty() {
            assertFalse(IT.intentFromResponseCommand(null).isPresent());
        }

        @Test @DisplayName("empty string returns empty")
        void emptyReturnsEmpty() {
            assertFalse(IT.intentFromResponseCommand("").isPresent());
        }

        @Test @DisplayName("unknown command returns empty")
        void unknownReturnsEmpty() {
            assertFalse(IT.intentFromResponseCommand("nonexistent_command").isPresent());
        }
    }

    // =========================================================================
    // 11. intentFromMessageTypeInt (messageType integer lookup)
    // =========================================================================

    @Nested
    @DisplayName("intentFromMessageTypeInt — messageType integer → Intent")
    class IntentFromMessageTypeInt {

        @ParameterizedTest(name = "messageType {0} → {1}")
        @MethodSource("messageTypes")
        void resolves(int messageType, String expectedName) {
            Optional<Intent> result = IT.intentFromMessageTypeInt(messageType);
            assertTrue(result.isPresent(), "Expected intent for messageType: " + messageType);
            assertEquals(expectedName, result.get().name);
        }

        static Stream<Arguments> messageTypes() {
            return Stream.of(
                Arguments.of(1,    "ActorStart"),
                Arguments.of(2,    "ActorEcho"),
                Arguments.of(3,    "Status"),
                Arguments.of(4,    "ActorRequest"),
                Arguments.of(5,    "GatewayId"),
                Arguments.of(6,    "GatewayDisconnect"),
                Arguments.of(7,    "GatewaySendNext"),
                Arguments.of(8,    "GatewayNoSend"),
                Arguments.of(9,    "GatewayStreamOff"),
                Arguments.of(10,   "GatewayStreamOn"),
                Arguments.of(11,   "ActorRecord"),
                Arguments.of(12,   "GatewayBatchStart"),
                Arguments.of(13,   "GatewayBatchEnd"),
                Arguments.of(14,   "QueueNextRequest"),
                Arguments.of(15,   "QueueAllRequest"),
                Arguments.of(16,   "QueueCountRequest"),
                Arguments.of(17,   "QueueEmpty"),
                Arguments.of(18,   "Keepalive"),
                Arguments.of(19,   "ActorReport"),
                Arguments.of(20,   "ReportRequest"),
                Arguments.of(21,   "InformationReport"),
                Arguments.of(30,   "ActorResponse"),
                Arguments.of(99,   "ActorHalt"),
                Arguments.of(100,  "AuthAddUser"),
                Arguments.of(101,  "AuthUpdateUser"),
                Arguments.of(102,  "AuthUserList"),
                Arguments.of(103,  "AuthDisableUser"),
                Arguments.of(110,  "StatusRequest"),
                Arguments.of(1000, "StoreEvent"),
                Arguments.of(1001, "StoreEventResponse"),
                Arguments.of(65536,"ActorUser")
            );
        }

        @Test @DisplayName("messageType 0 (routing-only) returns empty")
        void zeroReturnsEmpty() {
            assertFalse(IT.intentFromMessageTypeInt(0).isPresent());
        }

        @Test @DisplayName("unknown messageType returns empty")
        void unknownReturnsEmpty() {
            assertFalse(IT.intentFromMessageTypeInt(Integer.MAX_VALUE).isPresent());
        }
    }

    // =========================================================================
    // 12. intentFromMessageTypeAndCommand (combined dispatch)
    // =========================================================================

    @Nested
    @DisplayName("intentFromMessageTypeAndCommand — precise NMD dispatch")
    class IntentFromMessageTypeAndCommand {

        @Test @DisplayName("messageType=1000 dispatches to request intent")
        void memReqDispatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1000, "store");
            assertTrue(result.isPresent());
            assertEquals("StoreEvent", result.get().name);
            assertEquals(1000, result.get().messageType);
        }

        @Test @DisplayName("messageType=1001 dispatches to response intent")
        void memReplyDispatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1001, "store");
            assertTrue(result.isPresent());
            assertEquals("StoreEventResponse", result.get().name);
            assertEquals(1001, result.get().messageType);
        }

        @Test @DisplayName("messageType=1000 with store_batch")
        void memReqStoreBatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1000, "store_batch");
            assertTrue(result.isPresent());
            assertEquals("StoreBatchEvents", result.get().name);
        }

        @Test @DisplayName("messageType=1001 with store_batch")
        void memReplyStoreBatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1001, "store_batch");
            assertTrue(result.isPresent());
            assertEquals("StoreBatchEventsResponse", result.get().name);
        }

        @Test @DisplayName("messageType=1000 with link_batch")
        void memReqLinkBatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1000, "link_batch");
            assertTrue(result.isPresent());
            assertEquals("StoreBatchLinks", result.get().name);
        }

        @Test @DisplayName("messageType=1001 with link_batch")
        void memReplyLinkBatch() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(1001, "link_batch");
            assertTrue(result.isPresent());
            assertEquals("StoreBatchLinksResponse", result.get().name);
        }

        @Test @DisplayName("messageType=11 (RECORD) with known response command falls back to response intent")
        void recordWithKnownCommand() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(11, "store");
            assertTrue(result.isPresent());
            assertEquals("StoreEventResponse", result.get().name);
        }

        @Test @DisplayName("messageType=11 (RECORD) with unknown command falls back to messageType lookup")
        void recordWithUnknownCommand() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(11, "unknown");
            assertTrue(result.isPresent());
            assertEquals("ActorRecord", result.get().name);
        }

        @Test @DisplayName("non-NMD messageType ignores command and uses messageType lookup")
        void nonNmdIgnoresCommand() {
            Optional<Intent> result = IT.intentFromMessageTypeAndCommand(5, "store");
            assertTrue(result.isPresent());
            assertEquals("GatewayId", result.get().name);
        }

        @Test @DisplayName("messageType=1000 with unknown command returns empty")
        void memReqUnknownCommandReturnsEmpty() {
            assertFalse(IT.intentFromMessageTypeAndCommand(1000, "no_such_command").isPresent());
        }

        @Test @DisplayName("messageType=1001 with unknown command returns empty")
        void memReplyUnknownCommandReturnsEmpty() {
            assertFalse(IT.intentFromMessageTypeAndCommand(1001, "no_such_command").isPresent());
        }
    }

    // =========================================================================
    // 13. Intent.isEmpty()
    // =========================================================================

    @Nested
    @DisplayName("Intent.isEmpty()")
    class IsEmpty {

        @Test @DisplayName("no declared intent is empty")
        void noDeclaredIntentIsEmpty() {
            Intent[] allIntents = {
                IT.StoreEvent, IT.StoreBatchEvents, IT.StoreBatchTags, IT.GetEvent,
                IT.GetEventsForTags, IT.LinkEvent, IT.UnlinkEvent, IT.StoreBatchLinks,
                IT.StoreEventResponse, IT.StoreBatchEventsResponse, IT.StoreBatchTagsResponse,
                IT.GetEventResponse, IT.GetEventsForTagsResponse, IT.LinkEventResponse,
                IT.UnlinkEventResponse, IT.StoreBatchLinksResponse,
                IT.ActorEcho, IT.ActorHalt, IT.ActorStart, IT.Status, IT.GatewayStatus,
                IT.StatusRequest, IT.ActorRequest, IT.ActorResponse, IT.GatewayId,
                IT.GatewayDisconnect, IT.GatewaySendNext, IT.GatewayNoSend,
                IT.GatewayStreamOff, IT.GatewayStreamOn, IT.ActorRecord,
                IT.GatewayBatchStart, IT.GatewayBatchEnd,
                IT.QueueNextRequest, IT.QueueAllRequest, IT.QueueCountRequest,
                IT.QueueEmpty, IT.Keepalive,
                IT.ActorReport, IT.ReportRequest, IT.InformationReport,
                IT.AuthAddUser, IT.AuthUpdateUser, IT.AuthUserList, IT.AuthDisableUser,
                IT.ActorUser, IT.RouteAnyMessage, IT.RouteUserOnlyMessage
            };
            for (Intent intent : allIntents) {
                assertFalse(intent.isEmpty(),
                        "Expected intent to not be empty: " + intent.name);
            }
        }

        @Test @DisplayName("manually constructed empty intent is empty")
        void emptyIntentIsEmpty() {
            Intent empty = new Intent("", "NONE", 0);
            assertTrue(empty.isEmpty());
        }
    }

    // =========================================================================
    // 14. Intent equality and hashCode
    // =========================================================================

    @Nested
    @DisplayName("Intent.equals() and hashCode()")
    class EqualsAndHashCode {

        @Test @DisplayName("same object is equal to itself")
        void reflexive() {
            assertEquals(IT.StoreEvent, IT.StoreEvent);
        }

        @Test @DisplayName("equivalent Intent instances are equal")
        void equivalentInstances() {
            Intent a = new Intent("StoreEvent", "MEM_REQ", "store", 1000);
            Intent b = new Intent("StoreEvent", "MEM_REQ", "store", 1000);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test @DisplayName("intents with different names are not equal")
        void differentNamesNotEqual() {
            Intent a = new Intent("StoreEvent",       "MEM_REQ", "store", 1000);
            Intent b = new Intent("StoreBatchEvents", "MEM_REQ", "store", 1000);
            assertNotEquals(a, b);
        }

        @Test @DisplayName("intents with different messageTypes are not equal")
        void differentMessageTypesNotEqual() {
            Intent a = new Intent("StoreEvent", "MEM_REQ",   "store", 1000);
            Intent b = new Intent("StoreEvent", "MEM_REPLY", "store", 1001);
            assertNotEquals(a, b);
        }

        @Test @DisplayName("intents with different neuralMemoryCommands are not equal")
        void differentCommandsNotEqual() {
            Intent a = new Intent("StoreEvent", "MEM_REQ", "store",       1000);
            Intent b = new Intent("StoreEvent", "MEM_REQ", "store_batch", 1000);
            assertNotEquals(a, b);
        }

        @Test @DisplayName("intent is not equal to null")
        void notEqualToNull() {
            assertNotEquals(IT.StoreEvent, null);
        }

        @Test @DisplayName("intent is not equal to a different type")
        void notEqualToDifferentType() {
            assertNotEquals(IT.StoreEvent, "StoreEvent");
        }
    }

    // =========================================================================
    // 15. Routing constants
    // =========================================================================

    @Nested
    @DisplayName("Routing test and action constants")
    class RoutingConstants {

        @Test @DisplayName("routing test type constants are correct")
        void routingTestTypes() {
            assertEquals("NONE",   IntentTypes.ROUTING_TEST_NONE);
            assertEquals("EQ",     IntentTypes.ROUTING_TEST_EQ);
            assertEquals("NE",     IntentTypes.ROUTING_TEST_NE);
            assertEquals("LT",     IntentTypes.ROUTING_TEST_LT);
            assertEquals("LE",     IntentTypes.ROUTING_TEST_LE);
            assertEquals("GT",     IntentTypes.ROUTING_TEST_GT);
            assertEquals("GE",     IntentTypes.ROUTING_TEST_GE);
            assertEquals("range",  IntentTypes.ROUTING_TEST_RANGE);
            assertEquals("excl",   IntentTypes.ROUTING_TEST_EXCL);
            assertEquals("regexp", IntentTypes.ROUTING_TEST_REGEXP);
            assertEquals("#EQ",    IntentTypes.ROUTING_TEST_NUM_EQ);
            assertEquals("#NE",    IntentTypes.ROUTING_TEST_NUM_NE);
            assertEquals("#LT",    IntentTypes.ROUTING_TEST_NUM_LT);
            assertEquals("#LE",    IntentTypes.ROUTING_TEST_NUM_LE);
            assertEquals("#GT",    IntentTypes.ROUTING_TEST_NUM_GT);
            assertEquals("#GE",    IntentTypes.ROUTING_TEST_NUM_GE);
            assertEquals("#RANGE", IntentTypes.ROUTING_TEST_NUM_RANGE);
            assertEquals("#EXCL",  IntentTypes.ROUTING_TEST_NUM_EXCL);
        }

        @Test @DisplayName("routing action type constants are correct")
        void routingActionTypes() {
            assertEquals("NONE",      IntentTypes.ROUTING_ACTION_NONE);
            assertEquals("ROUTE",     IntentTypes.ROUTING_ACTION_ROUTE);
            assertEquals("DISCARD",   IntentTypes.ROUTING_ACTION_DISCARD);
            assertEquals("CHANGE",    IntentTypes.ROUTING_ACTION_CHANGE);
            assertEquals("DUPLICATE", IntentTypes.ROUTING_ACTION_DUPLICATE);
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static void assertIntent(Intent intent,
                                     String expectedName,
                                     String expectedRoutingMessageType,
                                     String expectedNeuralMemoryCommand,
                                     int expectedMessageType) {
        assertNotNull(intent, "Intent '" + expectedName + "' must not be null");
        assertEquals(expectedName,               intent.name,               "name");
        assertEquals(expectedRoutingMessageType, intent.routingMessageType, "routingMessageType");
        assertEquals(expectedNeuralMemoryCommand,intent.neuralMemoryCommand,"neuralMemoryCommand");
        assertEquals(expectedMessageType,        intent.messageType,        "messageType");
    }
}
