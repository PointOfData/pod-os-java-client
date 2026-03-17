package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single event specification for batch storage (StoreBatchEvents).
 */
public class BatchEventSpec {
    public EventFields event = new EventFields();
    public List<Tag> tags = new ArrayList<>();

    public BatchEventSpec() {}

    public BatchEventSpec(EventFields event, List<Tag> tags) {
        this.event = event;
        this.tags = tags != null ? tags : new ArrayList<>();
    }
}
