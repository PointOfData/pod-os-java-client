package com.pointofdata.podos.message;

/**
 * Represents a Pod-OS message intent type.
 * Each intent defines how a message is routed and what operation is performed.
 */
public final class Intent {

    /** Friendly Pod-OS name for the intent. */
    public final String name;

    /** Defines message_type name for routing message functions. */
    public final String routingMessageType;

    /** Defines the command to send to the Evolutionary Neural Memory Actor. Empty for non-NMD intents. */
    public final String neuralMemoryCommand;

    /** The message type integer set in the message header. */
    public final int messageType;

    public Intent(String name, String routingMessageType, String neuralMemoryCommand, int messageType) {
        this.name = name;
        this.routingMessageType = routingMessageType;
        this.neuralMemoryCommand = neuralMemoryCommand != null ? neuralMemoryCommand : "";
        this.messageType = messageType;
    }

    public Intent(String name, String routingMessageType, int messageType) {
        this(name, routingMessageType, "", messageType);
    }

    @Override
    public String toString() {
        return "Intent{name='" + name + "', messageType=" + messageType + ", routingMessageType='" + routingMessageType + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Intent)) return false;
        Intent intent = (Intent) o;
        return messageType == intent.messageType
                && name.equals(intent.name)
                && neuralMemoryCommand.equals(intent.neuralMemoryCommand);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + messageType;
    }

    /** Returns true if this intent has an empty name (unset). */
    public boolean isEmpty() {
        return name == null || name.isEmpty();
    }
}
