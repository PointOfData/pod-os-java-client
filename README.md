# Pod-OS Java Client

High-performance Java client library for the **Pod-OS Actor messaging platform**.
A faithful port of the [pod-os-go-client](https://github.com/PointOfData/pod-os-go-client),
targeting Java 17+ with zero external runtime dependencies beyond SLF4J.

---

## Features

- **Wire-protocol faithful** — Implements the Pod-OS flat byte-stream protocol exactly as the Go reference client.
- **High throughput** — Sustains 100K+ messages/second using buffered TCP I/O, `ReentrantLock`-protected sends, and a pipelined concurrent receiver loop.
- **Concurrent mode** — Background receiver thread routes responses to calling threads via `CompletableFuture` and MessageId correlation, enabling many simultaneous in-flight requests.
- **Auto-reconnect** — Configurable exponential-backoff reconnection with optional unlimited retries.
- **Client registry** — Static registry keyed by `clientName:gatewayActorName`, preventing duplicate connections.
- **Full intent coverage** — All Pod-OS intents supported: Gateway, Event Store, Evolutionary Neural Memory (Get, Store, Batch, Link, Tag).
- **SLF4J logging** — Drop-in with any SLF4J backend; zero-overhead `NoOpLogger` by default.
- **Zero external runtime deps** — Only `slf4j-api` is required at runtime. Everything else uses the Java standard library.

---

## Requirements

| | |
|---|---|
| Java | 17 or later |
| Build | Maven 3.6+ |
| Runtime dependency | `slf4j-api` 2.x |

---

## Build

```bash
mvn clean package
```

To skip tests:

```bash
mvn clean package -DskipTests
```

---

## Quick Start

### 1. Add the dependency (once published to Maven Central)

```xml
<dependency>
    <groupId>com.pointofdata</groupId>
    <artifactId>pod-os-java-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create a client

```java
import com.pointofdata.podos.PodOsClient;
import com.pointofdata.podos.config.Config;

Config cfg = new Config();
cfg.host             = "zeroth.pod-os.com";
cfg.port             = "62312";
cfg.gatewayActorName = "zeroth.pod-os.com";
cfg.clientName       = "my-java-app";
cfg.passcode         = "secret";

// Optional: enable concurrent mode for high-throughput parallel sends
cfg.enableConcurrentMode = true;

// Optional: enable INFO-level logging via SLF4J
cfg.logLevel = 3; // 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG

PodOsClient client = PodOsClient.newClient(cfg);
```

### 3. Send a message

```java
import com.pointofdata.podos.message.*;

// Build a StoreEvent message
Message msg = new Message();
msg.to      = "$events@zeroth.pod-os.com";
msg.from    = "my-java-app@zeroth.pod-os.com";
msg.intent  = IntentTypes.INSTANCE.StoreEvent;

msg.event = new EventFields();
msg.event.id        = "evt-001";
msg.event.owner     = "my-java-app";
msg.event.timestamp = MessageUtils.getTimestamp();
msg.event.locationSeparator = "|";

msg.payload = new PayloadFields();
msg.payload.data     = "{ \"sensor\": \"temp\", \"value\": 22.5 }";
msg.payload.mimeType = "application/json";

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
System.out.println("Status: " + response.processingStatus());
```

### 4. Close the client

```java
client.close();
```

Or use try-with-resources:

```java
try (PodOsClient client = PodOsClient.newClient(cfg)) {
    // ... use client
}
```

---

## Configuration Reference

| Field | Type | Default | Description |
|---|---|---|---|
| `host` | `String` | `""` | Gateway hostname or IP |
| `port` | `String` | `"62312"` | Gateway port |
| `network` | `String` | `"tcp"` | Protocol (`"tcp"`, `"udp"`, `"unix"`) |
| `gatewayActorName` | `String` | `""` | Actor name used in message routing (**required**) |
| `clientName` | `String` | `""` | Unique client identifier (**required**) |
| `passcode` | `String` | `""` | Authentication passcode |
| `dialTimeout` | `Duration` | `5s` | TCP connect timeout |
| `sendTimeout` | `Duration` | `5s` | Socket write timeout |
| `receiveTimeout` | `Duration` | `5s` | Socket read timeout |
| `responseTimeout` | `Duration` | `30s` | Overall request-response timeout |
| `enableStreaming` | `Boolean` | `null` (enabled) | Send STREAM_ON after connect |
| `enableConcurrentMode` | `boolean` | `false` | Start background receiver thread |
| `logLevel` | `int` | `0` | 0=off, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG |
| `logger` | `PodOsLogger` | `null` | Custom logger (overrides `logLevel`) |
| `wireHook` | `WireHook` | `null` | Observe raw send/receive bytes |
| `retryConfig` | `RetryConfig` | defaults | Connection attempt retry settings |
| `poolConfig` | `PoolConfig` | defaults | Connection pool settings |
| `reconnectConfig` | `ReconnectConfig` | defaults | Auto-reconnection settings |

### RetryConfig

| Field | Default | Description |
|---|---|---|
| `retries` | `3` | Maximum retry attempts |
| `backoff` | `1s` | Initial backoff duration |
| `backoffMultiplier` | `1.5` | Backoff multiplier per attempt |
| `disableBackoffCaps` | `false` | Disable default caps |

### ReconnectConfig

| Field | Default | Description |
|---|---|---|
| `enabled` | `null` (enabled) | Enable auto-reconnect |
| `maxRetries` | `10` | Max reconnect attempts (0 = unlimited) |
| `initialBackoff` | `1s` | Initial backoff |
| `backoffMultiplier` | `2.0` | Multiplier per attempt |
| `maxBackoff` | `60s` | Maximum backoff cap |

---

## Supported Intents

### Evolutionary Neural Memory Requests (MessageType 1000 — `MEM_REQ`)

| Intent | NeuralMemory Command | Description |
|---|---|---|
| `StoreEvent` | `store` | Persist a single event |
| `StoreData` | `store_data` | Persist raw payload data with a unique identifier, timestamp, and location |
| `StoreBatchEvents` | `store_batch` | Persist events in bulk |
| `StoreBatchTags` | `tag_store_batch` | Persist tags in bulk |
| `GetEvent` | `get` | Retrieve a single event |
| `GetEventsForTags` | `events_for_tag` | Retrieve events matching tags |
| `LinkEvent` | `link` | Create a link between events |
| `UnlinkEvent` | `unlink` | Remove a link between events |
| `StoreBatchLinks` | `link_batch` | Persist links in bulk |

### Evolutionary Neural Memory Responses (MessageType 1001 — `MEM_REPLY`)

| Intent | NeuralMemory Command | Description |
|---|---|---|
| `StoreEventResponse` | `store` | Response to StoreEvent |
| `StoreDataResponse` | `store_data` | Response to StoreData |
| `StoreBatchEventsResponse` | `store_batch` | Response to StoreBatchEvents |
| `StoreBatchTagsResponse` | `tag_store_batch` | Response to StoreBatchTags |
| `GetEventResponse` | `get` | Response to GetEvent |
| `GetEventsForTagsResponse` | `events_for_tags` | Response to GetEventsForTags |
| `LinkEventResponse` | `link` | Response to LinkEvent |
| `UnlinkEventResponse` | `unlink` | Response to UnlinkEvent |
| `StoreBatchLinksResponse` | `link_batch` | Response to StoreBatchLinks |

### Gateway / Actor Intents

| Intent | Routing Type | MessageType | Description |
|---|---|---|---|
| `GatewayId` | `ID` | 5 | Identify client to gateway |
| `GatewayDisconnect` | `DISCONNECT` | 6 | Disconnect from gateway |
| `GatewaySendNext` | `NEXT` | 7 | Request next message |
| `GatewayNoSend` | `NO_SEND` | 8 | Signal no message to send |
| `GatewayStreamOff` | `STREAM_OFF` | 9 | Disable streaming mode |
| `GatewayStreamOn` | `STREAM_ON` | 10 | Enable streaming mode |
| `GatewayBatchStart` | `BATCH_START` | 12 | Begin a batch operation |
| `GatewayBatchEnd` | `BATCH_END` | 13 | End a batch operation |
| `GatewayStatus` | `STATUS` | 3 | Gateway status message |
| `ActorEcho` | `ECHO` | 2 | Echo / ping |
| `ActorHalt` | `HALT` | 99 | Stop an actor |
| `ActorStart` | `START` | 1 | Start an actor |
| `ActorRecord` | `RECORD` | 11 | Record actor data |
| `ActorRequest` | `REQUEST` | 4 | Generic actor request |
| `ActorResponse` | `REPLY` | 30 | Generic actor response |
| `Status` | `STATUS` | 3 | Status message |
| `StatusRequest` | `STATUS_REQ` | 110 | Status request |

### Queue Intents

| Intent | Routing Type | MessageType | Description |
|---|---|---|---|
| `QueueNextRequest` | `QUEUE_NEXT` | 14 | Dequeue the next item |
| `QueueAllRequest` | `QUEUE_ALL` | 15 | Dequeue all items |
| `QueueCountRequest` | `QUEUE_COUNT` | 16 | Count queued items |
| `QueueEmpty` | `QUEUE_EMPTY` | 17 | Signal queue is empty |
| `Keepalive` | `KEEPALIVE` | 18 | Keepalive heartbeat |

### Report Intents

| Intent | Routing Type | MessageType | Description |
|---|---|---|---|
| `ActorReport` | `REPORT` | 19 | Actor report |
| `ReportRequest` | `REPORT_REQUEST` | 20 | Request a report |
| `InformationReport` | `INFO_REPORT` | 21 | Informational report |

### Auth Intents

| Intent | Routing Type | MessageType | Description |
|---|---|---|---|
| `AuthAddUser` | `AUTH_ADD_USER` | 100 | Add a user |
| `AuthUpdateUser` | `AUTH_UPDATE_USER` | 101 | Update a user |
| `AuthUserList` | `AUTH_USER_LIST` | 102 | List users |
| `AuthDisableUser` | `AUTH_DISABLE_USER` | 103 | Disable a user |

### Routing / User-Defined Intents

| Intent | Routing Type | MessageType | Description |
|---|---|---|---|
| `ActorUser` | `USER` | 65536 | User-defined intent (base value) |
| `RouteAnyMessage` | `ANY` | — | Route any message type |
| `RouteUserOnlyMessage` | `USERONLY` | — | Route user-only messages |

### Intent Lookup

```java
// Lookup by NeuralMemory command (MEM_REQ)
Optional<Intent> req  = IntentTypes.INSTANCE.intentFromCommand("store");

// Lookup by NeuralMemory command (MEM_REPLY)
Optional<Intent> resp = IntentTypes.INSTANCE.intentFromResponseCommand("store");

// Lookup by messageType integer
Optional<Intent> byType = IntentTypes.INSTANCE.intentFromMessageTypeInt(5);

// Lookup by messageType + command (most precise for NMD messages)
Optional<Intent> precise = IntentTypes.INSTANCE.intentFromMessageTypeAndCommand(1000, "store");
```

---

## Concurrent Mode — High Throughput

When `enableConcurrentMode = true`, a daemon thread continuously reads from the socket
and routes each response to the matching `CompletableFuture` via its `_msg_id` header.
This enables many in-flight requests simultaneously:

```java
cfg.enableConcurrentMode = true;
cfg.responseTimeout = Duration.ofSeconds(30);
PodOsClient client = PodOsClient.newClient(cfg);

// Send 1000 messages concurrently using a thread pool
ExecutorService pool = Executors.newFixedThreadPool(32);
List<Future<Message>> futures = new ArrayList<>();

for (int i = 0; i < 1000; i++) {
    final int seq = i;
    futures.add(pool.submit(() -> {
        Message msg = buildEventMessage(seq);
        return client.sendMessage(msg, Duration.ofSeconds(10));
    }));
}

for (Future<Message> f : futures) {
    Message response = f.get();
    // process response
}
```

---

## Wire Protocol

The Pod-OS protocol is a flat byte stream with a fixed 7×9-byte length prefix followed by variable-length sections:

```
[totalLength:9][toLength:9][fromLength:9][headerLength:9]
[messageType:9][dataType:9][payloadLength:9]
[TO bytes][FROM bytes][HEADER bytes][PAYLOAD bytes]
```

All 9-byte length fields use hex encoding: `x` + 8 hex digits.
`messageType` and `dataType` use zero-padded 9-digit decimal.
Headers are tab-separated `key=value` pairs.
Timestamps are POSIX microseconds formatted as `+%.6f`.

---

## Package Structure

```
com.pointofdata.podos
├── PodOsClient.java               # Root client (factory, registry, send/receive)
├── config/
│   ├── Config.java                # Main configuration
│   ├── RetryConfig.java           # Connection retry settings
│   ├── PoolConfig.java            # Connection pool settings
│   └── ReconnectConfig.java       # Auto-reconnect settings
├── connection/
│   ├── ConnectionClient.java      # TCP socket I/O (send, receive, reconnect)
│   ├── Retry.java                 # Exponential-backoff retry
│   └── WireHook.java              # Wire observation interface
├── errors/
│   ├── ErrCode.java               # Error code enum
│   └── GatewayDError.java         # Typed runtime exception
├── log/
│   ├── PodOsLogger.java           # Logger interface
│   ├── NoOpLogger.java            # Zero-overhead no-op implementation
│   └── Slf4jLogger.java           # SLF4J-backed implementation
└── message/
    ├── Intent.java                # Message intent descriptor
    ├── IntentTypes.java           # Singleton intent registry
    ├── Message.java               # Core message model
    ├── Envelope.java              # Routing fields (to, from, intent, ...)
    ├── EventFields.java           # Event metadata
    ├── PayloadFields.java         # Payload data + mime type
    ├── NeuralMemoryFields.java    # Evolutionary Neural Memory operations
    ├── ResponseFields.java        # Decoded response data
    ├── LinkFields.java            # Link operation fields
    ├── Tag.java                   # Message tag (typed key-value)
    ├── TagOutput.java             # Parsed tag from response
    ├── BatchEventSpec.java        # Bulk event specification
    ├── BatchLinkEventSpec.java    # Bulk link specification
    ├── BriefHitRecord.java        # Search hit record
    ├── StoreBatchEventRecord.java # Batch store response record
    ├── StoreLinkBatchEventRecord.java # Batch link response record
    ├── GetEventOptions.java       # GetEvent options
    ├── GetEventsForTagsOptions.java # GetEventsForTags options
    ├── DataType.java              # Payload data type enum
    ├── DateTimeObject.java        # Decomposed date/time
    ├── SocketMessage.java         # Encoded wire message
    ├── MessageConstants.java      # Wire protocol constants
    ├── MessageUtils.java          # Timestamp, ASCII, length helpers
    ├── HeaderBuilder.java         # Per-intent header construction
    ├── MessageEncoder.java        # Message → wire bytes
    └── MessageDecoder.java        # Wire bytes → Message
```

---

## Error Handling

All errors surface as `GatewayDError` (a `RuntimeException` subclass) with a typed `ErrCode`:

```java
try {
    Message response = client.sendMessage(msg, Duration.ofSeconds(10));
} catch (IOException e) {
    // transport-level failure (encode, send, receive, decode)
} catch (GatewayDError e) {
    // Pod-OS protocol-level error
    ErrCode code = e.getCode();
    switch (code) {
        case CLIENT_NOT_CONNECTED:     // reconnect and retry
        case CLIENT_SEND_FAILED:       // transient write error
        case CLIENT_RECEIVE_FAILED:    // transient read error / timeout
        case VALIDATION_FAILED:        // message validation error
        default:
            System.err.println("Error: " + e.getMessage());
    }
}
```

---

## Custom Logger

Implement `PodOsLogger` to integrate with your own logging framework:

```java
public class MyLogger implements PodOsLogger {
    @Override public boolean isEnabled(Level level) { return true; }
    @Override public void info(String msg, Object... kv)  { /* ... */ }
    @Override public void debug(String msg, Object... kv) { /* ... */ }
    @Override public void warn(String msg, Object... kv)  { /* ... */ }
    @Override public void error(String msg, Object... kv) { /* ... */ }
    @Override public PodOsLogger with(Object... kv)       { return this; }
}

cfg.logger = new MyLogger();
```

---

## Wire Hook (Observability)

Implement `WireHook` to observe all raw bytes flowing in and out — useful for debugging,
audit logging, or protocol replay:

```java
cfg.wireHook = new WireHook() {
    @Override public void onSend(byte[] data)    { /* log outgoing bytes */ }
    @Override public void onReceive(byte[] data) { /* log incoming bytes */ }
};
```

---

## Relationship to the Go Client

This library is a direct port of
[pod-os-go-client](https://github.com/PointOfData/pod-os-go-client).
All wire-protocol behavior, intent semantics, header construction, payload parsing,
and error codes are identical. Java idioms are used where appropriate
(e.g., `Builder` pattern for `ConnectionClient`, `CompletableFuture` for async routing,
`AutoCloseable` for lifecycle management), but the business logic is preserved exactly.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

## Contributing

Pull requests are welcome. Please ensure all existing tests pass (`mvn test`) and that
new code follows the same conventions as the Go reference implementation.
