package com.pointofdata.podos.readiness;

/**
 * Names a known-stable anchor actor used to confirm a gateway route is serving AIP again.
 * Use a platform singleton (e.g. test@zeroth) — never a freshly-peered customer gateway
 * that has no actors yet.
 */
public class GatewayReadinessProbe {

    /** Required anchor actor address (e.g. test@zeroth.pod-os.com). */
    public String probeActor = "";
    /** Actor type for probe intent selection (e.g. neural_memory, socket). */
    public String probeActorType = "";

    public GatewayReadinessProbe() {}

    public GatewayReadinessProbe(String probeActor, String probeActorType) {
        this.probeActor = probeActor;
        this.probeActorType = probeActorType;
    }
}
