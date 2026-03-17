package com.pointofdata.podos.errors;

/**
 * Typed error for Pod-OS gateway/connection failures.
 * Mirrors Go's {@code GatewayDError} struct.
 */
public class GatewayDError extends RuntimeException {

    private final ErrCode code;
    private final String message;
    private final Throwable originalError;

    public GatewayDError(ErrCode code, String message) {
        super(message);
        this.code          = code;
        this.message       = message;
        this.originalError = null;
    }

    public GatewayDError(ErrCode code, String message, Throwable cause) {
        super(message, cause);
        this.code          = code;
        this.message       = message;
        this.originalError = cause;
    }

    /** Returns a new GatewayDError wrapping the given cause, preserving code and message. */
    public GatewayDError wrap(Throwable cause) {
        return new GatewayDError(this.code, this.message, cause);
    }

    public ErrCode getCode() { return code; }

    @Override
    public String getMessage() {
        if (originalError == null) return message;
        return message + ", OriginalError: " + originalError.getMessage();
    }

    @Override
    public Throwable getCause() { return originalError; }

    // =========================================================================
    // Pre-allocated sentinel errors (mirrors Go's var Err* declarations)
    // =========================================================================

    public static final GatewayDError ERR_CLIENT_NOT_FOUND        = new GatewayDError(ErrCode.CLIENT_NOT_FOUND,        "client not found");
    public static final GatewayDError ERR_NIL_CONTEXT             = new GatewayDError(ErrCode.NIL_CONTEXT,             "context is nil");
    public static final GatewayDError ERR_CLIENT_NOT_CONNECTED    = new GatewayDError(ErrCode.CLIENT_NOT_CONNECTED,    "client is not connected");
    public static final GatewayDError ERR_CLIENT_CONNECTION_FAILED= new GatewayDError(ErrCode.CLIENT_CONNECTION_FAILED,"failed to create a new connection");
    public static final GatewayDError ERR_NETWORK_NOT_SUPPORTED   = new GatewayDError(ErrCode.NETWORK_NOT_SUPPORTED,   "network is not supported");
    public static final GatewayDError ERR_RESOLVE_FAILED          = new GatewayDError(ErrCode.RESOLVE_FAILED,          "failed to resolve address");
    public static final GatewayDError ERR_POOL_EXHAUSTED          = new GatewayDError(ErrCode.POOL_EXHAUSTED,          "pool is exhausted");
    public static final GatewayDError ERR_CLIENT_RECEIVE_FAILED   = new GatewayDError(ErrCode.CLIENT_RECEIVE_FAILED,   "couldn't receive data from the server");
    public static final GatewayDError ERR_CLIENT_SEND_FAILED      = new GatewayDError(ErrCode.CLIENT_SEND_FAILED,      "couldn't send data to the server");
    public static final GatewayDError ERR_VALIDATION_FAILED       = new GatewayDError(ErrCode.VALIDATION_FAILED,       "validation failed");
    public static final GatewayDError ERR_NIL_POINTER             = new GatewayDError(ErrCode.NIL_POINTER,             "nil pointer");
    public static final GatewayDError ERR_CAST_FAILED             = new GatewayDError(ErrCode.CAST_FAILED,             "failed to cast");
    public static final GatewayDError ERR_MSG_ENCODE_ERROR        = new GatewayDError(ErrCode.MSG_ENCODE_ERROR,        "error encoding message");
}
