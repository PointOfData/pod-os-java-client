package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/** Parsed response record for StoreBatchEvents. */
public class StoreBatchEventRecord {
    public String status = "";
    public String message = "";
    /** Total number of Events stored. */
    public int eventCount;
    /** Individual event results. */
    public List<EventFields> eventResults = new ArrayList<>();
}
