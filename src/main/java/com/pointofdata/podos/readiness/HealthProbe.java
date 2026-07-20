package com.pointofdata.podos.readiness;

import com.pointofdata.podos.message.GetEventsForTagsOptions;
import com.pointofdata.podos.message.IntentTypes;
import com.pointofdata.podos.message.Message;
import com.pointofdata.podos.message.NeuralMemoryFields;
import com.pointofdata.podos.message.PayloadFields;

import java.util.Locale;
import java.util.UUID;

/**
 * Constructs AIP health probe messages and evaluates probe outcomes.
 */
public final class HealthProbe {

    private HealthProbe() {}

    /**
     * Reports whether an actor type runs pod_aip_db and can answer Neural-Memory intents
     * such as GetEventsForTags.
     */
    public static boolean isNeuralMemoryBackedForHealthProbe(String actorType) {
        if (actorType == null) {
            return false;
        }
        switch (actorType.toLowerCase(Locale.ROOT).trim()) {
            case "pod_db":
            case "evolutionary-neural-memory":
            case "neural_memory":
            case "neural-memory":
                return true;
            default:
                return false;
        }
    }

    /**
     * Constructs the AIP health probe for one actor based on type.
     * NM-backed actors use GetEventsForTags (CountOnly); socket/shell and other types
     * use StatusRequest.
     */
    public static Message buildActorHealthProbeMessage(
            String actorAddress, String fromAddress, String clientName, String actorType) {
        String messageId = UUID.randomUUID().toString();

        if (isNeuralMemoryBackedForHealthProbe(actorType)) {
            String healthCheckTag = "_podos_health_check_" + UUID.randomUUID();
            String searchClause = "health_check=" + healthCheckTag;

            Message msg = new Message();
            msg.to = actorAddress;
            msg.from = fromAddress;
            msg.intent = IntentTypes.INSTANCE.GetEventsForTags;
            msg.clientName = clientName;
            msg.messageId = messageId;
            msg.payload = new PayloadFields();
            msg.payload.data = searchClause;
            msg.neuralMemory = new NeuralMemoryFields();
            msg.neuralMemory.getEventsForTags = new GetEventsForTagsOptions();
            msg.neuralMemory.getEventsForTags.countOnly = true;
            return msg;
        }

        Message msg = new Message();
        msg.to = actorAddress;
        msg.from = fromAddress;
        msg.intent = IntentTypes.INSTANCE.StatusRequest;
        msg.clientName = clientName;
        msg.messageId = messageId;
        return msg;
    }

    /**
     * Reports whether a health probe transport and AIP status indicate success.
     */
    public static boolean actorHealthProbeSucceeded(Exception err, Message resp) {
        if (err != null) {
            return false;
        }
        return resp == null || !"ERROR".equals(resp.processingStatus());
    }
}
