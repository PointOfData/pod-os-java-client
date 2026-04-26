package com.pointofdata.podos.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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
    @DisplayName("GetEventsForTags links populate uniqueIdA from parent event uniqueId")
    void getEventsForTagsLinksPopulateUniqueIdA() {
        String header = "_type=events_for_tag\t_status=OK\t_count=1";
        String payload = "_event_id=evt1\tunique_id=src_uid\ttag:1:_unique_id=src_uid\n"
                + "_link=link1\tsource=evt1\ttarget=evt2\tstrength=0.9\tcategory=describes\ttarget_unique_id=tgt_uid";

        byte[] raw = buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload);

        Message msg = MessageDecoder.decodeMessage(raw);

        assertNotNull(msg.response);
        assertEquals(1, msg.response.eventRecords.size());

        EventFields event = msg.response.eventRecords.get(0);
        assertEquals(1, event.links.size());

        LinkFields link = event.links.get(0);
        assertEquals("src_uid", link.uniqueIdA);
        assertEquals("tgt_uid", link.uniqueIdB);
    }

    @Test
    @DisplayName("GetEvent links populate uniqueIdA from parent event uniqueId")
    void getEventLinksPopulateUniqueIdA() {
        String header = "_type=get\t_status=OK\t_event_id=evt1\t_unique_id=src_uid";
        String payload = "_link=link1\ttarget_event=evt2\ttarget_unique_id=tgt_uid\tstrength=0.9\tcategory=describes";

        byte[] raw = buildMinimalMessage(
                "mem@gateway.example.com", "client@gateway.example.com",
                header, 1001, 0, payload);

        Message msg = MessageDecoder.decodeMessage(raw);

        assertNotNull(msg.event);
        assertEquals(1, msg.event.links.size());

        LinkFields link = msg.event.links.get(0);
        assertEquals("src_uid", link.uniqueIdA);
        assertEquals("tgt_uid", link.uniqueIdB);
    }
}
