package com.pointofdata.podos;

import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.MessageDecoder;
import com.pointofdata.podos.message.MessageEncoder;
import com.pointofdata.podos.message.ResponseFields;
import com.pointofdata.podos.message.SocketMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HealthTest {

    @Test
    void buildStatusHealthReply() throws Exception {
        PodOsClient client = newEmptyClient("socket-actor", "zeroth.pod-os.com");
        Message inbound = new Message();
        inbound.from = "probe-client@zeroth.pod-os.com";
        inbound.messageId = "probe-msg-1";
        inbound.intent = IntentTypes.INSTANCE.StatusRequest;

        Message reply = Health.buildStatusHealthReply(client, inbound);
        assertSame(IntentTypes.INSTANCE.Status, reply.intent);
        assertEquals("probe-msg-1", reply.messageId);
        assertEquals("probe-client@zeroth.pod-os.com", reply.to);
        assertEquals("socket-actor@zeroth.pod-os.com", reply.from);
        assertNotNull(reply.response);
        assertEquals("OK", reply.response.status);
        assertEquals("actor is healthy", reply.response.message);

        SocketMessage socketMsg = MessageEncoder.encodeMessage(reply, "conv-uuid");
        Message decoded = MessageDecoder.decodeMessage(socketMsg.messageBytes);
        assertEquals(IntentTypes.INSTANCE.Status.messageType, decoded.intent.messageType);
        assertEquals("probe-msg-1", decoded.messageId);
        assertNotNull(decoded.response);
        assertEquals("OK", decoded.response.status);
    }

    @Test
    void buildStatusHealthProbeRequest() throws Exception {
        Message msg = new Message();
        msg.to = "socket-actor@gateway.pod-os.com";
        msg.from = "dashboard@zeroth.pod-os.com";
        msg.intent = IntentTypes.INSTANCE.StatusRequest;
        msg.clientName = "dashboard";
        msg.messageId = "health-1";

        SocketMessage socketMsg = MessageEncoder.encodeMessage(msg, "conv-uuid");
        Message decoded = MessageDecoder.decodeMessage(socketMsg.messageBytes);
        assertEquals(IntentTypes.INSTANCE.StatusRequest.name, decoded.intent.name);
        assertEquals("health-1", decoded.messageId);
    }

    private static PodOsClient newEmptyClient(String clientName, String gateway) throws Exception {
        var ctor = PodOsClient.class.getDeclaredConstructor(
                com.pointofdata.podos.connection.ConnectionClient.class,
                com.pointofdata.podos.connection.ConnectionPool.class,
                Config.class,
                com.pointofdata.podos.log.PodOsLogger.class);
        ctor.setAccessible(true);
        Config cfg = new Config();
        cfg.clientName = clientName;
        cfg.gatewayActorName = gateway;
        return (PodOsClient) ctor.newInstance(null, null, cfg, com.pointofdata.podos.log.NoOpLogger.INSTANCE);
    }
}
