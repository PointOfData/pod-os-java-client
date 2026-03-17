package com.pointofdata.podos.message;

/** Represents the data type encoding for message payloads. */
public enum DataType {
    RAW(0);
    // Future: BPE(1), GZIP(2), LZ7(4), BZIP(8), RC4(256)

    private final int value;

    DataType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DataType fromInt(int v) {
        for (DataType dt : values()) {
            if (dt.value == v) return dt;
        }
        return RAW;
    }
}
