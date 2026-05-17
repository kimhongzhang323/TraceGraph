package io.tracegraph.observability.replay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Bulk-exports every {@link ExecutionTrace} in a {@link TraceStore} as a JSONL file with one
 * JSON object per {@link TraceStep}. The line format is platform-neutral and intended for
 * batch ingestion by external observability backends (LangSmith, Langfuse, Arize, ...).
 *
 * <p>Each line carries: {@code executionId}, {@code stepIndex}, {@code nodeName},
 * {@code attempts}, {@code before}, {@code after}, {@code usage}, {@code durationMs}, and
 * (when present) {@code error}. Null fields are omitted from the JSON.
 *
 * <p>Jackson is an {@code <optional>} dependency of {@code tracegraph-observability}; users
 * who call this exporter must also depend on {@code jackson-databind} themselves.
 */
public final class JsonlTraceExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonlTraceExporter() {}

    /**
     * Write every trace from {@code store} to {@code target}, one JSON object per top-level
     * {@link TraceStep}. The target file is created (with parent directories) if missing, and
     * truncated if it already exists.
     *
     * @return the number of JSONL lines written
     */
    public static int export(TraceStore store, Path target) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(target, "target");
        Path parent = target.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create parent directory: " + parent, e);
            }
        }
        int count = 0;
        try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            for (String id : store.listIds()) {
                ExecutionTrace<?> trace = store.load(id).orElse(null);
                if (trace == null) continue;
                for (TraceStep<?> step : trace.steps()) {
                    w.write(MAPPER.writeValueAsString(StepLine.from(trace.executionId(), step)));
                    w.write('\n');
                    count++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to export JSONL traces to " + target, e);
        }
        return count;
    }

    record StepLine(String executionId, int stepIndex, String nodeName, int attempts,
                    Object before, Object after,
                    @JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) TraceStep.Usage usage,
                    long durationMs,
                    @JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) ErrorLine error) {
        static StepLine from(String executionId, TraceStep<?> s) {
            return new StepLine(executionId, s.index(), s.nodeName(), s.attempts(),
                    s.before(), s.after(), s.usage(),
                    s.duration().toMillis(), ErrorLine.from(s.error()));
        }
    }

    record ErrorLine(String className, String message) {
        static ErrorLine from(Throwable t) {
            return t == null ? null : new ErrorLine(t.getClass().getName(), t.getMessage());
        }
    }
}
