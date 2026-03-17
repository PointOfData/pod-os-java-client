package com.pointofdata.podos.message;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Singleton holding all Pod-OS intent type definitions.
 * Mirrors the Go {@code IntentType} global variable.
 *
 * <p>Usage: {@code IntentTypes.INSTANCE.StoreEvent}
 */
public final class IntentTypes {

    /** Singleton instance. Equivalent to Go's {@code IntentType} package-level variable. */
    public static final IntentTypes INSTANCE = new IntentTypes();

    // Evolutionary Neural Memory Request intents (MessageType 1000 = MEM_REQ)
    public final Intent StoreEvent        = new Intent("StoreEvent",        "MEM_REQ", "store",           1000);
    public final Intent StoreData         = new Intent("StoreData",         "MEM_REQ", "store_data",      1000);
    public final Intent StoreBatchEvents  = new Intent("StoreBatchEvents",  "MEM_REQ", "store_batch",     1000);
    public final Intent StoreBatchTags    = new Intent("StoreBatchTags",    "MEM_REQ", "tag_store_batch", 1000);
    public final Intent GetEvent          = new Intent("GetEvent",          "MEM_REQ", "get",             1000);
    public final Intent GetEventsForTags  = new Intent("GetEventsForTags",  "MEM_REQ", "events_for_tag",  1000);
    public final Intent LinkEvent         = new Intent("LinkEvent",         "MEM_REQ", "link",            1000);
    public final Intent UnlinkEvent       = new Intent("UnlinkEvent",       "MEM_REQ", "unlink",          1000);
    public final Intent StoreBatchLinks   = new Intent("StoreBatchLinks",   "MEM_REQ", "link_batch",      1000);

    // Evolutionary Neural Memory Response intents (MessageType 1001 = MEM_REPLY)
    public final Intent StoreEventResponse        = new Intent("StoreEventResponse",        "MEM_REPLY", "store",           1001);
    public final Intent StoreDataResponse         = new Intent("StoreDataResponse",         "MEM_REPLY", "store_data",      1001);
    public final Intent StoreBatchEventsResponse  = new Intent("StoreBatchEventsResponse",  "MEM_REPLY", "store_batch",     1001);
    public final Intent StoreBatchTagsResponse    = new Intent("StoreBatchTagsResponse",    "MEM_REPLY", "tag_store_batch", 1001);
    public final Intent GetEventResponse          = new Intent("GetEventResponse",          "MEM_REPLY", "get",             1001);
    public final Intent GetEventsForTagsResponse  = new Intent("GetEventsForTagsResponse",  "MEM_REPLY", "events_for_tags", 1001);
    public final Intent LinkEventResponse         = new Intent("LinkEventResponse",         "MEM_REPLY", "link",            1001);
    public final Intent UnlinkEventResponse       = new Intent("UnlinkEventResponse",       "MEM_REPLY", "unlink",          1001);
    public final Intent StoreBatchLinksResponse   = new Intent("StoreBatchLinksResponse",   "MEM_REPLY", "link_batch",      1001);

    // Gateway / Actor intents
    public final Intent ActorEcho           = new Intent("ActorEcho",           "ECHO",           2);
    public final Intent ActorHalt           = new Intent("ActorHalt",           "HALT",           99);
    public final Intent ActorStart          = new Intent("ActorStart",          "START",          1);
    public final Intent Status              = new Intent("Status",              "STATUS",         3);
    public final Intent GatewayStatus       = new Intent("GatewayStatus",       "STATUS",         3);
    public final Intent StatusRequest       = new Intent("StatusRequest",       "STATUS_REQ",     110);
    public final Intent ActorRequest        = new Intent("ActorRequest",        "REQUEST",        4);
    public final Intent ActorResponse       = new Intent("ActorResponse",       "REPLY",          30);
    public final Intent GatewayId          = new Intent("GatewayId",           "ID",             5);
    public final Intent GatewayDisconnect  = new Intent("GatewayDisconnect",   "DISCONNECT",     6);
    public final Intent GatewaySendNext    = new Intent("GatewaySendNext",     "NEXT",           7);
    public final Intent GatewayNoSend      = new Intent("GatewayNoSend",       "NO_SEND",        8);
    public final Intent GatewayStreamOff   = new Intent("GatewayStreamOff",    "STREAM_OFF",     9);
    public final Intent GatewayStreamOn    = new Intent("GatewayStreamOn",     "STREAM_ON",      10);
    public final Intent ActorRecord        = new Intent("ActorRecord",         "RECORD",         11);
    public final Intent GatewayBatchStart  = new Intent("GatewayBatchStart",   "BATCH_START",    12);
    public final Intent GatewayBatchEnd    = new Intent("GatewayBatchEnd",     "BATCH_END",      13);

    // Queue intents
    public final Intent QueueNextRequest   = new Intent("QueueNextRequest",    "QUEUE_NEXT",     14);
    public final Intent QueueAllRequest    = new Intent("QueueAllRequest",     "QUEUE_ALL",      15);
    public final Intent QueueCountRequest  = new Intent("QueueCountRequest",   "QUEUE_COUNT",    16);
    public final Intent QueueEmpty         = new Intent("QueueEmpty",          "QUEUE_EMPTY",    17);
    public final Intent Keepalive          = new Intent("Keepalive",           "KEEPALIVE",      18);

    // Report intents
    public final Intent ActorReport        = new Intent("ActorReport",         "REPORT",         19);
    public final Intent ReportRequest      = new Intent("ReportRequest",       "REPORT_REQUEST", 20);
    public final Intent InformationReport  = new Intent("InformationReport",   "INFO_REPORT",    21);

    // Auth intents
    public final Intent AuthAddUser        = new Intent("AuthAddUser",         "AUTH_ADD_USER",    100);
    public final Intent AuthUpdateUser     = new Intent("AuthUpdateUser",      "AUTH_UPDATE_USER", 101);
    public final Intent AuthUserList       = new Intent("AuthUserList",        "AUTH_USER_LIST",   102);
    public final Intent AuthDisableUser    = new Intent("AuthDisableUser",     "AUTH_DISABLE_USER",103);

    // User-defined intents (≥ 65536)
    public final Intent ActorUser          = new Intent("ActorUser",           "USER",           65536);

    // Routing-only (no MessageType)
    public final Intent RouteAnyMessage      = new Intent("RouteAnyMessage",     "ANY",            0);
    public final Intent RouteUserOnlyMessage = new Intent("RouteUserOnlyMessage","USERONLY",       0);

    // -------------------------------------------------------------------------
    // Command-to-intent lookup maps (equivalent to Go's commandToIntent maps)
    // -------------------------------------------------------------------------

    /** Maps NeuralMemoryCommand string → request Intent (MEM_REQ). */
    private final Map<String, Intent> commandToIntent = new HashMap<>(16);

    /** Maps NeuralMemoryCommand string → response Intent (MEM_REPLY). */
    private final Map<String, Intent> commandToResponseIntent = new HashMap<>(16);

    /** Maps MessageType integer → first matching Intent (for non-NMD messages). */
    private final Map<Integer, Intent> messageTypeToIntent = new HashMap<>(64);

    private IntentTypes() {
        commandToIntent.put("store",           StoreEvent);
        commandToIntent.put("store_data",      StoreData);
        commandToIntent.put("store_batch",     StoreBatchEvents);
        commandToIntent.put("tag_store_batch", StoreBatchTags);
        commandToIntent.put("get",             GetEvent);
        commandToIntent.put("events_for_tag",  GetEventsForTags);
        commandToIntent.put("link",            LinkEvent);
        commandToIntent.put("unlink",          UnlinkEvent);
        commandToIntent.put("link_batch",      StoreBatchLinks);

        commandToResponseIntent.put("store",           StoreEventResponse);
        commandToResponseIntent.put("store_data",      StoreDataResponse);
        commandToResponseIntent.put("store_batch",     StoreBatchEventsResponse);
        commandToResponseIntent.put("tag_store_batch", StoreBatchTagsResponse);
        commandToResponseIntent.put("get",             GetEventResponse);
        commandToResponseIntent.put("events_for_tag",  GetEventsForTagsResponse);
        commandToResponseIntent.put("events_for_tags", GetEventsForTagsResponse); // handle both variants
        commandToResponseIntent.put("link",            LinkEventResponse);
        commandToResponseIntent.put("unlink",          UnlinkEventResponse);
        commandToResponseIntent.put("link_batch",      StoreBatchLinksResponse);

        // Register all intents by messageType (first registration wins for duplicates)
        Intent[] all = {
            StoreEvent, StoreData, StoreBatchEvents, StoreBatchTags, GetEvent, GetEventsForTags,
            LinkEvent, UnlinkEvent, StoreBatchLinks,
            StoreEventResponse, StoreDataResponse, StoreBatchEventsResponse, StoreBatchTagsResponse,
            GetEventResponse, GetEventsForTagsResponse, LinkEventResponse,
            UnlinkEventResponse, StoreBatchLinksResponse,
            ActorEcho, ActorHalt, ActorStart, Status, GatewayStatus, StatusRequest,
            ActorRequest, ActorResponse, GatewayId, GatewayDisconnect,
            GatewaySendNext, GatewayNoSend, GatewayStreamOff, GatewayStreamOn,
            ActorRecord, GatewayBatchStart, GatewayBatchEnd,
            QueueNextRequest, QueueAllRequest, QueueCountRequest, QueueEmpty, Keepalive,
            ActorReport, ReportRequest, InformationReport,
            AuthAddUser, AuthUpdateUser, AuthUserList, AuthDisableUser, ActorUser
        };
        for (Intent intent : all) {
            if (intent.messageType > 0) {
                messageTypeToIntent.putIfAbsent(intent.messageType, intent);
            }
        }
    }

    /**
     * Returns the request Intent for the given NeuralMemoryCommand string (MEM_REQ).
     */
    public Optional<Intent> intentFromCommand(String command) {
        if (command == null || command.isEmpty()) return Optional.empty();
        return Optional.ofNullable(commandToIntent.get(command));
    }

    /**
     * Returns the response Intent for the given NeuralMemoryCommand string (MEM_REPLY).
     */
    public Optional<Intent> intentFromResponseCommand(String command) {
        if (command == null || command.isEmpty()) return Optional.empty();
        return Optional.ofNullable(commandToResponseIntent.get(command));
    }

    /**
     * Returns the correct Intent based on messageType and command.
     * For MEM_REQ (1000) → request intents; for MEM_REPLY (1001) → response intents.
     * For RECORD (11) → try response first, then fall back to messageType lookup.
     * For all others → messageType lookup.
     */
    public Optional<Intent> intentFromMessageTypeAndCommand(int messageType, String command) {
        switch (messageType) {
            case 1000:
                return intentFromCommand(command);
            case 1001:
                return intentFromResponseCommand(command);
            case 11: {
                Optional<Intent> r = intentFromResponseCommand(command);
                return r.isPresent() ? r : intentFromMessageTypeInt(messageType);
            }
            default:
                return intentFromMessageTypeInt(messageType);
        }
    }

    /**
     * Returns the Intent corresponding to a MessageType integer.
     * For 1000/1001, returns the first matching intent.
     * Use {@link #intentFromMessageTypeAndCommand} for precise NMD intent resolution.
     */
    public Optional<Intent> intentFromMessageTypeInt(int messageType) {
        return Optional.ofNullable(messageTypeToIntent.get(messageType));
    }

    // -------------------------------------------------------------------------
    // Routing test type constants
    // -------------------------------------------------------------------------
    public static final String ROUTING_TEST_NONE         = "NONE";
    public static final String ROUTING_TEST_EQ           = "EQ";
    public static final String ROUTING_TEST_NE           = "NE";
    public static final String ROUTING_TEST_LT           = "LT";
    public static final String ROUTING_TEST_LE           = "LE";
    public static final String ROUTING_TEST_GT           = "GT";
    public static final String ROUTING_TEST_GE           = "GE";
    public static final String ROUTING_TEST_RANGE        = "range";
    public static final String ROUTING_TEST_EXCL         = "excl";
    public static final String ROUTING_TEST_REGEXP       = "regexp";
    public static final String ROUTING_TEST_NUM_EQ       = "#EQ";
    public static final String ROUTING_TEST_NUM_NE       = "#NE";
    public static final String ROUTING_TEST_NUM_LT       = "#LT";
    public static final String ROUTING_TEST_NUM_LE       = "#LE";
    public static final String ROUTING_TEST_NUM_GT       = "#GT";
    public static final String ROUTING_TEST_NUM_GE       = "#GE";
    public static final String ROUTING_TEST_NUM_RANGE    = "#RANGE";
    public static final String ROUTING_TEST_NUM_EXCL     = "#EXCL";

    // -------------------------------------------------------------------------
    // Routing action type constants
    // -------------------------------------------------------------------------
    public static final String ROUTING_ACTION_NONE      = "NONE";
    public static final String ROUTING_ACTION_ROUTE     = "ROUTE";
    public static final String ROUTING_ACTION_DISCARD   = "DISCARD";
    public static final String ROUTING_ACTION_CHANGE    = "CHANGE";
    public static final String ROUTING_ACTION_DUPLICATE = "DUPLICATE";
}
