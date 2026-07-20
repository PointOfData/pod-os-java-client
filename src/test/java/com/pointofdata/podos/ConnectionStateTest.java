package com.pointofdata.podos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConnectionState} and the state-management additions
 * in {@link PodOsClient}.
 *
 * <p>These tests exercise the enum, the state observer API, and the closed-flag
 * guard without requiring a real server connection. Reflective access is used
 * to reach private fields/methods where necessary.
 */
@DisplayName("ConnectionState")
@Timeout(10)
class ConnectionStateTest {

    // =========================================================================
    // 1. ConnectionState enum — toString labels
    // =========================================================================

    @Nested
    @DisplayName("toString labels")
    class ToStringLabels {

        @Test
        @DisplayName("CONNECTED toString is 'connected'")
        void connectedLabel() {
            assertEquals("connected", ConnectionState.CONNECTED.toString());
        }

        @Test
        @DisplayName("DISCONNECTED toString is 'disconnected'")
        void disconnectedLabel() {
            assertEquals("disconnected", ConnectionState.DISCONNECTED.toString());
        }

        @Test
        @DisplayName("RECONNECTING toString is 'reconnecting'")
        void reconnectingLabel() {
            assertEquals("reconnecting", ConnectionState.RECONNECTING.toString());
        }

        @Test
        @DisplayName("RECONNECT_FAILED toString is 'reconnect_failed'")
        void reconnectFailedLabel() {
            assertEquals("reconnect_failed", ConnectionState.RECONNECT_FAILED.toString());
        }
    }

    // =========================================================================
    // 2. ConnectionState enum — values completeness
    // =========================================================================

    @Test
    @DisplayName("enum has exactly 4 values")
    void enumHasFourValues() {
        assertEquals(4, ConnectionState.values().length);
    }

    @Test
    @DisplayName("valueOf round-trips for all constants")
    void valueOfRoundTrips() {
        for (ConnectionState cs : ConnectionState.values()) {
            assertSame(cs, ConnectionState.valueOf(cs.name()));
        }
    }

    // =========================================================================
    // 3. emitState with no handler — must not NPE
    // =========================================================================

    @Test
    @DisplayName("emitState with no handler does not throw")
    void emitStateNoHandler() throws Exception {
        PodOsClient client = createBareClient();
        Method emitState = PodOsClient.class.getDeclaredMethod("emitState", ConnectionState.class, Exception.class);
        emitState.setAccessible(true);

        assertDoesNotThrow(() ->
                emitState.invoke(client, ConnectionState.DISCONNECTED, new IOException("test")));
        assertDoesNotThrow(() ->
                emitState.invoke(client, ConnectionState.CONNECTED, null));
    }

    // =========================================================================
    // 4. onConnectionStateChange receives correct states and errors
    // =========================================================================

    @Test
    @DisplayName("onConnectionStateChange receives state and error via emitState")
    void stateHandlerReceivesEvents() throws Exception {
        PodOsClient client = createBareClient();

        List<ConnectionState> states = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        client.onConnectionStateChange((state, err) -> {
            states.add(state);
            errors.add(err);
        });

        Method emitState = PodOsClient.class.getDeclaredMethod("emitState", ConnectionState.class, Exception.class);
        emitState.setAccessible(true);

        IOException cause = new IOException("link down");
        emitState.invoke(client, ConnectionState.DISCONNECTED, cause);
        emitState.invoke(client, ConnectionState.RECONNECTING, cause);
        emitState.invoke(client, ConnectionState.CONNECTED, null);

        assertEquals(3, states.size());
        assertEquals(ConnectionState.DISCONNECTED, states.get(0));
        assertEquals(ConnectionState.RECONNECTING, states.get(1));
        assertEquals(ConnectionState.CONNECTED, states.get(2));

        assertSame(cause, errors.get(0));
        assertSame(cause, errors.get(1));
        assertNull(errors.get(2));
    }

    // =========================================================================
    // 5. Handler replacement — second handler replaces first
    // =========================================================================

    @Test
    @DisplayName("second onConnectionStateChange replaces first handler")
    void handlerReplacement() throws Exception {
        PodOsClient client = createBareClient();

        AtomicReference<String> calledBy = new AtomicReference<>("none");
        client.onConnectionStateChange((s, e) -> calledBy.set("first"));
        client.onConnectionStateChange((s, e) -> calledBy.set("second"));

        Method emitState = PodOsClient.class.getDeclaredMethod("emitState", ConnectionState.class, Exception.class);
        emitState.setAccessible(true);
        emitState.invoke(client, ConnectionState.CONNECTED, null);

        assertEquals("second", calledBy.get());
    }

    // =========================================================================
    // 6. closed flag — attemptReconnection returns false after close
    // =========================================================================

    @Test
    @DisplayName("attemptReconnection returns false after close()")
    void reconnectionBlockedAfterClose() throws Exception {
        PodOsClient client = createBareClient();

        // Set closed flag via reflection (close() would also try to close the null conn)
        Field closedField = PodOsClient.class.getDeclaredField("closed");
        closedField.setAccessible(true);
        ((AtomicBoolean) closedField.get(client)).set(true);

        assertFalse(client.attemptReconnection());
    }

    // =========================================================================
    // 7. isClosed accessor
    // =========================================================================

    @Test
    @DisplayName("isClosed returns false initially and true after closed flag is set")
    void isClosedAccessor() throws Exception {
        PodOsClient client = createBareClient();
        assertFalse(client.isClosed());

        Field closedField = PodOsClient.class.getDeclaredField("closed");
        closedField.setAccessible(true);
        ((AtomicBoolean) closedField.get(client)).set(true);

        assertTrue(client.isClosed());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a PodOsClient instance via reflection without establishing
     * a real connection. The conn field will be null, but that's fine for
     * testing state management APIs.
     */
    private static PodOsClient createBareClient() throws Exception {
        var ctor = PodOsClient.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);

        var cfg = new com.pointofdata.podos.config.Config();
        cfg.clientName = "test-client";
        cfg.gatewayActorName = "test-gateway";

        var logger = com.pointofdata.podos.log.NoOpLogger.INSTANCE;

        return (PodOsClient) ctor.newInstance(null, cfg, logger);
    }
}
