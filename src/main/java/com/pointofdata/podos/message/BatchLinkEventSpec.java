package com.pointofdata.podos.message;

/**
 * Represents a single link specification for batch linking (StoreBatchLinks).
 */
public class BatchLinkEventSpec {
    public EventFields event = new EventFields();
    public LinkFields link = new LinkFields();

    public BatchLinkEventSpec() {}

    public BatchLinkEventSpec(EventFields event, LinkFields link) {
        this.event = event;
        this.link = link;
    }
}
