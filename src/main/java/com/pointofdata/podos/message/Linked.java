package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * One directed adjacency from the searched or returned event to a neighbor.
 * Strength is always source → target. Write-side A/B weights stay on {@link LinkFields}.
 */
public class Linked {
    /** Link event id ({@code _link}). */
    public String id = "";
    /** Link unique id. */
    public String uniqueId = "";
    /** Returned/search object event id. */
    public String sourceId = "";
    /** Returned/search object unique id. */
    public String sourceUniqueId = "";
    /** Neighbor event id. */
    public String targetId = "";
    /** Neighbor unique id. */
    public String targetUniqueId = "";
    /** Source → target only. */
    public double strength;
    /** Link category / predicate. */
    public String category = "";
    /** Tags from {@code _linktag} rows (including optional {@code endpoint_*} provenance). */
    public List<TagOutput> tags = new ArrayList<>();
    /** Tags describing the target event; populated from {@code _targettag} records. */
    public List<TagOutput> targetTags = new ArrayList<>();

    public Linked() {}
}
