package com.pointofdata.podos;

import com.pointofdata.podos.config.Config;
import com.pointofdata.podos.connection.ConnectionClient;
import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.SocketMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DisconnectTest {

    @Test
    void buildDisconnectMessageUsesIntent6() throws Exception {
        PodOsClient client = newEmptyClient("my-client", "zeroth.pod-os.com");
        Method build = PodOsClient.class.getDeclaredMethod("buildDisconnectMessage");
        build.setAccessible(true);
        Message msg = (Message) build.invoke(client);

        assertEquals("$system@zeroth.pod-os.com", msg.to);
        assertEquals("my-client@zeroth.pod-os.com", msg.from);
        assertSame(IntentTypes.INSTANCE.GatewayDisconnect, msg.intent);
        assertEquals(6, msg.intent.messageType);
        assertNull(msg.event);
        assertNull(msg.payload);
        assertNull(msg.neuralMemory);
    }

    @Test
    void encodeDisconnectProducesWireBytesWithType6() throws Exception {
        PodOsClient client = newEmptyClient("my-client", "zeroth.pod-os.com");
        Method encode = PodOsClient.class.getDeclaredMethod("encodeDisconnect");
        encode.setAccessible(true);
        SocketMessage socket = (SocketMessage) encode.invoke(client);
        assertNotNull(socket.messageBytes);
        assertTrue(new String(socket.messageBytes, StandardCharsets.US_ASCII).contains("000000006"));
    }

    @Test
    void closeSendsDisconnectBeforeTCP() throws Exception {
        AtomicReference<Socket> accepted = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try {
                    accepted.set(server.accept());
                } catch (Exception ignored) {}
            }, "disconnect-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            ConnectionClient conn = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(server.getLocalPort()))
                    .dialTimeout(Duration.ofMillis(500))
                    .build();
            conn.connect();

            PodOsClient client = newClientWithConn(conn, "close-test-client", "zeroth.pod-os.com");
            Socket serverSocket = accepted.get();
            for (int i = 0; i < 20 && serverSocket == null; i++) {
                Thread.sleep(50);
                serverSocket = accepted.get();
            }
            assertNotNull(serverSocket);

            byte[] buf = new byte[4096];
            Thread reader = new Thread(() -> {
                try (InputStream in = serverSocket.getInputStream()) {
                    in.read(buf);
                } catch (Exception ignored) {}
            }, "disconnect-reader");
            reader.start();

            client.close();

            reader.join(2000);
            String got = new String(buf, StandardCharsets.US_ASCII).trim();
            assertTrue(got.contains("000000006"),
                    "server did not receive GatewayDisconnect frame; got=" + got);
        }
    }

    private static PodOsClient newEmptyClient(String clientName, String gateway) throws Exception {
        return newClientWithConn(null, clientName, gateway);
    }

    private static PodOsClient newClientWithConn(ConnectionClient conn, String clientName, String gateway)
            throws Exception {
        Constructor<PodOsClient> ctor = PodOsClient.class.getDeclaredConstructor(
                ConnectionClient.class,
                com.pointofdata.podos.connection.ConnectionPool.class,
                Config.class,
                com.pointofdata.podos.log.PodOsLogger.class);
        ctor.setAccessible(true);
        Config cfg = new Config();
        cfg.clientName = clientName;
        cfg.gatewayActorName = gateway;
        return ctor.newInstance(conn, null, cfg, com.pointofdata.podos.log.NoOpLogger.INSTANCE);
    }
}
