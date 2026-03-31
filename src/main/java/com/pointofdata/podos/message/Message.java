package com.pointofdata.podos.message;

/**
 * Represents a Pod-OS Actor message for both sending and receiving.
 *
 * <p><b>Sending a message:</b> Populate {@link #to}, {@link #from}, {@link #intent},
 * {@link #clientName}, optionally {@link #event}, {@link #neuralMemory}, and
 * {@link #payload}.
 *
 * <p><b>Processing a response:</b> After {@link MessageDecoder#decodeMessage(byte[])}
 * returns, read from {@link #response}, {@link #event}, and {@link #payload}.
 *
 * <p>The {@link Envelope} fields are promoted to top-level for convenient access,
 * matching the Go client's embedded struct pattern.
 */
public class Message extends Envelope {

    /** Event metadata (null for non-event operations). */
    public EventFields event;

    /** Payload data (null if no payload). Used to send data to the Actor. */
    public PayloadFields payload;

    /** Evolutionary Neural Memory Actor operations (null for Gateway-only messages). */
    public NeuralMemoryFields neuralMemory;

    /** Response data (populated by decoder, null for requests). */
    public ResponseFields response;

    public Message() {}

    // -------------------------------------------------------------------------
    // Convenience accessors — mirror Go's helper methods
    // -------------------------------------------------------------------------

    /** Returns GetEvent options or null if not set. */
    public GetEventOptions getEventOpts() {
        return (neuralMemory != null) ? neuralMemory.getEvent : null;
    }

    /** Returns GetEventsForTags options or null if not set. */
    public GetEventsForTagsOptions getEventsForTagsOpts() {
        return (neuralMemory != null) ? neuralMemory.getEventsForTags : null;
    }

    /** Returns Event.id or empty string if event is null. */
    public String eventId() {
        return (event != null) ? event.id : "";
    }

    /** Returns Event.uniqueId or empty string if event is null. */
    public String eventUniqueId() {
        return (event != null) ? event.uniqueId : "";
    }

    /** Returns Payload.data or null if payload is null. */
    public Object payloadData() {
        return (payload != null) ? payload.data : null;
    }

    /** Returns Payload.mimeType or empty string if payload is null. */
    public String payloadMimeType() {
        return (payload != null && payload.mimeType != null) ? payload.mimeType : "";
    }

    /** Returns Response.status or empty string if response is null. */
    public String processingStatus() {
        return (response != null && response.status != null) ? response.status : "";
    }

    /** Returns Response.message or empty string if response is null. */
    public String processingMessage() {
        return (response != null && response.message != null) ? response.message : "";
    }

    /** Returns NeuralMemory.tags or null if neuralMemory is null. */
    public java.util.List<Tag> tags() {
        return (neuralMemory != null) ? neuralMemory.tags : null;
    }

    /** Returns NeuralMemory.link or null if neuralMemory is null. */
    public LinkFields link() {
        return (neuralMemory != null) ? neuralMemory.link : null;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validates this message before encoding.
     * Returns {@code null} if validation is disabled (PODOS_VALIDATE env var).
     * Otherwise returns all violations at once.
     *
     * @return validation errors, or {@code null} if validation is disabled
     * @see MessageValidator#validate(Message)
     */
    public ValidationErrors validate() {
        return MessageValidator.validate(this);
    }
}
