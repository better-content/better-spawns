package com.bettercontent.betterspawns.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/** The same pure contract runs in each owning repository and can run via main without Minecraft. */
public final class PlaytestLogContractTest {
    private static final class Capture implements PlaytestLog.Sink {
        boolean debug = true, trace;
        final List<String> messages = new ArrayList<>();
        public boolean enabled(String category, String level) { return level.equals("INFO") || (level.equals("TRACE") ? trace : debug); }
        public void write(String category, String level, String message) { messages.add(level + " " + message); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Test public void disabledIsLazyAndStatusIsExplicit() {
        Capture sink = new Capture(); sink.debug = false;
        PlaytestLog log = new PlaytestLog("test_mod", sink, () -> 0L, new Properties());
        log.debug("action", "try", PlaytestLog.Context.server("minecraft:overworld", 4), "rejected", "ordinary", () -> { throw new AssertionError("disabled supplier"); });
        check(sink.messages.isEmpty(), "disabled detail emitted");
        log.status("common");
        check(sink.messages.size() == 1 && sink.messages.get(0).contains("mode=normal"), "missing disabled status");
    }
    @Test public void globalBudgetSummariesAndTeardownDoNotRearmAllowance() {
        Capture sink = new Capture(); AtomicLong now = new AtomicLong();
        PlaytestLog log = new PlaytestLog("test_mod", sink, now::get, new Properties());
        for (int i = 0; i < 100; i++) log.event("action", "attempt", "server", "rejected", "not_ready", () -> " input=one");
        check(sink.messages.size() == 20, "burst is not twenty");
        log.reset("server");
        int afterReset = sink.messages.size();
        log.event("action", "attempt", "server", "rejected", "not_ready", () -> { throw new AssertionError("reset bypassed bucket"); });
        check(sink.messages.size() == afterReset, "reset replenished budget");
        now.set(30_000_000_000L); log.summary("server");
        check(sink.messages.stream().anyMatch(s -> s.contains("attempted=100") && s.contains("emitted=20") && s.contains("suppressed=80")), "summary lost totals");
        log.event("other", "later", "server", "accepted", "committed", () -> "");
        check(sink.messages.get(sink.messages.size() - 1).contains("event=later"), "time failed to refill");
    }
    @Test public void boundedCardinalityEscapingAndLength() {
        Capture sink = new Capture(); AtomicLong now = new AtomicLong();
        PlaytestLog log = new PlaytestLog("test_mod", sink, now::get, new Properties());
        for (int i = 0; i < 400; i++) log.event("action", "attempt", "server", "rejected", "reason_" + i, () -> "");
        check(log.aggregationKeys() == 256, "unbounded aggregation keys");
        now.set(30_000_000_000L); log.summary("server");
        check(log.aggregationKeys() == 0, "expired keys retained");
        check(sink.messages.stream().anyMatch(s -> s.contains("overflow=144")), "overflow missing");
        log.event("action", "escaped", "server", "accepted", "test", () -> PlaytestLog.fields("value", "a\nb\r\t\"\\"));
        String escaped = sink.messages.get(sink.messages.size() - 1);
        check(!escaped.contains("\n") && !escaped.contains("\r") && !escaped.contains("\t"), "multiline record");
        now.addAndGet(1_000_000_000L);
        log.event("action", "bounded", "server", "accepted", "test", () -> " value=" + "x".repeat(10000));
        String bounded = sink.messages.get(sink.messages.size() - 1);
        check(bounded.substring(bounded.indexOf("BC_PLAYTEST")).length() <= 2048, "record too long");
        check(bounded.contains("truncated=true"), "truncation not reported");
    }
    @Test public void traceRequiresValidMatchingFocusAndExpiresWithoutRearming() {
        Capture sink = new Capture(); sink.trace = true;
        AtomicLong now = new AtomicLong();
        PlaytestLog.Context context = new PlaytestLog.Context("server", "minecraft:overworld", 4, "00000000-0000-0000-0000-000000000001", "1,2,3");
        PlaytestLog absent = new PlaytestLog("test_mod", sink, now::get, new Properties());
        absent.trace("detail", "one", context, "observed", "focus", () -> { throw new AssertionError("absent focus evaluated"); });
        absent.trace("detail", "two", context, "observed", "focus", () -> "");
        check(sink.messages.size() == 1 && sink.messages.get(0).contains("focus_required"), "missing focus notice floods");
        Properties props = new Properties(); props.setProperty("bc.playtest.trace.test_mod.dimension", "minecraft:overworld");
        props.setProperty("bc.playtest.trace.test_mod.subject", context.subject());
        PlaytestLog focused = new PlaytestLog("test_mod", sink, now::get, props);
        int before = sink.messages.size();
        focused.trace("detail", "wrong", PlaytestLog.Context.server("minecraft:the_nether", 4), "observed", "focus", () -> { throw new AssertionError("wrong focus evaluated"); });
        check(sink.messages.size() == before, "wrong trace focus accepted");
        focused.trace("detail", "match", context, "observed", "focus", () -> "");
        check(sink.messages.get(sink.messages.size() - 1).startsWith("TRACE "), "focused trace absent");
        now.set(60_000_000_000L);
        focused.trace("detail", "expired", context, "observed", "focus", () -> { throw new AssertionError("expired trace evaluated"); });
        focused.reset("server"); int after = sink.messages.size();
        focused.trace("detail", "still_expired", context, "observed", "focus", () -> { throw new AssertionError("trace rearmed"); });
        check(sink.messages.size() == after, "reset rearmed trace");
        props.setProperty("bc.playtest.trace.test_mod.pos", "1,2,3");
        PlaytestLog ambiguous = new PlaytestLog("test_mod", sink, now::get, props);
        check(!ambiguous.traceEnabled("detail"), "ambiguous trace focus accepted");
        props.remove("bc.playtest.trace.test_mod.subject");
        props.setProperty("bc.playtest.trace.test_mod.pos", "9999999999,0,0");
        check(!new PlaytestLog("test_mod", sink, now::get, props).traceEnabled("detail"), "overflowing block coordinate accepted");
    }
    @Test public void sourceIdentityIsBoundedAndRejectsOtherModsOrMalformedMetadata() {
        PlaytestLog log = new PlaytestLog("test_mod", new Capture(), () -> 0L, new Properties());
        String head = "a".repeat(40);
        String valid = "schema=bc.custom_mod_source.v1\nrepository=test-mod\nmod_id=test_mod\ncommit=" + head + "\n";
        java.util.function.Function<String, String> parse = text -> log.sourceIdentity(new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(parse.apply(valid).equals(head), "valid own source HEAD unavailable");
        check(parse.apply(valid.replace("mod_id=test_mod", "mod_id=other_mod")).equals("UNKNOWN"), "another mod source HEAD accepted");
        check(parse.apply(valid.replace(head, "not-a-commit")).equals("UNKNOWN"), "malformed source HEAD accepted");
        check(parse.apply(valid.replace("v1", "v2")).equals("UNKNOWN"), "unknown metadata schema accepted");
        check(parse.apply(valid + "x".repeat(16385)).equals("UNKNOWN"), "unbounded metadata accepted");
        check(parse.apply("schema=\\uZZZZ").equals("UNKNOWN"), "malformed properties raised into gameplay");
    }
    @Test public void concurrentProducersShareOneBudget() throws Exception {
        Capture sink = new Capture(); PlaytestLog log = new PlaytestLog("test_mod", sink, () -> 0L, new Properties());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) threads.add(new Thread(() -> {
            for (int j = 0; j < 100; j++) log.event("action", "concurrent", "server", "observed", "test", () -> "");
        }));
        for (Thread thread : threads) thread.start(); for (Thread thread : threads) thread.join();
        check(sink.messages.size() == 20, "concurrent producers bypassed global bucket");
        log.reset("server");
        check(sink.messages.stream().anyMatch(s -> s.contains("attempted=800") && s.contains("suppressed=780")), "concurrent accounting lost");
    }
    public static void main(String[] args) throws Exception {
        PlaytestLogContractTest tests = new PlaytestLogContractTest();
        tests.disabledIsLazyAndStatusIsExplicit(); tests.globalBudgetSummariesAndTeardownDoNotRearmAllowance();
        tests.boundedCardinalityEscapingAndLength(); tests.traceRequiresValidMatchingFocusAndExpiresWithoutRearming();
        tests.concurrentProducersShareOneBudget(); tests.sourceIdentityIsBoundedAndRejectsOtherModsOrMalformedMetadata();
        System.out.println("PlaytestLog contract: 6 cases passed");
    }
}
