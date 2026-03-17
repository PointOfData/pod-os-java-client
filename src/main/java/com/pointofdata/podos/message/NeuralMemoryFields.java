package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups all Evolutionary Neural Memory Actor-specific operation fields.
 * Set only the sub-field relevant to your Intent; others should be null.
 */
public class NeuralMemoryFields {
    /** Options for GetEvent intent. */
    public GetEventOptions getEvent;
    /** Options for GetEventsForTags intent. */
    public GetEventsForTagsOptions getEventsForTags;
    /** Single link operation (LinkEvent, UnlinkEvent). */
    public LinkFields link;
    /** Single unlink operation. */
    public LinkFields unlink;
    /** Batch link operations (StoreBatchLinks). */
    public List<BatchLinkEventSpec> batchLinks = new ArrayList<>();
    /** Tags to store with an event (StoreEvent, StoreBatchTags). */
    public List<Tag> tags = new ArrayList<>();
    /** Batch event storage (StoreBatchEvents). */
    public List<BatchEventSpec> batchEvents = new ArrayList<>();

    public NeuralMemoryFields() {}
}
