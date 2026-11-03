package com.bettercontent.betterspawns.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Repository-local copy of the bounded playtest contract. No native objects or gameplay RNG. */
public final class PlaytestLog {
    public static final int MAX_RECORD = 2048;
    private static final long WINDOW = 30_000_000_000L;
    private static final long TRACE_WINDOW = 60_000_000_000L;
    private static final String PROCESS = processId();
    public record Context(String side, String dimension, long tick, String subject, String pos) {
        public static Context common() { return new Context("common", "", -1, "", ""); }
        public static Context server(String dimension, long tick) { return new Context("server", dimension, tick, "", ""); }
        public static Context client(String dimension, long tick) { return new Context("client", dimension, tick, "", ""); }
    }
    /** Injectable pure sink for deterministic owner-local tests. Level: INFO, DEBUG, TRACE. */
    public interface Sink {
        boolean enabled(String category, String level);
        void write(String category, String level, String message);
    }
    private static final class NativeSink implements Sink {
        private final String name;
        private final Map<String, Logger> loggers = new LinkedHashMap<>();
        NativeSink(String mod) { name = "bc.playtest." + mod; }
        private synchronized Logger logger(String category) {
            String key = code(category);
            if (!loggers.containsKey(key) && loggers.size() >= 31) key = "other";
            return loggers.computeIfAbsent(key, k -> LoggerFactory.getLogger(name + "." + k));
        }
        public boolean enabled(String category, String level) {
            Logger logger = logger(category);
            return switch (level) { case "TRACE" -> logger.isTraceEnabled(); case "DEBUG" -> logger.isDebugEnabled(); default -> logger.isInfoEnabled(); };
        }
        public void write(String category, String level, String message) {
            Logger logger = logger(category);
            switch (level) { case "TRACE" -> logger.trace(message); case "DEBUG" -> logger.debug(message); default -> logger.info(message); }
        }
    }
    private final String mod;
    private final Sink sink;
    private final LongSupplier clock;
    private final String focusDimension, focusSubject, focusPos;
    private final boolean validFocus;
    private final Map<String, Long> reasons = new LinkedHashMap<>();
    private double tokens = 20;
    private long refill, windowStart, attempted, emitted, suppressed, overflow;
    private long traceStart;
    private int traceRecords;
    private boolean traceStarted, traceNotice, announced;

    public PlaytestLog(String mod) { this(mod, new NativeSink(mod), System::nanoTime, System.getProperties()); }
    public PlaytestLog(String mod, Sink sink, LongSupplier clock, java.util.Properties properties) {
        this.mod = code(mod); this.sink = sink; this.clock = clock;
        refill = windowStart = clock.getAsLong();
        String prefix = "bc.playtest.trace." + this.mod;
        focusDimension = properties.getProperty(prefix + ".dimension", "");
        focusSubject = properties.getProperty(prefix + ".subject", "");
        focusPos = properties.getProperty(prefix + ".pos", "");
        boolean subject = validUuid(focusSubject);
        boolean position = validPosition(focusPos);
        validFocus = focusDimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
            && ((subject && focusPos.isEmpty()) || (position && focusSubject.isEmpty()));
    }
    /** Call only at initialization using the owning Forge ModFile's exact metadata path. */
    public String sourceIdentity(Path metadata) {
        if (metadata == null) return "UNKNOWN";
        try (InputStream input = Files.newInputStream(metadata)) { return sourceIdentity(input); }
        catch (IOException | RuntimeException unavailable) { return "UNKNOWN"; }
    }
    /** Bounded metadata parser; never search another mod's classloader resources for this name. */
    public String sourceIdentity(InputStream input) {
        if (input == null) return "UNKNOWN";
        try {
            byte[] bytes = input.readNBytes(16_385);
            if (bytes.length > 16_384) return "UNKNOWN";
            java.util.Properties values = new java.util.Properties();
            values.load(new ByteArrayInputStream(bytes));
            String commit = values.getProperty("commit", "");
            return "bc.custom_mod_source.v1".equals(values.getProperty("schema"))
                && mod.equals(values.getProperty("mod_id"))
                && !values.getProperty("repository", "").isBlank()
                && commit.matches("(?:[a-f0-9]{40}|[a-f0-9]{64})") ? commit : "UNKNOWN";
        } catch (IOException | RuntimeException unavailable) { return "UNKNOWN"; }
    }
    public boolean enabled(String category) { return sink.enabled(category, "DEBUG"); }
    public synchronized boolean traceEnabled(String category) {
        if (!sink.enabled(category, "TRACE")) return false;
        if (!validFocus && !traceNotice) {
            traceNotice = true;
            sink.write("status", "INFO", bounded(base("logging.trace", Context.common(), "disabled", "focus_required")));
        }
        if (validFocus && traceStarted && (clock.getAsLong() - traceStart >= TRACE_WINDOW || traceRecords >= 1000)) {
            if (!traceNotice) {
                traceNotice = true;
                sink.write("status", "INFO", bounded(base("logging.trace", Context.common(), "disabled", "trace_expired")));
            }
            return false;
        }
        return validFocus;
    }
    public void status(String side) { status(side, "unknown", "unknown", () -> ""); }
    public synchronized void status(String side, String version, String source, Supplier<String> fields) {
        if (announced || !sink.enabled("status", "INFO")) return;
        announced = true;
        String mode = enabled("status") ? "debug" : "normal";
        sink.write("status", "INFO", bounded(base("logging.status", new Context(side, "", -1, "", ""), "ready", "initialized")
            + fields("mode", mode, "version", version, "source", source, "detail_per_minute", 120, "burst", 20,
                "max_keys", 256, "max_record", MAX_RECORD, "summary_seconds", 30, "trace_focus", validFocus)
            + safeFields(fields)));
    }
    /** Caller should guard enabled(category) before constructing native contexts or expensive suppliers. */
    public void event(String category, String event, String side, String result, String reason, Supplier<String> fields) {
        debug(category, event, new Context(side, "", -1, "", ""), result, reason, fields);
    }
    public void debug(String category, String event, Context context, String result, String reason, Supplier<String> fields) {
        record(category, event, context, result, reason, fields, false);
    }
    public void trace(String category, String event, Context context, String result, String reason, Supplier<String> fields) {
        record(category, event, context, result, reason, fields, true);
    }
    private synchronized void record(String category, String event, Context context, String result, String reason, Supplier<String> fields, boolean trace) {
        String level = trace ? "TRACE" : "DEBUG";
        if (!sink.enabled(category, level)) return;
        long now = clock.getAsLong();
        if (now - windowStart >= WINDOW) flush(context.side(), now);
        if (trace) {
            if (!validFocus) {
                if (!traceNotice) {
                    traceNotice = true;
                    sink.write("status", "INFO", bounded(base("logging.trace", context, "disabled", "focus_required")));
                }
                return;
            }
            if (!focusDimension.equals(context.dimension()) || (!focusSubject.isEmpty() && !focusSubject.equals(context.subject()))
                || (!focusPos.isEmpty() && !focusPos.equals(context.pos()))) return;
            if (!traceStarted) { traceStarted = true; traceStart = now; }
            if (now - traceStart >= TRACE_WINDOW || traceRecords >= 1000) {
                if (!traceNotice) {
                    traceNotice = true;
                    sink.write("status", "INFO", bounded(base("logging.trace", context, "disabled", "trace_expired")));
                }
                return;
            }
        }
        attempted++;
        String key = code(category) + "." + code(event) + ":" + code(reason);
        if (reasons.containsKey(key)) reasons.put(key, reasons.get(key) + 1);
        else if (reasons.size() < 256) reasons.put(key, 1L);
        else overflow++;
        long delta = Math.max(0, now - refill);
        tokens = Math.min(20, tokens + delta / 500_000_000.0);
        refill = now;
        if (tokens < 1) { suppressed++; return; }
        tokens -= 1;
        emitted++;
        if (trace) traceRecords++;
        sink.write(category, level, bounded(base(event, context, result, reason) + safeFields(fields)));
    }
    /** Call on an existing tick/event boundary; does no world work and never reads native state. */
    public synchronized void summary(String side) {
        long now = clock.getAsLong();
        if (now - windowStart >= WINDOW) flush(side, now);
    }
    private void flush(String side, long now) {
        if (attempted > 0 && sink.enabled("summary", "DEBUG")) {
            StringBuilder detail = new StringBuilder(fields("attempted", attempted, "emitted", emitted,
                "suppressed", suppressed, "overflow", overflow, "keys", reasons.size()));
            int shown = 0; long other = 0;
            for (Map.Entry<String, Long> entry : reasons.entrySet()) {
                if (shown++ < 8) detail.append(fields("count." + entry.getKey(), entry.getValue()));
                else other += entry.getValue();
            }
            detail.append(fields("other_reasons", other));
            sink.write("summary", "DEBUG", bounded(base("logging.summary", new Context(side, "", -1, "", ""), "observed", "window") + detail));
        }
        attempted = emitted = suppressed = overflow = 0;
        reasons.clear(); windowStart = now;
    }
    /** Clear transient keys on teardown; the once-per-process focused TRACE does not rearm. */
    public synchronized void reset(String side) {
        flush(side, clock.getAsLong());
        // Teardown clears keys, not the process-wide rate allowance or TRACE lifetime.
    }
    public synchronized int aggregationKeys() { return reasons.size(); }
    private String base(String event, Context context, String result, String reason) {
        return "BC_PLAYTEST schema=bc.playtest.v1" + fields("mod", mod, "event", code(event), "side", code(context.side()),
            "process", PROCESS, "result", code(result), "reason", code(reason))
            + (context.dimension() == null || context.dimension().isEmpty() ? "" : fields("dimension", context.dimension()))
            + (context.tick() < 0 ? "" : fields("tick", context.tick()))
            + (context.subject() == null || context.subject().isEmpty() ? "" : fields("subject", context.subject()))
            + (context.pos() == null || context.pos().isEmpty() ? "" : fields("pos", context.pos()));
    }
    public static String fields(Object... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length && out.length() < MAX_RECORD; i += 2)
            out.append(' ').append(code(String.valueOf(pairs[i]))).append('=').append(escape(String.valueOf(pairs[i + 1])));
        return out.toString();
    }
    private static String safeFields(Supplier<String> supplier) {
        if (supplier == null) return "";
        try { String value = supplier.get(); return value == null ? "" : " " + escapeRecord(value); }
        catch (RuntimeException diagnosticFailure) { return fields("diagnostic_fields", "unavailable"); }
    }
    private static String escapeRecord(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_RECORD));
        for (int i = 0; i < value.length() && result.length() < MAX_RECORD; i++) {
            char ch = value.charAt(i);
            if (ch == '\n') result.append("\\n"); else if (ch == '\r') result.append("\\r");
            else if (ch == '\t') result.append("\\t"); else if (Character.isISOControl(ch)) result.append('?'); else result.append(ch);
        }
        return result.toString();
    }
    private static String escape(String value) {
        String escaped = escapeRecord(value);
        return escaped.isEmpty() || escaped.chars().anyMatch(Character::isWhitespace)
            || escaped.indexOf('"') >= 0 || escaped.indexOf('\\') >= 0
            ? "\"" + escaped.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : escaped;
    }
    private static String code(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        StringBuilder out = new StringBuilder(Math.min(96, value.length()));
        for (int i = 0; i < value.length() && out.length() < 96; i++) {
            char c = value.charAt(i); out.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':' ? c : '_');
        }
        return out.toString();
    }
    private static String bounded(String value) { return value.length() <= MAX_RECORD ? value : value.substring(0, MAX_RECORD - 15) + " truncated=true"; }
    private static boolean validPosition(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) return false;
        try {
            for (String part : parts) if (!Integer.toString(Integer.parseInt(part)).equals(part)) return false;
            return true;
        } catch (NumberFormatException ignored) { return false; }
    }
    private static boolean validUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException ignored) { return false; }
    }
    private static String processId() {
        synchronized (System.getProperties()) {
            String existing = System.getProperty("bc.playtest.process");
            if (existing != null && validUuid(existing)) return existing;
            String created = UUID.randomUUID().toString(); System.setProperty("bc.playtest.process", created); return created;
        }
    }
}
