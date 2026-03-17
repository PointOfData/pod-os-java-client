package com.pointofdata.podos.message;

/**
 * Contains the core routing fields required for all Actor messages.
 * Embedded in {@link Message} for convenient top-level access.
 */
public class Envelope {
    /** Recipient: {@code <Actor Name>@<Gateway Name>} */
    public String to = "";
    /** Sender: {@code <Actor Name>@<Gateway Name>} */
    public String from = "";
    /** Message intent type. */
    public Intent intent;
    /**
     * Unique client identifier for this connection; required for GatewayId messages.
     */
    public String clientName = "";
    /**
     * Unique message identifier for request/response correlation.
     * Wire field: {@code _msg_id}. Optional.
     */
    public String messageId = "";
    /**
     * Optional passcode for authentication and authorization.
     * Wire field: {@code id:passcode}.
     */
    public String passcode = "";
    /**
     * Optional user name for authentication and authorization.
     * Wire field: {@code id:user}.
     */
    public String userName = "";

    public Envelope() {}
}
