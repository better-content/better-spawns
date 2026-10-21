package com.bettercontent.betterspawns.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class FeatureTelemetryTest {
    @Test public void nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload() {
        List<String> records = new ArrayList<>();
        PlaytestLog log = new PlaytestLog("better_spawns", new PlaytestLog.Sink() {
            public boolean enabled(String category, String level) { return true; }
            public void write(String category, String level, String message) { records.add(message); }
        }, () -> 0L, new Properties());
        FeatureTelemetry.finalized(log, new PlaytestLog.Context("server", "minecraft:overworld", 42,
                "7ac1c622-f1a4-4986-9404-70bf2345061c", ""), false, false);
        if (records.size() != 1 || !records.get(0).contains("result=deferred reason=onboarding_committed")
                || !records.get(0).contains("dimension=minecraft:overworld") || !records.get(0).contains("tick=42")
                || !records.get(0).contains("subject=7ac1c622-f1a4-4986-9404-70bf2345061c") || records.get(0).contains("pos=")
                || records.get(0).contains("player=") || records.get(0).contains("payload=")) {
            throw new AssertionError(records.toString());
        }
    }
    public static void main(String[] args) { new FeatureTelemetryTest().nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload(); }
}
