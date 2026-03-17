# Evolutionary Neural Memory Database — Information Retrieval

Central to Pod-OS is the concept of "pattern search", which permits searching for whole symbols or symbols that match a certain description as opposed to searching for rigidly-defined values such as keywords. For example, "any number" is a pattern, while a list of numbers is a limited set. It is far more efficient to search for all matches of a pattern rather than all matches of a set, since matching a set requires at least N searches, where N is the number of elements in the set.

### Buffered Results

Queries sent to the Evolutionary Neural Memory DB may return more than one message. There is an option to return results either as a single message where the payload contains the list of results, or as a series of individual result messages.

In the series of individual result messages case, replies will be sent after a `GatewayBatchStart` (`BATCH_START`) type message. Individual results are returned in subsequent `ActorRecord` (`RECORD`) type messages. After the last `RECORD` message, a `GatewayBatchEnd` (`BATCH_END`) message indicates no more results are to be sent.

---

## Pattern Matching Types

| Pattern Type | Description | Low Value | High Value |
|---|---|---|---|
| `fastpattern` | Fast, character-based pattern match (native Pod-OS) | Pattern to match | If specified, match all values between low and high |
| `regexp` | Standard regular expression | The regex | Not used |
| `eq` | String equality | String to match | — |
| `ne` | String non-equality | String to match | — |
| `le` | String ≤ low value | String to match | — |
| `lt` | String < low value | String to match | — |
| `ge` | String ≥ low value | String to match | — |
| `gt` | String > low value | String to match | — |
| `distance` | Edit distance ≤ high | Comparison string | Maximum distance allowed |
| `range_eq` | Inclusive range (≥ low AND ≤ high) | Lower limit | Upper limit |
| `range_ne` | Exclusive range (< low OR > high) | Upper limit of low range | Lower limit of high range |
| `int_eq` | Integer equality | Integer to match | — |
| `int_ne` | Integer non-equality | Integer to match | — |
| `int_le` | Integer ≤ | Integer to match | — |
| `int_lt` | Integer < | Integer to match | — |
| `int_ge` | Integer ≥ | Integer to match | — |
| `int_gt` | Integer > | Integer to match | — |
| `int_range_eq` | Integer range | Bottom of range | Top of range |
| `int_range_ne` | Integer range exclusion | Bottom of range | Top of range |
| `dbl_eq` | Double equality | Float to match | — |
| `dbl_ne` | Double non-equality | Float to match | — |
| `dbl_le` | Double ≤ | Float to match | — |
| `dbl_lt` | Double < | Float to match | — |
| `dbl_ge` | Double ≥ | Float to match | — |
| `dbl_gt` | Double > | Float to match | — |
| `dbl_range_eq` | Double range | Bottom of range | Top of range |
| `dbl_range_ne` | Double range exclusion | Bottom of range | Top of range |

---

## Search Clause Components

A search clause finds events that match the search and alters results using a boolean operation.

| Field | Description | Content |
|---|---|---|
| `clause_type` | Type of clause | Required — must be `S` |
| `boolean` | Boolean operation | Required — `and`, `or`, `not`, `xor`. First clause is always forced to `or` |
| `low` | Low or match pattern for tag values | Required |
| `high` | High match pattern | Optional — for range |
| `filter_type` | Tag value filter type | Optional |
| `filter_low` | Low match value for filter | Required if `filter_type` used |
| `filter_high` | High match value for filter range | Optional |
| `owner_event_id` | Tag owner event key | Optional |
| `event_id` | Event key for tag association | Optional |
| `clause_name` | Name of the clause | Optional — used for branching |

Clauses are tab-separated and newline-terminated: `clause_type:S\tboolean:or\tlow:action=click`

---

## Retrieval Messages with Examples

### GetEvent

Use to retrieve a single Event Object by `EventId`, `EventUniqueId`, or time+location.

**Rules:**
- Use `GetEvent` when `Event.id`, `Event.uniqueId`, or time+location are known.
- If `getLinks=true` and `sendData=true`, Pod-OS returns links rather than the data blob.

#### Example 1 — Basic GetEvent (by EventId)

```java
import com.pointofdata.podos.message.*;
import java.util.UUID;

Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEvent;

msg.event = new EventFields();
msg.event.id = "2024.01.15.14.30.45.123456@actor1|location1|segment1";

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.getEvent = new GetEventOptions();
msg.neuralMemory.getEvent.sendData = true;   // request payload data with MIME type
msg.neuralMemory.getEvent.getTags  = true;   // request tags for the event
msg.neuralMemory.getEvent.getLinks = false;

Message response = client.sendMessage(msg, Duration.ofSeconds(10));

// Access response fields
String eventId     = response.eventId();
String uniqueId    = response.eventUniqueId();
String status      = response.processingStatus();  // "OK" or "ERROR"
```

#### Example 2 — Advanced GetEvent (with tags, links, filters)

```java
Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEvent;

msg.event = new EventFields();
msg.event.id                = "2024.01.15.14.30.45.123456@actor1|location1|segment1";
msg.event.uniqueId          = "user-provided-unique-id-123";
msg.event.timestamp         = "+1705327845.123456";
msg.event.locationSeparator = "|";
msg.event.location          = "TERRA|47.619463|-122.518691|100.5";

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.getEvent = new GetEventOptions();
msg.neuralMemory.getEvent.sendData         = true;
msg.neuralMemory.getEvent.getTags          = true;
msg.neuralMemory.getEvent.eventFacetFilter = "location*,action*";
msg.neuralMemory.getEvent.tagFilter        = "^user.*=";
msg.neuralMemory.getEvent.getLinks         = true;
msg.neuralMemory.getEvent.getLinkTags      = true;
msg.neuralMemory.getEvent.getTargetTags    = true;
msg.neuralMemory.getEvent.firstLink        = 0;
msg.neuralMemory.getEvent.linkCount        = 10;
msg.neuralMemory.getEvent.linkFacetFilter  = "category*";
msg.neuralMemory.getEvent.categoryFilter   = "related,similar";
msg.neuralMemory.getEvent.requestFormat    = 2;

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

#### Example 3 — Minimal GetEvent (by UniqueId)

```java
Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEvent;

msg.event = new EventFields();
msg.event.uniqueId = "user-provided-unique-id-123";

Message response = client.sendMessage(msg, Duration.ofSeconds(10));
```

---

### GetEventsForTags

Used to retrieve Events that match Tag search parameters. Searches use a series of match clauses in the payload.

#### Rules

There are three types of search clauses:
1. **Search clause (`S`)** — finds events associated with tags matching a value pattern.
2. **Branch clause (`B`)** — jumps to a named clause based on a pattern and a data source.
3. **Action clause (`A`)** — alters the result set, saves data, or takes an action.

Clauses are evaluated in order. Each clause is tab-separated `fieldname:value` pairs, terminated with a newline.

#### Example 1 — Basic GetEventsForTags

```java
Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEventsForTags;

msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
// Single clause: find all events tagged with action=click
msg.payload.data     = "clause_type:S\tboolean:or\tlow:action=click";
msg.payload.dataType = DataType.RAW;

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.getEventsForTags = new GetEventsForTagsOptions();
msg.neuralMemory.getEventsForTags.bufferResults   = true;  // single reply payload
msg.neuralMemory.getEventsForTags.includeTagStats = true;  // return tag stats per match
msg.neuralMemory.getEventsForTags.bufferFormat    = "0";   // text output

Message response = client.sendMessage(msg, Duration.ofSeconds(30));
```

#### Example 2 — Advanced GetEventsForTags (with filtering and paging)

```java
Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEventsForTags;

// Multi-clause search: events tagged with "action=click" AND "user=*"
String clauses = "clause_type:S\tboolean:or\tlow:action=click\n"
               + "clause_type:S\tboolean:and\tlow:user=*";
msg.payload = new PayloadFields();
msg.payload.mimeType = "text/plain";
msg.payload.data     = clauses;
msg.payload.dataType = DataType.RAW;

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.getEventsForTags = new GetEventsForTagsOptions();

// Event filtering
msg.neuralMemory.getEventsForTags.eventPattern      = "2024.*";
msg.neuralMemory.getEventsForTags.includeBriefHits  = false;
msg.neuralMemory.getEventsForTags.getAllData         = false;

// Paging
msg.neuralMemory.getEventsForTags.startResult       = 0;
msg.neuralMemory.getEventsForTags.endResult         = 100;
msg.neuralMemory.getEventsForTags.eventsPerMessage  = 50;
msg.neuralMemory.getEventsForTags.minEventHits      = 1;

// Link retrieval
msg.neuralMemory.getEventsForTags.firstLink          = 0;
msg.neuralMemory.getEventsForTags.linkCount          = 10;
msg.neuralMemory.getEventsForTags.getMatchLinks      = true;
msg.neuralMemory.getEventsForTags.countMatchLinks    = false;
msg.neuralMemory.getEventsForTags.getLinkTags        = true;
msg.neuralMemory.getEventsForTags.linkTagFilter      = "category=*";
msg.neuralMemory.getEventsForTags.linkedEventsFilter = "type=document";
msg.neuralMemory.getEventsForTags.linkCategory       = "related";

// Output options
msg.neuralMemory.getEventsForTags.countOnly          = false;
msg.neuralMemory.getEventsForTags.bufferResults      = true;
msg.neuralMemory.getEventsForTags.includeTagStats    = true;
msg.neuralMemory.getEventsForTags.hitTagFilter       = "^(action|user)=";
msg.neuralMemory.getEventsForTags.invertHitTagFilter = false;
msg.neuralMemory.getEventsForTags.bufferFormat       = "0";

Message response = client.sendMessage(msg, Duration.ofSeconds(30));

// Response fields
String status    = response.processingStatus();
int eventCount   = response.response != null ? response.response.eventCount : 0;
```

---

### GetEvent with Tags (GetTags)

Use `GetEvent` with `getTags = true` to retrieve tags for an event.

```java
Message msg = new Message();
msg.to         = "mem@zeroth.example.com";
msg.from       = "MyJavaClient@zeroth.example.com";
msg.clientName = "MyJavaClient";
msg.messageId  = UUID.randomUUID().toString();
msg.intent     = IntentTypes.INSTANCE.GetEvent;

msg.event = new EventFields();
msg.event.id = "2024.01.15.14.30.45.123456@actor1|location1|segment1";

msg.neuralMemory = new NeuralMemoryFields();
msg.neuralMemory.getEvent = new GetEventOptions();
msg.neuralMemory.getEvent.getTags          = true;
msg.neuralMemory.getEvent.requestFormat    = 2;
msg.neuralMemory.getEvent.eventFacetFilter = "category:*"; // filter tags by prefix

Message response = client.sendMessage(msg, Duration.ofSeconds(10));

// Tags are in response.response.tagResults
if (response.response != null) {
    for (TagOutput tag : response.response.tagResults) {
        System.out.printf("freq=%d category=%s value=%s%n",
            tag.frequency, tag.category, tag.value);
    }
}
```

**Response tag payload format:** `<frequency>\t<category>\t<value>\n`

Example:
```
1	*	word1
10	*	word2
5	location	city=Seattle
```

---

## Programmable Searches (Compound)

Pod-OS supports compound searches — the results of one search clause can be used to feed another, enabling "n-dimensional" or "tiered" retrieval without low-level programming.

**Example:** Find all events tagged with a keyword, then use the tags on those events to find semantically related events (regardless of type — text, media, binary):

```java
// Clause 1: find events matching "topic=quantum_mechanics"
// Clause 2: AND those where "user=*" (filter to user-owned events)
// Action: jump to clause 3 if result count > 50
String clauses =
    "clause_type:S\tboolean:or\tlow:topic=quantum_mechanics\n" +
    "clause_type:S\tboolean:and\tlow:user=*\n" +
    "clause_type:B\tboolean:or\tsource:$total_events\tfilter_type:int_gt\tfilter_low:50\ttarget:done\n" +
    "clause_name:done";

msg.payload.data = clauses;
```
