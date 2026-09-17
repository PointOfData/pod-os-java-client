package com.pointofdata.podos.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchStatusTest {

    @Test
    void batchLinksFailedDetectsErrorRecord() {
        Message msg = new Message();
        msg.response = new ResponseFields();
        msg.response.storeLinkBatchEventRecord = new StoreLinkBatchEventRecord();
        msg.response.storeLinkBatchEventRecord.status = "ERROR";
        msg.response.storeLinkBatchEventRecord.message = "OWNER EVENT NOT FOUND";
        msg.response.storeLinkBatchEventRecord.linksWithErrors = 2;

        assertEquals("OWNER EVENT NOT FOUND", BatchStatus.batchLinksFailed(msg));
    }

    @Test
    void batchEventsFailedOkWhenNoError() {
        Message msg = new Message();
        msg.response = new ResponseFields();
        msg.response.storeBatchEventRecord = new StoreBatchEventRecord();
        msg.response.storeBatchEventRecord.status = "OK";

        assertNull(BatchStatus.batchEventsFailed(msg));
    }
}
