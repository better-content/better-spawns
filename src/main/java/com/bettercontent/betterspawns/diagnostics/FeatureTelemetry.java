package com.bettercontent.betterspawns.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void finalized(PlaytestLog log, boolean released, boolean reselection) {
        log.event("selection", "onboarding.finalize", "server", released ? "released" : "deferred", reselection ? "reselection_committed" : "onboarding_committed", null);
    }
}
