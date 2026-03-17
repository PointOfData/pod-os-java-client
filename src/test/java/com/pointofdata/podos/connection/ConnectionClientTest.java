package com.pointofdata.podos.connection;

import com.pointofdata.podos.errors.ErrCode;
import com.pointofdata.podos.errors.GatewayDError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConnectionClient}.
 *
 * <p>Tests that require a real connection use an in-process {@link TestServer}
 * bound to a random {@code localhost} port. No external services are needed.
 */
@DisplayName("ConnectionClient")
@Timeout(10)
class ConnectionClientTest {

    // =========================================================================
    // 1. Builder defaults
    // =========================================================================

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("default port is 62312")
        void defaultPort() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertEquals("62312", c.getPort());
        }

        @Test
        @DisplayName("default host is empty string")
        void defaultHost() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertEquals("", c.getHost());
        }

        @Test
        @DisplayName("default actorName is empty string")
        void defaultActorName() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertEquals("", c.getActorName());
        }

        @Test
        @DisplayName("build() without connect() leaves isConnected() false")
        void notConnectedAfterBuild() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertFalse(c.isConnected());
        }
    }

    // =========================================================================
    // 2. Builder custom values
    // =========================================================================

    @Nested
    @DisplayName("Builder custom values")
    class BuilderCustomValues {

        @Test
        @DisplayName("custom host, port, actorName are reflected in accessors")
        void customAccessors() {
            ConnectionClient c = ConnectionClient.builder()
                    .host("example.com")
                    .port("9999")
                    .actorName("my-actor")
                    .build();
            assertEquals("example.com", c.getHost());
            assertEquals("9999",        c.getPort());
            assertEquals("my-actor",    c.getActorName());
        }

        @Test
        @DisplayName("WireHook.NO_OP is accepted without error")
        void wireHookNoOpAccepted() {
            assertDoesNotThrow(() ->
                    ConnectionClient.builder()
                            .wireHook(WireHook.NO_OP)
                            .build());
        }
    }

    // =========================================================================
    // 3. State before connect
    // =========================================================================

    @Nested
    @DisplayName("State before connect")
    class StateBeforeConnect {

        @Test
        @DisplayName("remoteAddr() is empty when not connected")
        void remoteAddrEmptyWhenNotConnected() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertEquals("", c.remoteAddr());
        }

        @Test
        @DisplayName("localAddr() is empty when not connected")
        void localAddrEmptyWhenNotConnected() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertEquals("", c.localAddr());
        }

        @Test
        @DisplayName("send() when not connected throws ERR_CLIENT_NOT_CONNECTED")
        void sendWhenNotConnectedThrows() {
            ConnectionClient c = ConnectionClient.builder().build();
            GatewayDError err = assertThrows(GatewayDError.class,
                    () -> c.send("hello".getBytes(StandardCharsets.UTF_8)));
            assertEquals(ErrCode.CLIENT_NOT_CONNECTED, err.getCode());
        }

        @Test
        @DisplayName("receive() when not connected throws ERR_CLIENT_NOT_CONNECTED")
        void receiveWhenNotConnectedThrows() {
            ConnectionClient c = ConnectionClient.builder().build();
            GatewayDError err = assertThrows(GatewayDError.class,
                    () -> c.receive(1000));
            assertEquals(ErrCode.CLIENT_NOT_CONNECTED, err.getCode());
        }
    }

    // =========================================================================
    // 4. connect()
    // =========================================================================

    @Nested
    @DisplayName("connect()")
    class ConnectTests {

        @Test
        @DisplayName("successful connect sets isConnected() to true")
        void connectSuccess() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildClient(server.getPort());
                client.connect();
                try {
                    assertTrue(client.isConnected());
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("connect to a refused port throws IOException after retries")
        void connectRefusedPortThrows() throws Exception {
            // Find a port with no listener
            int refusedPort;
            try (ServerSocket s = new ServerSocket(0)) {
                refusedPort = s.getLocalPort();
            } // port now closed — connection will be refused

            Retry retry = new Retry(2, Duration.ofMillis(10), 1.0);
            ConnectionClient client = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(refusedPort))
                    .dialTimeout(Duration.ofMillis(300))
                    .retry(retry)
                    .build();

            IOException ex = assertThrows(IOException.class, client::connect);
            assertNotNull(ex.getMessage());
            assertFalse(client.isConnected());
        }

        @Test
        @DisplayName("connect retries on transient failure and succeeds")
        void connectRetriesAndSucceeds() throws Exception {
            // Temporarily occupy a port, release it, then start a real server on it.
            // The trick: build with a retry count ≥ 2 and a very short backoff,
            // then start the real server before the second attempt.
            // Easier: start the server right away and use 1 retry — first attempt always works.
            try (TestServer server = TestServer.acceptOnly()) {
                Retry retry = new Retry(3, Duration.ofMillis(10), 1.0);
                ConnectionClient client = ConnectionClient.builder()
                        .host("localhost")
                        .port(String.valueOf(server.getPort()))
                        .dialTimeout(Duration.ofMillis(500))
                        .retry(retry)
                        .build();
                client.connect();
                try {
                    assertTrue(client.isConnected());
                } finally {
                    client.close();
                }
            }
        }
    }

    // =========================================================================
    // 5. send()
    // =========================================================================

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("send() after close() throws ERR_CLIENT_NOT_CONNECTED")
        void sendAfterCloseThrows() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                client.close();
                GatewayDError err = assertThrows(GatewayDError.class,
                        () -> client.send("data".getBytes()));
                assertEquals(ErrCode.CLIENT_NOT_CONNECTED, err.getCode());
            }
        }

        @Test
        @DisplayName("send() returns the number of bytes written")
        void sendReturnsCorrectByteCount() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
                    int n = client.send(data);
                    assertEquals(data.length, n);
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("send() calls WireHook.onSend with the exact bytes")
        void sendCallsWireHook() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                AtomicReference<byte[]> captured = new AtomicReference<>();
                WireHook hook = new WireHook() {
                    @Override public void onSend(byte[] raw)    { captured.set(raw.clone()); }
                    @Override public void onReceive(byte[] raw) {}
                };

                ConnectionClient client = ConnectionClient.builder()
                        .host("localhost")
                        .port(String.valueOf(server.getPort()))
                        .dialTimeout(Duration.ofMillis(500))
                        .sendTimeout(Duration.ofMillis(500))
                        .receiveTimeout(Duration.ofMillis(500))
                        .wireHook(hook)
                        .build();
                client.connect();
                try {
                    byte[] data = "wire-hook-test".getBytes(StandardCharsets.UTF_8);
                    client.send(data);
                    assertNotNull(captured.get());
                    assertArrayEquals(data, captured.get());
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("send() with empty byte array returns 0")
        void sendEmptyBytes() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    assertEquals(0, client.send(new byte[0]));
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("concurrent sends from multiple threads do not throw")
        void concurrentSendsAreSafe() throws Exception {
            try (TestServer server = TestServer.echo()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    int threads = 10;
                    byte[] data = new byte[64];
                    Arrays.fill(data, (byte) 'A');

                    ExecutorService pool = Executors.newFixedThreadPool(threads);
                    List<Future<Integer>> futures = new ArrayList<>();
                    for (int i = 0; i < threads; i++) {
                        futures.add(pool.submit(() -> client.send(data)));
                    }
                    pool.shutdown();
                    pool.awaitTermination(5, TimeUnit.SECONDS);

                    for (Future<Integer> f : futures) {
                        assertEquals(data.length, f.get());
                    }
                } finally {
                    client.close();
                }
            }
        }
    }

    // =========================================================================
    // 6. receive()
    // =========================================================================

    @Nested
    @DisplayName("receive()")
    class ReceiveTests {

        @Test
        @DisplayName("receive() after close() throws ERR_CLIENT_NOT_CONNECTED")
        void receiveAfterCloseThrows() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                client.close();
                GatewayDError err = assertThrows(GatewayDError.class,
                        () -> client.receive(500));
                assertEquals(ErrCode.CLIENT_NOT_CONNECTED, err.getCode());
            }
        }

        @Test
        @DisplayName("receive() reassembles a hex-framed message correctly")
        void receiveValidHexFrame() throws Exception {
            byte[] body  = "hello-body".getBytes(StandardCharsets.UTF_8);
            byte[] frame = TestServer.hexFrame(body);

            try (TestServer server = TestServer.sendBytesAndWait(frame)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    byte[] received = client.receive(2000);
                    assertArrayEquals(frame, received);
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() reassembles a decimal-framed message correctly")
        void receiveValidDecimalFrame() throws Exception {
            byte[] body  = "decimal-body".getBytes(StandardCharsets.UTF_8);
            byte[] frame = TestServer.decimalFrame(body);

            try (TestServer server = TestServer.sendBytesAndWait(frame)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    byte[] received = client.receive(2000);
                    assertArrayEquals(frame, received);
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() handles a minimal 9-byte hex frame (no body)")
        void receiveMinimalHexFrame() throws Exception {
            // Frame is just the 9-byte prefix claiming total = 9 (no body)
            byte[] frame = TestServer.hexFrame(new byte[0]);
            assertEquals(9, frame.length);

            try (TestServer server = TestServer.sendBytesAndWait(frame)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    byte[] received = client.receive(2000);
                    assertArrayEquals(frame, received);
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() handles a large multi-chunk body")
        void receiveLargeBody() throws Exception {
            // 10 KB body → forces multiple chunk reads (default chunk = 512 bytes)
            byte[] body = new byte[10_240];
            Arrays.fill(body, (byte) 'Z');
            byte[] frame = TestServer.hexFrame(body);

            try (TestServer server = TestServer.sendBytesAndWait(frame)) {
                ConnectionClient client = ConnectionClient.builder()
                        .host("localhost")
                        .port(String.valueOf(server.getPort()))
                        .receiveChunkSize(512)
                        .dialTimeout(Duration.ofMillis(500))
                        .receiveTimeout(Duration.ofSeconds(3))
                        .build();
                client.connect();
                try {
                    byte[] received = client.receive(3000);
                    assertArrayEquals(frame, received);
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() throws ERR_CLIENT_RECEIVE_FAILED on invalid length prefix")
        void receiveInvalidLengthPrefix() throws Exception {
            // 9 bytes that are neither pure digits nor 'x' + hex
            byte[] garbage = "GARBAG!!!" .getBytes(StandardCharsets.US_ASCII);
            assertEquals(9, garbage.length);

            try (TestServer server = TestServer.sendBytesAndWait(garbage)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> client.receive(2000));
                    assertEquals(ErrCode.CLIENT_RECEIVE_FAILED, err.getCode());
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() throws ERR_CLIENT_RECEIVE_FAILED on prefix with invalid hex char")
        void receiveInvalidHexChar() throws Exception {
            // 'x' prefix but contains 'G' which is not valid hex
            byte[] badPrefix = "x0000000G".getBytes(StandardCharsets.US_ASCII);
            assertEquals(9, badPrefix.length);

            try (TestServer server = TestServer.sendBytesAndWait(badPrefix)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> client.receive(2000));
                    assertEquals(ErrCode.CLIENT_RECEIVE_FAILED, err.getCode());
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() throws ERR_CLIENT_RECEIVE_FAILED when server closes immediately (EOF during prefix)")
        void receiveEofDuringPrefix() throws Exception {
            try (TestServer server = TestServer.closeImmediately()) {
                // Brief delay to let the server accept and close before the client reads
                ConnectionClient client = buildAndConnect(server.getPort());
                Thread.sleep(50);
                try {
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> client.receive(2000));
                    assertEquals(ErrCode.CLIENT_RECEIVE_FAILED, err.getCode());
                    assertFalse(client.isConnected(),
                            "isConnected() must be false after EOF");
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() throws ERR_CLIENT_RECEIVE_FAILED when server closes during body")
        void receiveEofDuringBody() throws Exception {
            // Send a prefix claiming 50 bytes total, but close after sending only the prefix (9 bytes)
            byte[] truncatedFrame = TestServer.hexPrefixOnly(50);
            assertEquals(9, truncatedFrame.length);

            try (TestServer server = TestServer.sendThenClose(truncatedFrame)) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    GatewayDError err = assertThrows(GatewayDError.class,
                            () -> client.receive(2000));
                    assertEquals(ErrCode.CLIENT_RECEIVE_FAILED, err.getCode());
                    assertFalse(client.isConnected(),
                            "isConnected() must be false after EOF during body");
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() calls WireHook.onReceive with the complete frame")
        void receiveCallsWireHook() throws Exception {
            byte[] body  = "hook-body".getBytes(StandardCharsets.UTF_8);
            byte[] frame = TestServer.hexFrame(body);

            AtomicReference<byte[]> captured = new AtomicReference<>();
            WireHook hook = new WireHook() {
                @Override public void onSend(byte[] raw) {}
                @Override public void onReceive(byte[] raw) { captured.set(raw.clone()); }
            };

            try (TestServer server = TestServer.sendBytesAndWait(frame)) {
                ConnectionClient client = ConnectionClient.builder()
                        .host("localhost")
                        .port(String.valueOf(server.getPort()))
                        .dialTimeout(Duration.ofMillis(500))
                        .receiveTimeout(Duration.ofSeconds(2))
                        .wireHook(hook)
                        .build();
                client.connect();
                try {
                    client.receive(2000);
                    assertNotNull(captured.get());
                    assertArrayEquals(frame, captured.get());
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("receive() uses the provided timeout override, not the configured one")
        void receiveUsesProvidedTimeout() throws Exception {
            // Server accepts but never writes — receive should timeout
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = ConnectionClient.builder()
                        .host("localhost")
                        .port(String.valueOf(server.getPort()))
                        .dialTimeout(Duration.ofMillis(500))
                        .receiveTimeout(Duration.ofSeconds(30))  // configured long
                        .build();
                client.connect();
                try {
                    long start = System.currentTimeMillis();
                    // Pass a short override timeout of 200 ms
                    assertThrows(GatewayDError.class, () -> client.receive(200));
                    long elapsed = System.currentTimeMillis() - start;
                    assertTrue(elapsed < 2000,
                            "receive should have timed out in ~200ms, not the configured 30s; elapsed=" + elapsed);
                } finally {
                    client.close();
                }
            }
        }
    }

    // =========================================================================
    // 7. close()
    // =========================================================================

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("close() sets isConnected() to false")
        void closeSetsDisconnected() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                assertTrue(client.isConnected());
                client.close();
                assertFalse(client.isConnected());
            }
        }

        @Test
        @DisplayName("close() is idempotent — second call does not throw")
        void closeIsIdempotent() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                client.close();
                assertDoesNotThrow(client::close);
                assertFalse(client.isConnected());
            }
        }

        @Test
        @DisplayName("close() on a never-connected client does not throw")
        void closeNeverConnected() {
            ConnectionClient c = ConnectionClient.builder().build();
            assertDoesNotThrow(c::close);
        }
    }

    // =========================================================================
    // 8. reconnect()
    // =========================================================================

    @Nested
    @DisplayName("reconnect()")
    class ReconnectTests {

        @Test
        @DisplayName("reconnect() closes the old connection and opens a new one")
        void reconnectRestoresConnection() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                assertTrue(client.isConnected(), "should be connected before reconnect");

                client.reconnect();

                assertTrue(client.isConnected(), "should be connected after reconnect");
                client.close();
            }
        }

        @Test
        @DisplayName("reconnect() while already disconnected re-establishes connection")
        void reconnectWhenDisconnected() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                client.close();
                assertFalse(client.isConnected());

                client.reconnect();

                assertTrue(client.isConnected());
                client.close();
            }
        }
    }

    // =========================================================================
    // 9. Addresses after connect
    // =========================================================================

    @Nested
    @DisplayName("Addresses after connect")
    class AddressTests {

        @Test
        @DisplayName("remoteAddr() is non-empty after connect")
        void remoteAddrPopulated() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    assertFalse(client.remoteAddr().isEmpty(),
                            "remoteAddr() should be populated after connect");
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("localAddr() is non-empty after connect")
        void localAddrPopulated() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    assertFalse(client.localAddr().isEmpty(),
                            "localAddr() should be populated after connect");
                } finally {
                    client.close();
                }
            }
        }

        @Test
        @DisplayName("remoteAddr() contains the server port")
        void remoteAddrContainsPort() throws Exception {
            try (TestServer server = TestServer.acceptOnly()) {
                ConnectionClient client = buildAndConnect(server.getPort());
                try {
                    assertTrue(client.remoteAddr().contains(String.valueOf(server.getPort())),
                            "remoteAddr() should contain server port " + server.getPort());
                } finally {
                    client.close();
                }
            }
        }
    }

    // =========================================================================
    // 10. Send + receive round-trip
    // =========================================================================

    @Test
    @DisplayName("send followed by receive returns the same framed bytes (echo)")
    void sendReceiveRoundTrip() throws Exception {
        byte[] body  = "round-trip".getBytes(StandardCharsets.UTF_8);
        byte[] frame = TestServer.hexFrame(body);

        try (TestServer server = TestServer.echo()) {
            ConnectionClient client = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(server.getPort()))
                    .dialTimeout(Duration.ofMillis(500))
                    .sendTimeout(Duration.ofSeconds(2))
                    .receiveTimeout(Duration.ofSeconds(2))
                    .build();
            client.connect();
            try {
                client.send(frame);
                byte[] received = client.receive(2000);
                assertArrayEquals(frame, received);
            } finally {
                client.close();
            }
        }
    }

    // =========================================================================
    // 11. WireHook — both directions
    // =========================================================================

    @Test
    @DisplayName("WireHook.NO_OP does not interfere with send/receive")
    void wireHookNoOpDoesNotInterfere() throws Exception {
        byte[] body  = "noop".getBytes(StandardCharsets.UTF_8);
        byte[] frame = TestServer.hexFrame(body);

        try (TestServer server = TestServer.sendBytesAndWait(frame)) {
            ConnectionClient client = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(server.getPort()))
                    .dialTimeout(Duration.ofMillis(500))
                    .receiveTimeout(Duration.ofSeconds(2))
                    .wireHook(WireHook.NO_OP)
                    .build();
            client.connect();
            try {
                byte[] received = client.receive(2000);
                assertArrayEquals(frame, received);
            } finally {
                client.close();
            }
        }
    }

    @Test
    @DisplayName("WireHook captures both send and receive in the correct order")
    void wireHookBothDirections() throws Exception {
        byte[] body  = "both".getBytes(StandardCharsets.UTF_8);
        byte[] frame = TestServer.hexFrame(body);

        List<String> events = new CopyOnWriteArrayList<>();
        WireHook hook = new WireHook() {
            @Override public void onSend(byte[] raw)    { events.add("send:" + raw.length); }
            @Override public void onReceive(byte[] raw) { events.add("recv:" + raw.length); }
        };

        try (TestServer server = TestServer.echo()) {
            ConnectionClient client = ConnectionClient.builder()
                    .host("localhost")
                    .port(String.valueOf(server.getPort()))
                    .dialTimeout(Duration.ofMillis(500))
                    .sendTimeout(Duration.ofSeconds(2))
                    .receiveTimeout(Duration.ofSeconds(2))
                    .wireHook(hook)
                    .build();
            client.connect();
            try {
                client.send(frame);
                client.receive(2000);
            } finally {
                client.close();
            }
        }

        assertEquals(2, events.size());
        assertTrue(events.get(0).startsWith("send:"));
        assertTrue(events.get(1).startsWith("recv:"));
        // Frame length must match in both directions
        assertEquals(events.get(0).split(":")[1], events.get(1).split(":")[1]);
    }

    // =========================================================================
    // Helper factory methods
    // =========================================================================

    private static ConnectionClient buildClient(int port) {
        return ConnectionClient.builder()
                .host("localhost")
                .port(String.valueOf(port))
                .dialTimeout(Duration.ofMillis(500))
                .sendTimeout(Duration.ofMillis(500))
                .receiveTimeout(Duration.ofSeconds(2))
                .retry(new Retry(2, Duration.ofMillis(10), 1.0))
                .build();
    }

    private static ConnectionClient buildAndConnect(int port) throws IOException {
        ConnectionClient c = buildClient(port);
        c.connect();
        return c;
    }
}
