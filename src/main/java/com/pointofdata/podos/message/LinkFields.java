package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains fields for link operations between events.
 * Used for LinkEvent, UnlinkEvent, StoreBatchLinks, and their response variants.
 */
public class LinkFields {
    /** Developer-provided unique ID for the link event. */
    public String uniqueId = "";
    /** AIP-generated unique ID; must be ASCII encoded. */
    public String id = "";
    /** Local machine ID for the event. */
    public String localId = "";
    /** Owner ID; indicates what entity created the event (default "$sys"). */
    public String owner = "";
    /** POSIX timestamp in microseconds ("+%.6f"). */
    public String timestamp = "";
    /** Parsed date/time. */
    public DateTimeObject dateTime = new DateTimeObject();
    /** Location specification (e.g., "TERRA|47.6|-122.5"). */
    public String location = "";
    /** Location segment delimiter (default "|"). */
    public String locationSeparator = "";
    /** Required if uniqueIdA is not provided. */
    public String eventA = "";
    /** Required if uniqueIdB is not provided. */
    public String eventB = "";
    /** Required if eventA is not provided. */
    public String uniqueIdA = "";
    /** Required if eventB is not provided. */
    public String uniqueIdB = "";
    /** Link strength A→B, required. Zero, NaN, and infinities are rejected; other positive and negative values are allowed. */
    public double strengthA;
    /** Link strength B→A, required. Zero, NaN, and infinities are rejected; other positive and negative values are allowed. */
    public double strengthB;
    /** Link category, required. */
    public String category = "";

    /**
     * Zero, negative zero, NaN, and infinities are not stored.
     * Other positive and negative values are allowed.
     */
    public static boolean strengthRejected(double value) {
        return value == 0.0d || !Double.isFinite(value);
    }

    /**
     * Rejects a link the actor would drop instead of saving.
     * strengthA and strengthB must be finite and non-zero. category must be non-empty.
     *
     * @param lk    link to check; null is treated as missing
     * @param where caller context included in the error message
     * @throws IllegalArgumentException when strength or category is missing, zero, or non-finite
     */
    public static void requireWireFields(LinkFields lk, String where) {
        if (lk == null) {
            throw new IllegalArgumentException(
                    where + ": link is missing; strength_a, strength_b, and category are required");
        }
        rejectStrength(lk.strengthA, where, "strength_a");
        rejectStrength(lk.strengthB, where, "strength_b");
        if (lk.category == null || lk.category.isEmpty()) {
            throw new IllegalArgumentException(where + ": category is required");
        }
    }

    private static void rejectStrength(double value, String where, String field) {
        if (strengthRejected(value)) {
            throw new IllegalArgumentException(
                    where + ": " + field + " must be a finite non-zero number (positive and negative values are allowed)");
        }
    }
    /** Developer-defined event type string. */
    public String type = "";
    /** Owner unique ID; required if ownerID is not provided. */
    public String ownerUniqueId = "";
    /** Owner ID; required if ownerUniqueId is not provided. */
    public String ownerId = "";
    /** Tags for this link; populated from _linktag records. */
    public List<TagOutput> tags = new ArrayList<>();
    /** Tags describing the target event; populated from _targettag records. */
    public List<TagOutput> targetTags = new ArrayList<>();
    /** Status of the link; used in StoreBatchLinks response. */
    public String status = "";
    /** Message of the link; used in StoreBatchLinks response. */
    public String message = "";

    public LinkFields() {}
}
