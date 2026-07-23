package com.pointofdata.podos;

import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.MessageEncoder;
import com.pointofdata.podos.message.SocketMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class KeepaliveTest {

    @Test
    void configDefaultKeepaliveInterval() {
        Config cfg = new Config();
        assertEquals(Duration.ofSeconds(30), cfg.getKeepaliveInterval());
    }

    @Test
    void configDisabledKeepaliveInterval() {
        Config cfg = new Config();
        cfg.keepaliveInterval = Duration.ZERO;
        assertEquals(Duration.ZERO, cfg.getKeepaliveInterval());
    }

    @Test
    void configDefaultConnectionLivenessTimeout() {
        Config cfg = new Config();
        assertEquals(Duration.ofSeconds(90), cfg.getConnectionLivenessTimeout());
    }

    @Test
    void configCustomConnectionLivenessTimeout() {
        Config cfg = new Config();
        cfg.connectionLivenessTimeout = Duration.ofSeconds(15);
        assertEquals(Duration.ofSeconds(15), cfg.getConnectionLivenessTimeout());
    }

    @Test
    void configDisabledConnectionLivenessTimeout() {
        Config cfg = new Config();
        cfg.connectionLivenessTimeout = Duration.ofSeconds(-1);
        assertEquals(Duration.ZERO, cfg.getConnectionLivenessTimeout());
    }

    @Test
    void buildKeepaliveMessageUsesIntent18() throws Exception {
        PodOsClient client = newEmptyClient("my-client", "zeroth.pod-os.com");
        Method build = PodOsClient.class.getDeclaredMethod("buildKeepaliveMessage");
        build.setAccessible(true);
        Message msg = (Message) build.invoke(client);

        assertEquals("$system@zeroth.pod-os.com", msg.to);
        assertEquals("my-client@zeroth.pod-os.com", msg.from);
        assertSame(IntentTypes.INSTANCE.Keepalive, msg.intent);
        assertEquals(18, msg.intent.messageType);
        assertNull(msg.event);
        assertNull(msg.payload);
        assertNull(msg.neuralMemory);
    }

    @Test
    void encodeKeepaliveProducesWireBytes() throws Exception {
        PodOsClient client = newEmptyClient("my-client", "zeroth.pod-os.com");
        Method encode = PodOsClient.class.getDeclaredMethod("encodeKeepalive");
        encode.setAccessible(true);
        SocketMessage socket = (SocketMessage) encode.invoke(client);
        assertNotNull(socket.messageBytes);
        assertTrue(socket.messageBytes.length > 63);
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
