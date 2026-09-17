package com.pointofdata.podos.message;

/** Helpers for StoreBatchLinks / StoreBatchEvents batch record outcomes. */
public final class BatchStatus {

    private BatchStatus() {}

    /**
     * Returns a non-null error message when StoreBatchLinks batch record reports failures.
     * Envelope status can be OK while linksWithErrors &gt; 0 or batch record status is ERROR.
     */
    public static String batchLinksFailed(Message msg) {
        if (msg == null || msg.response == null) {
            return null;
        }
        StoreLinkBatchEventRecord rec = msg.response.storeLinkBatchEventRecord;
        if (rec == null) {
            if (msg.response.storageErrorCount > 0) {
                return "link store failed";
            }
            return null;
        }
        if ("ERROR".equalsIgnoreCase(rec.status.trim()) || rec.linksWithErrors > 0) {
            String text = rec.message == null ? "" : rec.message.trim();
            return text.isEmpty() ? "link store failed" : text;
        }
        return null;
    }

    /** Returns a non-null error message when StoreBatchEvents batch record reports failures. */
    public static String batchEventsFailed(Message msg) {
        if (msg == null || msg.response == null) {
            return null;
        }
        StoreBatchEventRecord rec = msg.response.storeBatchEventRecord;
        if (rec == null) {
            return null;
        }
        if ("ERROR".equalsIgnoreCase(rec.status.trim())) {
            String text = rec.message == null ? "" : rec.message.trim();
            return text.isEmpty() ? "batch event store failed" : text;
        }
        return null;
    }
}
