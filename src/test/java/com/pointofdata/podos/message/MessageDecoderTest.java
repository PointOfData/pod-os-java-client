package com.pointofdata.podos.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageDecoder")
class MessageDecoderTest {

    private static byte[] buildMinimalMessage(String to, String from, String header,
                                               int messageType, int dataType, String payload) {
        int toLen = to.length();
        int fromLen = from.length();
        int headerLen = header.length();
        int payloadLen = payload.length();
        int totalLen = 7 * 9 + toLen + fromLen + headerLen + payloadLen;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%9d", totalLen));
        sb.append(String.format("%9d", toLen));
        sb.append(String.format("%9d", fromLen));
        sb.append(String.format("%9d", headerLen));
        sb.append(String.format("%9d", messageType));
        sb.append(String.format("%9d", dataType));
        sb.append(String.format("%9d", payloadLen));
        sb.append(to);
        sb.append(from);
        sb.append(header);
        sb.append(payload);

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("GetEventsForTags _link= decodes as Linked from the hit's perspective")
    void getEventsForTagsDecodesLinked() {
        String header = "_type=events_for_tag\t_status=OK\t_count=1";
        String payload = "_event_id=evt1\tunique_id=src_uid\ttag:1:_unique_id=src_uid\n"
                + "_link=link1\tsource=evt1\tsource_unique_id=src_uid\ttarget=evt2\tstrength=0.9\tcategory=describes\ttarget_unique_id=tgt_uid";

        byte[] raw = buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload);

        Message msg = MessageDecoder.decodeMessage(raw);

        assertNotNull(msg.response);
        assertEquals(1, msg.response.eventRecords.size());

        EventFields event = msg.response.eventRecords.get(0);
        assertEquals(1, event.links.size());

        Linked link = event.links.get(0);
        assertEquals("src_uid", link.sourceUniqueId);
        assertEquals("tgt_uid", link.targetUniqueId);
        assertEquals(0.9, link.strength);
        assertEquals("evt1", link.sourceId);
        assertEquals("evt2", link.targetId);
    }

    @Test
    @DisplayName("GetEvent _link= decodes as Linked from the returned event")
    void getEventDecodesLinked() {
        String header = "_type=get\t_status=OK\t_event_id=evt1\t_unique_id=src_uid";
        String payload = "_link=link1\ttarget_event=evt2\ttarget_unique_id=tgt_uid\tstrength=0.9\tcategory=describes";

        byte[] raw = buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload);

        Message msg = MessageDecoder.decodeMessage(raw);

        assertNotNull(msg.event);
        assertEquals(1, msg.event.links.size());

        Linked link = msg.event.links.get(0);
        assertEquals("src_uid", link.sourceUniqueId);
        assertEquals("tgt_uid", link.targetUniqueId);
        assertEquals(0.9, link.strength);
        assertEquals("evt1", link.sourceId);
        assertEquals("evt2", link.targetId);
    }

    @Test
    @DisplayName("GetEvent outbound and inbound both decode as source=returned event")
    void getEventViewerPerspective() {
        String header = "_type=get\t_status=OK\t_event_id=evt_a\t_unique_id=eo_seed_a";
        String payload = "_link=+1\tunique_id=lk_a_to_x\ttarget_event=evt_x\ttarget_unique_id=eo_nbr_x\tstrength=0.750000\tcategory=supplies_to\n"
                + "_link=+2\tunique_id=lk_b_to_a\ttarget_event=evt_b\ttarget_unique_id=eo_nbr_b\tstrength=0.250000\tcategory=supplies_to";

        byte[] raw = buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload);

        Message msg = MessageDecoder.decodeMessage(raw);

        assertNotNull(msg.event);
        assertEquals(2, msg.event.links.size());

        Linked out = msg.event.links.get(0);
        Linked inb = msg.event.links.get(1);
        assertEquals("eo_seed_a", out.sourceUniqueId);
        assertEquals("eo_nbr_x", out.targetUniqueId);
        assertEquals(0.75, out.strength, 1e-9);
        assertEquals("eo_seed_a", inb.sourceUniqueId);
        assertEquals("eo_nbr_b", inb.targetUniqueId);
        assertEquals(0.25, inb.strength, 1e-9);
    }

    @Test
    @DisplayName("GetEvent rows that share a _link event id stay distinct by unique id")
    void getEventSharedLinkEventId() {
        String header = "_type=get\t_status=OK\t_event_id=evt_a\t_unique_id=eo_seed_a";
        String shared = "+1792088206.138462";
        String payload = "_link=" + shared + "\tunique_id=lk_a_to_x\ttarget_event=evt_x\ttarget_unique_id=eo_nbr_x\tstrength=0.750000\tcategory=supplies_to\n"
                + "_linktag\tevent_id=" + shared + "\tunique=lk_a_to_x\tfreq=1\tvalue=endpoint_a=eo_seed_a\n"
                + "_link=" + shared + "\tunique_id=lk_b_to_a\ttarget_event=evt_b\ttarget_unique_id=eo_nbr_b\tstrength=0.250000\tcategory=supplies_to\n"
                + "_linktag\tevent_id=" + shared + "\tunique=lk_b_to_a\tfreq=1\tvalue=endpoint_a=eo_nbr_b";

        Message msg = MessageDecoder.decodeMessage(buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload));

        assertNotNull(msg.event);
        assertEquals(2, msg.event.links.size());
        Map<String, Linked> byUid = msg.event.links.stream()
                .collect(Collectors.toMap(lk -> lk.uniqueId, lk -> lk));
        Linked out = byUid.get("lk_a_to_x");
        Linked inb = byUid.get("lk_b_to_a");
        assertEquals("eo_seed_a", out.sourceUniqueId);
        assertEquals("eo_nbr_x", out.targetUniqueId);
        assertEquals(0.75, out.strength, 1e-9);
        assertEquals(1, out.tags.size());
        assertEquals("endpoint_a", out.tags.get(0).key);
        assertEquals("eo_seed_a", out.tags.get(0).value);
        assertEquals("eo_seed_a", inb.sourceUniqueId);
        assertEquals("eo_nbr_b", inb.targetUniqueId);
        assertEquals(0.25, inb.strength, 1e-9);
        assertEquals(1, inb.tags.size());
        assertEquals("eo_nbr_b", inb.tags.get(0).value);
    }

    @Test
    @DisplayName("GetEvent link tags keyed only by event id still attach to a single unique row")
    void getEventLinkTagWithoutUnique() {
        String header = "_type=get\t_status=OK\t_event_id=evt1\t_unique_id=src_uid";
        String payload = "_link=link1\tunique_id=lk1\ttarget_event=evt2\ttarget_unique_id=tgt_uid\tstrength=0.9\tcategory=describes\n"
                + "_linktag\tevent_id=link1\tfreq=1\tvalue=color=blue";

        Message msg = MessageDecoder.decodeMessage(buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload));

        assertEquals(1, msg.event.links.size());
        Linked link = msg.event.links.get(0);
        assertEquals("lk1", link.uniqueId);
        assertEquals(1, link.tags.size());
        assertEquals("color", link.tags.get(0).key);
        assertEquals("blue", link.tags.get(0).value);
    }

    @Test
    @DisplayName("GetEventsForTags rows that share a _link event id both survive")
    void getEventsForTagsSharedLinkEventId() {
        String header = "_type=events_for_tag\t_status=OK\t_count=1";
        String shared = "+1792088467.007218";
        String payload = "_event_id=evt_a\tunique_id=eo_seed_a\ttag:1:_unique_id=eo_seed_a\n"
                + "_link=" + shared + "\tsource=evt_a\tsource_unique_id=eo_seed_a\ttarget=evt_x\ttarget_unique_id=eo_nbr_x\tstrength=0.750000\tcategory=supplies_to\tunique_id=lk_a_to_x\n"
                + "_linktag=" + shared + "\tfreq=1\tvalue=endpoint_a=eo_seed_a\n"
                + "_link=" + shared + "\tsource=evt_a\tsource_unique_id=eo_seed_a\ttarget=evt_b\ttarget_unique_id=eo_nbr_b\tstrength=0.250000\tcategory=supplies_to\tunique_id=lk_b_to_a";

        Message msg = MessageDecoder.decodeMessage(buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload));

        assertNotNull(msg.response);
        assertEquals(1, msg.response.eventRecords.size());
        EventFields event = msg.response.eventRecords.get(0);
        assertEquals(2, event.links.size());
        Map<String, Linked> byUid = event.links.stream()
                .collect(Collectors.toMap(lk -> lk.uniqueId, lk -> lk));
        Linked out = byUid.get("lk_a_to_x");
        Linked inb = byUid.get("lk_b_to_a");
        assertEquals("eo_seed_a", out.sourceUniqueId);
        assertEquals("eo_nbr_x", out.targetUniqueId);
        assertEquals(0.75, out.strength, 1e-9);
        assertEquals("eo_seed_a", inb.sourceUniqueId);
        assertEquals("eo_nbr_b", inb.targetUniqueId);
        assertEquals(0.25, inb.strength, 1e-9);
        assertEquals(1, out.tags.size());
        assertEquals("eo_seed_a", out.tags.get(0).value);
        assertEquals(1, inb.tags.size());
        assertEquals("eo_seed_a", inb.tags.get(0).value);
    }
}
