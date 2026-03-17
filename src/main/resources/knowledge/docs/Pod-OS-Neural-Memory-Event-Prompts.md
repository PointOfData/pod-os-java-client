## Evolutionary Neural Memory Database — Event Storage

Pod-OS provides an Evolutionary Neural Memory Database with the following behavioral characteristics: acquiring (encoding), stabilizing (consolidation), and retrieving information, and forming engrams (memory traces) within groups of Events. Key characteristics include its large storage capacity with decreasing marginal storage volume, native version management, and the ability to inter-link and describe any and all Event Objects using weights or activation functions, action policies, or other objective function methods.

The Evolutionary Neural Memory DB's central thesis is that each moment in time captures and processes important context that software and the most advanced, complex and adaptive AIs and robotics require — including LLMs, RAG, robotics, World Models, and other advanced AI and robotics applications.

### Three Primitives

1. **Event Objects** — A uniquely identified datum (from any source) used as a base reference for a set of versioned attributes and context-bound weighted links to other event objects. Event Objects carry crucial context information including time and location, owner, encryption, message source as `actor@gateway`, message destination `actor@gateway`, and payload MIME and binary data.

2. **Tags** — Values that describe important information about the Event. The simplest design is `frequency=tagvalue`. A powerful design option uses Facets: key/value pairs in the format `frequency=key_name=key_value`. Tags may be embeddings, hashes, pointers, or binary data.

3. **Link Objects** — Links connect any two Events (including Link Events). Since Links are Events themselves, they carry Event Object and Tag descriptions along with weights (integer or continuous function).

### Store Event Object Efficiency Guidance

- When multiple Event Objects need to be created, strongly prefer `StoreBatchEvents` intent as it is more efficient; this can also accept Tags as part of the batch.
- When using `StoreBatchEvents`, optimize batch size to minimize network overhead and latency; optimal batch size is 10,000–100,000 events per batch.
- When a single Event Object is created, prefer to add Tags in `StoreEvent` as it is more efficient.
- When adding Tags prefer to use `StoreBatchTags` intent.

### Ownership Guidance

1. The `EventOwner` is the `EventId` or `EventUniqueId` of the entity that created the Event Object.
2. RBAC ownership is defined elsewhere and is not part of the Event Owner.

### Reference Guidance

- Internally, all communication about Event Objects and Link Objects uses the `EventId` or `EventUniqueId` field. The `EventId` is created by the Actor when the Event Object is stored and is the primary reference.
- `EventUniqueId` is a developer-set customer ID for external reference.
- `MessageId` is used by client applications to track the message and conversation flow.

### Event Creation Rules

- The Event object must exist in the Evolutionary Neural Memory database store before any associated `StoreBatchTags` or `LinkEvent` Intents are created.
- Definition of "exist": the Actor responded with status `OK` when the Event Object is stored or retrieved.
- For any `EventOwner` other than `$sys`, the `EventOwner` `EventId` or `EventUniqueId` must already exist in the database.
- `EventUniqueId` is a useful developer-set customer ID for external reference.

The `EventId` returned from the Actor is formatted using ASCII character 1 (0x01) as delimiter: `"timestamp delim loc segment 1 delim loc segment 2 delim ... loc segment N"`.

---

## Storing Events

### StoreEvent

```java
import com.pointofdata.podos.message.*;
import java.util.UUID;

String domainName = "zeroth.pod-os.com";
String baseEventId = "$sys"; // or an existing EventId

Message msg = new Message();
msg.to         = "administration@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreEvent;

msg.event = new EventFields();
msg.event.owner             = baseEventId;
msg.event.uniqueId          = UUID.randomUUID().toString();
msg.event.timestamp         = MessageUtils.getTimestamp();
msg.event.location          = "TERRA|47.619463|-122.518691";
msg.event.locationSeparator = "|";
msg.event.type              = "system log object";

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = "System initialization log";
msg.payload.dataType = DataType.RAW;

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
String eventId = response.eventId(); // use for subsequent operations
```

### StoreEvent with Tags

```java
import java.util.Arrays;

Message msg = new Message();
msg.to         = "administration@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreEvent;

msg.event = new EventFields();
msg.event.owner             = "$sys";
msg.event.uniqueId          = UUID.randomUUID().toString();
msg.event.timestamp         = MessageUtils.getTimestamp();
msg.event.location          = "TERRA|47.619463|-122.518691";
msg.event.locationSeparator = "|";
msg.event.type              = "system log object";

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = "System initialization log";
msg.payload.dataType = DataType.RAW;

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.tags = Arrays.asList(
    new Tag(1, "domain",    domainName),
    new Tag(1, "log_type",  "system"),
    new Tag(1, "severity",  "INFO"),
    new Tag(1, "component", "pod-os-core"),
    new Tag(1, "timestamp", MessageUtils.getTimestamp())
);

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

### StoreBatchEvents

The text payload contains newline-terminated records, each formatted as `fieldname=value<tab>fieldname=value...`.

Required fields per record:
- `EventUniqueId` — developer-provided unique ID
- `EventOwner` OR `EventOwnerUniqueId` — use `$sys` for system-level creation
- `EventTimestamp` — POSIX microseconds (`+%.6f` format)
- `EventLocation` — developer-defined location
- `EventLocationSeparator` — separator character (default `|`)
- `EventType` — developer-defined event type string

Tags may be appended to the same line, tab-separated, as `unique_tag_number=freq:tag_value`.
Facets use: `tag_number=frequency:key_name=key_value`.

```java
import com.pointofdata.podos.message.*;
import java.util.Arrays;
import java.util.UUID;

// Build batch using BatchEventSpec
List<BatchEventSpec> batch = new ArrayList<>();
for (int i = 0; i < 10000; i++) {
    BatchEventSpec spec = new BatchEventSpec();
    spec.uniqueId          = UUID.randomUUID().toString();
    spec.owner             = "$sys";
    spec.timestamp         = MessageUtils.getTimestamp();
    spec.location          = "TERRA|47.619463|-122.518691";
    spec.locationSeparator = "|";
    spec.type              = "sensor reading";
    batch.add(spec);
}

Message msg = new Message();
msg.to         = "administration@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreBatchEvents;

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = batch;     // encoder serializes list to wire format
msg.payload.dataType = DataType.RAW;

// For large batches, use a longer timeout
Message response = client.sendMessage(msg, Duration.ofMinutes(3));
```

**Plain-text payload format example:**
```
EventUniqueId=550e8400-e29b-41d4-a716-446655440000	EventOwner=$sys	EventTimestamp=+1705318200.000000	EventLocation=TERRA|47.619463|-122.518691	EventLocationSeparator=|	EventType=system log object
EventUniqueId=550e8400-e29b-41d4-a716-446655440001	EventOwner=$sys	EventTimestamp=+1705318260.000000	EventLocation=TERRA|47.619463|-122.518691	EventLocationSeparator=|	EventType=system log object
```

---

## Storing Tags

### StoreBatchTags

The text payload contains newline-terminated records formatted as `frequency=tagvalue`.
Facets use: `frequency=key_name=key_value`.

```java
Message msg = new Message();
msg.to         = "administration@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreBatchTags;

msg.event = new EventFields();
msg.event.owner    = ownerEventId;
msg.event.id       = existingEventId; // EventId must already exist in the database

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = "10=the\n12=then\n100=and\n"; // frequency=tagvalue per line
msg.payload.dataType = DataType.RAW;

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

**Tag Rules:**
- `frequency` is a non-zero positive 64-bit integer.
- A negative frequency marks a tag as inactive.
- A frequency of zero stores the tag outside the index.
- Tags may be up to 1,000 bytes.
- The `$sys` owner makes tags publicly searchable.

---

## Linking Events

### LinkEvent

Links connect any two Event Objects with bidirectional weights and a category.

```java
Message msg = new Message();
msg.to         = "account@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.LinkEvent;

msg.event = new EventFields();
msg.event.owner             = ownerEventId;
msg.event.uniqueId          = UUID.randomUUID().toString();
msg.event.timestamp         = MessageUtils.getTimestamp();
msg.event.location          = "TERRA|47.619463|-122.518691";
msg.event.locationSeparator = "|";
msg.event.type              = "account link";

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.link = new LinkFields();
msg.neuralMemory.link.uniqueIdA = eventIdA;
msg.neuralMemory.link.uniqueIdB = eventIdB;
msg.neuralMemory.link.strengthA = 1.0;
msg.neuralMemory.link.strengthB = 1.0;
msg.neuralMemory.link.ownerId   = ownerEventId;
msg.neuralMemory.link.timestamp = MessageUtils.getTimestamp();
msg.neuralMemory.link.location          = "TERRA|47.619463|-122.518691";
msg.neuralMemory.link.locationSeparator = "|";
msg.neuralMemory.link.category = "Account";

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

**Link Rules:**
- An Event may be a Storage Event or a Link Event; links between Links are valid.
- There is no upper bound on the number of Links between Event Objects.
- Category is a null-terminated ASCII character string; there is no central directory.

### UnlinkEvent

```java
Message msg = new Message();
msg.to         = "account@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.UnlinkEvent;

msg.event = new EventFields();
msg.event.id        = linkEventId;  // the EventId of the Link Event
msg.event.timestamp = MessageUtils.getTimestamp();
msg.event.location          = "TERRA|47.619463|-122.518691";
msg.event.locationSeparator = "|";

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

### StoreBatchLinks

The text payload contains newline-terminated records, each tab-separated:

```java
Message msg = new Message();
msg.to         = "account@" + domainName;
msg.from       = "MyJavaClient@" + domainName;
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.StoreBatchLinks;

// Build batch using BatchLinkEventSpec
List<BatchLinkEventSpec> links = new ArrayList<>();
BatchLinkEventSpec link = new BatchLinkEventSpec();
link.uniqueIdA         = eventIdA;
link.uniqueIdB         = eventIdB;
link.strengthA         = 1.0;
link.strengthB         = 1.0;
link.category          = "Account";
link.owner             = "$sys";
link.uniqueId          = UUID.randomUUID().toString();
link.timestamp         = MessageUtils.getTimestamp();
link.location          = "TERRA|47.619463|-122.518691";
link.locationSeparator = "|";
link.type              = "account link";
links.add(link);

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = links;
msg.payload.dataType = DataType.RAW;

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

**Plain-text payload format example:**
```
unique_id_a=1234567890	unique_id_b=1234567891	strength_a=1.0	strength_b=1.0	category=Account	owner=$sys	unique_id=abc-123	time=+1705318200.000000	loc=TERRA|47.619463|-122.518691	loc_delim=|	type=account link
```

---

## Design Patterns

- **Sharding** — Separate Event Objects into each shard using routing rules; retrieve by filtering on Tag values using `GetEventsForTags` for each shard.
- **Replication** — Duplicate Event Objects by creating a Link Object to the original.
- **Relational data structure** — Simulate a table by creating a table Event Object, Event Objects for each row, and column Event Objects; associate in a hierarchy using Link Objects.
- **Knowledge Graph** — Use Links and Tags to build interconnected knowledge structures.
- **RAG (Retrieval Augmented Generation)** — Store document chunks as Events, embed vectors as Tags, retrieve via semantic search using `GetEventsForTags` pattern matching.
