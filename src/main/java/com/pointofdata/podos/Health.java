package com.pointofdata.podos;

import com.pointofdata.podos.log.PodOsLogger;
import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.MessageEncoder;
import com.pointofdata.podos.message.ResponseFields;
import com.pointofdata.podos.message.SocketMessage;

import java.io.IOException;
import java.util.UUID;

/**
 * Actor health-check helpers for non-Neural Memory socket Actors.
 *
 * <p>Responds to inbound {@code StatusRequest} probes (message_type 110) with
 * {@code Status} replies (message_type 3) echoing the probe {@code MessageId}.
 */
public final class Health {

    private Health() {}

    /**
     * Registers an unmatched-message handler on {@code client} that replies to inbound
     * {@code StatusRequest} probes with a {@code Status} message.
     * Requires {@link com.pointofdata.podos.config.Config#enableConcurrentMode}.
     */
    public static void respondToHealthChecks(PodOsClient client) {
        if (client == null) {
            return;
        }
        client.setUnmatchedMessageHandler(inbound -> {
            if (inbound == null
                    || inbound.intent == null
                    || !IntentTypes.INSTANCE.StatusRequest.name.equals(inbound.intent.name)) {
                return;
            }
            Message reply = buildStatusHealthReply(client, inbound);
            try {
                SocketMessage socketMsg = MessageEncoder.encodeMessage(
                        reply, UUID.randomUUID().toString());
                client.sendControlMessage(socketMsg);
            } catch (IOException e) {
                PodOsLogger logger = client.getLogger();
                if (logger != null && logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                    logger.debug("health reply send failed", "error", e.getMessage());
                }
            } catch (RuntimeException e) {
                PodOsLogger logger = client.getLogger();
                if (logger != null && logger.isEnabled(PodOsLogger.Level.DEBUG)) {
                    logger.debug("health reply encode failed", "error", e.getMessage());
                }
            }
        });
    }

    /**
     * Constructs a {@code Status} response for an inbound {@code StatusRequest} probe.
     */
    public static Message buildStatusHealthReply(PodOsClient client, Message inbound) {
        String requestId = "";
        String to = "";
        if (inbound != null) {
            requestId = inbound.messageId != null ? inbound.messageId : "";
            to = inbound.from != null ? inbound.from : "";
        }
        Message reply = new Message();
        reply.to = to;
        reply.from = client.getClientName() + "@" + client.getGatewayActorName();
        reply.intent = IntentTypes.INSTANCE.Status;
        reply.clientName = client.getClientName();
        reply.messageId = requestId;
        reply.response = new ResponseFields();
        reply.response.status = "OK";
        reply.response.message = "actor is healthy";
        return reply;
    }
}
