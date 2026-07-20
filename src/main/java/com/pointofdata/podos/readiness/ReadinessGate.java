package com.pointofdata.podos.readiness;

import com.pointofdata.podos.message.Message;

import java.time.Duration;
import java.time.Instant;

/**
 * Polls until an actor or gateway answers an AIP health probe, or the budget elapses.
 */
public final class ReadinessGate {

    /** Performs one AIP probe send. Callers wire this to their client stack. */
    @FunctionalInterface
    public interface SendFunc {
        Message send(Message msg, String label) throws Exception;
    }

    private ReadinessGate() {}

    /**
     * Polls until the named actor answers an AIP health probe, or the budget elapses.
     */
    public static void waitForActorAIPReady(
            SendFunc send,
            String actorAddress,
            String fromAddress,
            String clientName,
            String actorType,
            ActorAIPReadinessConfig rc) throws Exception {
        waitForAIPReady(send, actorAddress, fromAddress, clientName, actorType, rc);
    }

    /**
     * Polls until the stable anchor actor in probe answers an AIP health probe,
     * confirming the gateway route is routable again.
     */
    public static void waitForGatewayAIPReady(
            SendFunc send,
            GatewayReadinessProbe probe,
            String fromAddress,
            String clientName,
            ActorAIPReadinessConfig rc) throws Exception {
        if (probe == null || probe.probeActor == null || probe.probeActor.isEmpty()) {
            throw new IllegalArgumentException("gateway readiness probe: probeActor is required");
        }
        waitForAIPReady(send, probe.probeActor, fromAddress, clientName, probe.probeActorType, rc);
    }

    private static void waitForAIPReady(
            SendFunc send,
            String actorAddress,
            String fromAddress,
            String clientName,
            String actorType,
            ActorAIPReadinessConfig rc) throws Exception {
        if (send == null) {
            throw new IllegalArgumentException("gateway readiness: null send function");
        }
        ActorAIPReadinessConfig cfg = rc != null ? rc.normalized() : new ActorAIPReadinessConfig().normalized();
        Duration backoff = cfg.initialBackoff;
        Instant deadline = Instant.now().plus(cfg.timeout);
        Exception lastErr = null;
        int consecutive = 0;
        int attempt = 0;

        while (Instant.now().isBefore(deadline)) {
            attempt++;
            Message probeMsg = HealthProbe.buildActorHealthProbeMessage(
                    actorAddress, fromAddress, clientName, actorType);
            Message aip = null;
            Exception sendErr = null;
            try {
                aip = send.send(probeMsg, "aip_ready_" + actorAddress);
            } catch (Exception e) {
                sendErr = e;
            }

            if (HealthProbe.actorHealthProbeSucceeded(sendErr, aip)) {
                consecutive++;
                if (consecutive >= cfg.requiredConsecutive) {
                    return;
                }
                backoff = cfg.initialBackoff;
                sleep(cfg.successInterval);
                continue;
            }

            consecutive = 0;
            if (sendErr != null) {
                lastErr = sendErr;
            } else if (aip != null) {
                lastErr = new Exception("actor returned error: " + aip.processingMessage());
            } else {
                lastErr = new Exception("probe returned no response");
            }

            sleep(backoff);
            if (backoff.compareTo(cfg.maxBackoff) < 0) {
                backoff = backoff.multipliedBy(2);
                if (backoff.compareTo(cfg.maxBackoff) > 0) {
                    backoff = cfg.maxBackoff;
                }
            }
        }

        if (lastErr == null) {
            lastErr = new Exception("deadline exceeded");
        }
        throw new Exception(
                "actor " + actorAddress + " not reachable over AIP within " + cfg.timeout + ": "
                        + lastErr.getMessage(), lastErr);
    }

    private static void sleep(Duration d) throws InterruptedException {
        if (d == null || d.isZero() || d.isNegative()) {
            return;
        }
        Thread.sleep(d.toMillis());
    }
}
