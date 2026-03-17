package com.pointofdata.podos.message;

/**
 * Contains the message payload data and metadata.
 */
public class PayloadFields {
    /**
     * Payload data. Can be: String, byte[], List&lt;BatchEventSpec&gt;,
     * List&lt;BatchLinkEventSpec&gt;, List&lt;Tag&gt;, or any structured data.
     */
    public Object data;
    /** Bitmap indicating data format / compression. */
    public DataType dataType = DataType.RAW;
    /** MIME type (e.g., "application/json", "text/plain"). */
    public String mimeType = "";
    /** Data size in bytes (populated by decoder). */
    public int dataSize;

    public PayloadFields() {}

    public PayloadFields(Object data, String mimeType) {
        this.data = data;
        this.mimeType = mimeType;
    }
}
