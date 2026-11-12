package com.pointofdata.podos.message;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TagHistoryTest {

    private static TagOutput tag(String key, String value, String ts, int n) {
        TagOutput t = new TagOutput();
        t.key = key;
        t.value = value;
        t.timestamp = ts;
        t.tagNumber = n;
        t.frequency = 1;
        return t;
    }

    @Test
    void latestByKeyTimestampThenTagNumber() {
        List<TagOutput> tags = Arrays.asList(
                tag("hue.alpha", "old", "0000000001.000000", 9),
                tag("hue.alpha", "mid", "0000000002.000000", 1),
                tag("hue.alpha", "tie-low", "0000000002.000000", 4),
                tag("hue.beta", "only", "0000000001.000000", 1),
                tag("hue.alpha", "tie-high", "0000000002.000000", 4)
        );
        Map<String, TagOutput> got = TagHistory.latestByKey(tags);
        assertEquals("tie-high", got.get("hue.alpha").value);
        assertEquals("only", got.get("hue.beta").value);
        Map<String, TagOutput> later = TagHistory.latestByKey(Arrays.asList(
                tag("k", "low-number", "0000000005.000000", 1),
                tag("k", "high-number", "0000000005.000000", 8)
        ));
        assertEquals("high-number", later.get("k").value);
        Map<String, TagOutput> earlierTs = TagHistory.latestByKey(Arrays.asList(
                tag("k", "new-ts", "0000000009.000000", 1),
                tag("k", "old-ts", "0000000001.000000", 99)
        ));
        assertEquals("new-ts", earlierTs.get("k").value);
    }

    @Test
    void fieldHistoryZipAndEdges() {
        List<TagOutput> tags = Arrays.asList(
                tag("paint.beta", "b0", "0000000002.000000", 1),
                tag("paint.alpha", "a-low", "0000000002.000000", 2),
                tag("paint.alpha", "a-tie", "0000000002.000000", 9),
                tag("other.alpha", "nope", "0000000009.000000", 1),
                tag("paint", "bare", "0000000009.000000", 1)
        );
        List<TagHistory.TagSighting> got = TagHistory.fieldHistory(tags, "paint");
        assertEquals(2, got.size());
        assertEquals(0, got.get(0).index);
        assertEquals("a-low", got.get(0).facets.get("alpha").value);
        assertFalse(got.get(0).facets.containsKey("beta"));
        assertEquals("a-tie", got.get(1).facets.get("alpha").value);
        assertEquals("b0", got.get(1).facets.get("beta").value);
        assertTrue(TagHistory.fieldHistory(tags, "").isEmpty());
        assertTrue(TagHistory.fieldHistory(tags, "pai").isEmpty());

        List<TagHistory.TagSighting> dotted = TagHistory.fieldHistory(Arrays.asList(
                tag("a.b.c", "yes", "0000000001.000000", 1),
                tag("a.bc", "no", "0000000001.000000", 1),
                tag("a.b", "bare", "0000000001.000000", 1)
        ), "a.b");
        assertEquals(1, dotted.size());
        assertEquals("yes", dotted.get(0).facets.get("c").value);
        assertEquals(1, dotted.get(0).facets.size());
    }

    @Test
    void conventionalFacetNamesAreOrdinaryKeys() {
        List<TagOutput> tags = Arrays.asList(
                tag("stage.value", "seed", "0000000001.000000", 1),
                tag("stage.event_timestamp", "2020", "0000000001.000001", 2),
                tag("stage.delta_type", "initial", "0000000001.000002", 3),
                tag("stage.confidence", "0.5", "0000000001.000003", 4),
                tag("stage.evidence", "", "0000000001.000004", 5),
                tag("stage.source", "doc-1", "0000000001.000005", 6),
                tag("stage.value", "series-a", "0000000002.000000", 7),
                tag("stage.source", "doc-2", "0000000002.000005", 12)
        );
        List<TagHistory.TagSighting> got = TagHistory.fieldHistory(tags, "stage");
        assertEquals("seed", got.get(0).facets.get("value").value);
        assertEquals("doc-2", got.get(1).facets.get("source").value);
    }

    @Test
    void suggestDeltaAndClauses() {
        assertEquals("initial", TagHistory.suggestDelta(false, "", "seed"));
        assertEquals("confirmed", TagHistory.suggestDelta(true, "seed", "seed"));
        assertEquals("modified", TagHistory.suggestDelta(true, "seed", "series-a"));

        String got = TagHistory.tagSearchClauses(Arrays.asList(
                new TagHistory.TagTerm("hue.alpha", "red=blue"),
                new TagHistory.TagTerm("hue.beta", "1")
        ));
        String[] lines = got.split("\n", -1);
        assertEquals("clause_type:S\tboolean:or\tlow:hue.alpha=red=blue", lines[0]);
        assertEquals("clause_type:S\tboolean:and\tlow:hue.beta=1", lines[1]);
    }

    @Test
    void decodeGetEventLinkAndTargetTagHistory() {
        String header = "_type=get\t_status=OK\t_event_id=evt_a\t_unique_id=eo_seed";
        String payload = String.join("\n",
                "_link=link1\tunique_id=lk_1\ttarget_event=evt_n\ttarget_unique_id=eo_nbr\tstrength=0.5\tcategory=rel",
                "_linktag\tevent_id=link1\tunique=lk_1\tfreq=1\ttimestamp=0000000002.000000\tvalue=paint.alpha=later",
                "_linktag\tevent_id=link1\tunique=lk_1\tfreq=1\ttimestamp=0000000001.000000\tvalue=paint.alpha=earlier",
                "_target_event_tag\tevent_id=evt_n\tfreq=1\ttimestamp=0000000002.000000\tvalue=paint.alpha=n-later",
                "_target_event_tag\tevent_id=evt_n\tfreq=1\ttimestamp=0000000001.000000\tvalue=paint.alpha=n-earlier"
        );
        Message decoded = MessageDecoder.decodeMessage(frame(header, payload));
        assertNotNull(decoded.event);
        assertEquals(1, decoded.event.links.size());
        Linked link = decoded.event.links.get(0);
        assertEquals(2, link.tags.size());
        assertFalse(link.tags.get(0).timestamp.isEmpty());
        assertFalse(link.tags.get(1).timestamp.isEmpty());
        assertEquals(2, link.targetTags.size());
        List<TagHistory.TagSighting> linkHist = TagHistory.fieldHistory(link.tags, "paint");
        assertEquals("earlier", linkHist.get(0).facets.get("alpha").value);
        assertEquals("later", linkHist.get(1).facets.get("alpha").value);
        List<TagHistory.TagSighting> targetHist = TagHistory.fieldHistory(link.targetTags, "paint");
        assertEquals("n-earlier", targetHist.get(0).facets.get("alpha").value);
        assertEquals("n-later", targetHist.get(1).facets.get("alpha").value);
    }

    private static byte[] frame(String header, String payload) {
        String to = "mem@gateway.example.com";
        String from = "client@gateway.example.com";
        int totalLen = 7 * 9 + to.length() + from.length() + header.length() + payload.length();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%9d", totalLen));
        sb.append(String.format("%9d", to.length()));
        sb.append(String.format("%9d", from.length()));
        sb.append(String.format("%9d", header.length()));
        sb.append(String.format("%9d", 1001));
        sb.append(String.format("%9d", 0));
        sb.append(String.format("%9d", payload.length()));
        sb.append(to).append(from).append(header).append(payload);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
