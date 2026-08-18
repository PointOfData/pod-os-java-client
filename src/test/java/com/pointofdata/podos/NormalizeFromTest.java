package com.pointofdata.podos;

import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.log.NoOpLogger;
import com.pointofdata.podos.message.IntentType;
import com.pointofdata.podos.message.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Envelope from-address must always use the connection gateway (Go SDK parity). */
class NormalizeFromTest {

    @Test
    void fromAddressUsesConnectionGateway() throws Exception {
        PodOsClient client = testClient("my-client", "zeroth.pod-os.com");
        assertEquals("my-client@zeroth.pod-os.com", client.fromAddress());
    }

    @Test
    void normalizeMessageFromUsesConnectionGatewayNotCallerDomain() throws Exception {
        PodOsClient client = testClient("my-client", "zeroth.pod-os.com");
        Message msg = new Message();
        msg.to = "kb@skills.pod-os.com";
        msg.from = "other@skills.pod-os.com";
        msg.clientName = "other";
        msg.intent = IntentType.GetEvent;

        invokeNormalize(client, msg);

        assertEquals("my-client", msg.clientName);
        assertEquals("my-client@zeroth.pod-os.com", msg.from);
    }

    @Test
    void normalizeMessageFromFillsEmptyFrom() throws Exception {
        PodOsClient client = testClient("my-client", "skills.pod-os.com");
        Message msg = new Message();
        msg.to = "kb@skills.pod-os.com";
        msg.intent = IntentType.GetEvent;

        invokeNormalize(client, msg);

        assertEquals("my-client@skills.pod-os.com", msg.from);
    }

    private static PodOsClient testClient(String clientName, String gateway) throws Exception {
        Config cfg = new Config();
        cfg.clientName = clientName;
        cfg.gatewayActorName = gateway;

        Constructor<PodOsClient> ctor = PodOsClient.class.getDeclaredConstructor(
                com.pointofdata.podos.connection.ConnectionClient.class,
                com.pointofdata.podos.connection.ConnectionPool.class,
                Config.class,
                com.pointofdata.podos.log.PodOsLogger.class);
        ctor.setAccessible(true);
        return ctor.newInstance(null, null, cfg, NoOpLogger.INSTANCE);
    }

    private static void invokeNormalize(PodOsClient client, Message msg) throws Exception {
        Method m = PodOsClient.class.getDeclaredMethod("normalizeMessage", Message.class);
        m.setAccessible(true);
        m.invoke(client, msg);
    }
}
