## Pod-OS Message Handling

The Java client communicates with Gateway Actors and Actors via `PodOsClient.sendMessage()`. `sendMessage()` takes a `Message`, serializes it into a wire-format byte message, and sends it to a Gateway Actor's socket connection. The Gateway uses the `To` and `From` addressing to route the message. Messages are sent and received much like e-mail.

### Message Structure

Messages are composed of two address specifications (`To`, `From`), a header, a numeric message type, and an optional data payload which may be up to 2 gigabytes in size. The address specifications are ASCII strings, as is the header. The message type is a standard 32-bit signed integer, and the data payload is an unformatted buffer. The payload size is a standard 32-bit signed integer.

All addressing follows the `clientName@gatewayActorName` convention.

### Connection Event Sequence

When connecting to a Gateway Actor, a socket connection is first established. Following the connection, a `GatewayId` message is sent by the connecting client, which identifies the connection so that message traffic can be routed appropriately. The ID message is required before any other messages will be recognized — until the ID message is received, all messages from the new client will be ignored.

Once an ID is established, messages can be addressed and delivered to the specified `Actor@Gateway`.

### Streaming Mode

Messages use one of two states:
- **STREAM ON** (`GatewayStreamOn` intent) — the Gateway streams responses asynchronously; use `enableConcurrentMode = true` in `Config` to handle pipelined responses.
- **STREAM OFF** (`GatewayStreamOff` intent) — synchronous mode where the client requests one message at a time from a mailbox queue.

Default state is `STREAM OFF`. `PodOsClient.newClient()` sends `GatewayStreamOn` automatically unless `enableStreaming = false`.

### Wire Protocol

The Pod-OS wire protocol is a flat byte stream with a fixed 7×9-byte length prefix:

```
[totalLength:9][toLength:9][fromLength:9][headerLength:9]
[messageType:9][dataType:9][payloadLength:9]
[TO bytes][FROM bytes][HEADER bytes][PAYLOAD bytes]
```

All 9-byte length fields use hex encoding: `x` + 8 hex digits.
`messageType` and `dataType` use zero-padded 9-digit decimal.
Headers are tab-separated `key=value` pairs.
Timestamps are POSIX microseconds formatted as `+%.6f`.

### Sending and Receiving Messages

```java
import com.pointofdata.podos.PodOsClient;
import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.message.*;
import java.time.Duration;
import java.util.UUID;

Config cfg = new Config();
cfg.host             = "zeroth.pod-os.com";
cfg.port             = "62312";
cfg.gatewayActorName = "zeroth.pod-os.com";
cfg.clientName       = "MyJavaClient";

PodOsClient client = PodOsClient.newClient(cfg);

// Build the message
Message msg = new Message();
msg.to         = "mem@zeroth.pod-os.com";
msg.from       = "MyJavaClient@zeroth.pod-os.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreEvent;

// Send and receive
Message response = client.sendMessage(msg, Duration.ofSeconds(10));

// Inspect the response
String status  = response.processingStatus();  // "OK" or "ERROR"
String message = response.processingMessage(); // error detail if status="ERROR"
```

### Checking Response Status

Every Actor response carries a `processingStatus()`. Always check it:

```java
Message response = client.sendMessage(msg, Duration.ofSeconds(10));
if ("ERROR".equals(response.processingStatus())) {
    System.err.println("Actor error: " + response.processingMessage());
    return;
}
// proceed with response data
```

### Concurrent Mode (High Throughput)

Enable `cfg.enableConcurrentMode = true` to pipeline multiple in-flight requests from separate threads. Responses are routed to callers via `MessageId` correlation:

```java
cfg.enableConcurrentMode = true;
cfg.responseTimeout = Duration.ofSeconds(30);
PodOsClient client = PodOsClient.newClient(cfg);

ExecutorService pool = Executors.newFixedThreadPool(32);
List<Future<Message>> futures = new ArrayList<>();

for (int i = 0; i < 1000; i++) {
    final int seq = i;
    futures.add(pool.submit(() -> {
        Message m = buildEventMessage(seq);
        return client.sendMessage(m, Duration.ofSeconds(10));
    }));
}

for (Future<Message> f : futures) {
    Message resp = f.get();
    // process
}
pool.shutdown();
client.close();
```

### MessageId Tracking

Each message should carry a unique `messageId` for conversation tracking. `PodOsClient` auto-generates one if not set:

```java
msg.messageId = UUID.randomUUID().toString(); // or leave null — auto-assigned
```

### WireHook (Observability)

Implement `WireHook` to observe all raw bytes flowing in and out:

```java
cfg.wireHook = new WireHook() {
    @Override public void onSend(byte[] raw)    { /* audit log outgoing frame */ }
    @Override public void onReceive(byte[] raw) { /* audit log incoming frame */ }
};
```
