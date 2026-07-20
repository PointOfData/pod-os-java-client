package com.pointofdata.podos.readiness;

import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.ResponseFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealthProbe")
class HealthProbeTest {

    @Test
    @DisplayName("isNeuralMemoryBackedForHealthProbe recognizes NM types")
    void neuralMemoryTypes() {
        String[] nmTypes = {
                "neural_memory", "pod_db", "evolutionary-neural-memory", "Neural_Memory", "neural-memory"
        };
        for (String typ : nmTypes) {
            assertTrue(HealthProbe.isNeuralMemoryBackedForHealthProbe(typ), typ);
        }
        String[] nonNM = {"socket", "router", "shell", "", "gateway"};
        for (String typ : nonNM) {
            assertFalse(HealthProbe.isNeuralMemoryBackedForHealthProbe(typ), typ);
        }
    }

    @Test
    @DisplayName("buildActorHealthProbeMessage selects intent by type")
    void probeIntentByType() {
        Message socketMsg = HealthProbe.buildActorHealthProbeMessage(
                "mysocket@gateway.pod-os.com", "client@zeroth.pod-os.com", "client", "socket");
        assertEquals(IntentTypes.INSTANCE.StatusRequest.name, socketMsg.intent.name);
        assertFalse(socketMsg.messageId.isEmpty());

        Message nmMsg = HealthProbe.buildActorHealthProbeMessage(
                "account@zeroth.pod-os.com", "client@zeroth.pod-os.com", "client", "neural_memory");
        assertEquals(IntentTypes.INSTANCE.GetEventsForTags.name, nmMsg.intent.name);
        assertNotNull(nmMsg.neuralMemory);
        assertNotNull(nmMsg.neuralMemory.getEventsForTags);
        assertTrue(nmMsg.neuralMemory.getEventsForTags.countOnly);
    }

    @Test
    @DisplayName("actorHealthProbeSucceeded")
    void probeSucceeded() {
        assertTrue(HealthProbe.actorHealthProbeSucceeded(null, null));
        assertTrue(HealthProbe.actorHealthProbeSucceeded(null, new Message()));
        Message errResp = new Message();
        errResp.response = new ResponseFields();
        errResp.response.status = "ERROR";
        errResp.response.message = "fail";
        assertFalse(HealthProbe.actorHealthProbeSucceeded(null, errResp));
        assertFalse(HealthProbe.actorHealthProbeSucceeded(new Exception("transport"), null));
    }
}

@DisplayName("ReadinessGate")
class ReadinessGateTest {

    private static ActorAIPReadinessConfig fastConfig() {
        ActorAIPReadinessConfig rc = new ActorAIPReadinessConfig();
        rc.timeout = Duration.ofMillis(200);
        rc.initialBackoff = Duration.ofMillis(1);
        rc.maxBackoff = Duration.ofMillis(2);
        return rc;
    }

    @Test
    @DisplayName("waitForActorAIPReady succeeds immediately")
    void succeedsImmediately() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReadinessGate.SendFunc send = (msg, label) -> {
            calls.incrementAndGet();
            return new Message();
        };

        ReadinessGate.waitForActorAIPReady(
                send, "a@zeroth.pod-os.com", "c@zeroth.pod-os.com", "c", "socket", fastConfig());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("waitForGatewayAIPReady uses probe actor")
    void gatewayUsesProbeActor() throws Exception {
        final String[] to = new String[1];
        ReadinessGate.SendFunc send = (msg, label) -> {
            to[0] = msg.to;
            return new Message();
        };
        GatewayReadinessProbe probe = new GatewayReadinessProbe(
                "test@zeroth.pod-os.com", "neural_memory");

        ReadinessGate.waitForGatewayAIPReady(
                send, probe, "c@zeroth.pod-os.com", "c", fastConfig());
        assertEquals("test@zeroth.pod-os.com", to[0]);
    }

    @Test
    @DisplayName("waitForActorAIPReady retries then succeeds")
    void retriesThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReadinessGate.SendFunc send = (msg, label) -> {
            if (calls.incrementAndGet() < 3) {
                throw new Exception("connection to gateway was lost during request");
            }
            return new Message();
        };

        ReadinessGate.waitForActorAIPReady(
                send, "a@zeroth.pod-os.com", "c@zeroth.pod-os.com", "c",
                "evolutionary-neural-memory", fastConfig());
        assertEquals(3, calls.get());
    }

    @Test
    @DisplayName("waitForActorAIPReady deadline exceeded")
    void deadlineExceeded() {
        ReadinessGate.SendFunc send = (msg, label) -> {
            throw new Exception("connection to gateway was lost during request");
        };

        assertThrows(Exception.class, () -> ReadinessGate.waitForActorAIPReady(
                send, "a@zeroth.pod-os.com", "c@zeroth.pod-os.com", "c", "socket", fastConfig()));
    }
}
