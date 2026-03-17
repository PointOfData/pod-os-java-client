package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.List;

/** Parsed response record for StoreBatchLinks. */
public class StoreLinkBatchEventRecord {
    public String status = "";
    public String message = "";
    public int totalLinkRequestsFound;
    public int linksOk;
    public int linksWithErrors;
    /** Individual link results. */
    public List<LinkFields> linkResults = new ArrayList<>();
}
