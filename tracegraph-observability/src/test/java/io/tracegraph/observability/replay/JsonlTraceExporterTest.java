package io.tracegraph.observability.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlTraceExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant T0 = Instant.parse("2026-05-17T17:00:00Z");

    @Test
    void writesOneLinePerTraceStep(@TempDir Path tmp) throws Exception {
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("e1", "in", "out", Status.COMPLETED, null,
                List.of(
                        TraceStep.leaf(0, "fetch", 1, "in", "mid",
                                Duration.ofMillis(12), null, new TraceStep.Usage(10, 5)),
                        TraceStep.leaf(1, "format", 1, "mid", "out",
                                Duration.ofMillis(3), null)
                ),
                T0, T0.plusSeconds(1)));
        Path out = tmp.resolve("traces.jsonl");

        int written = JsonlTraceExporter.export(store, out);

        assertThat(written).isEqualTo(2);
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(2);

        JsonNode first = MAPPER.readTree(lines.get(0));
        assertThat(first.get("executionId").asText()).isEqualTo("e1");
        assertThat(first.get("nodeName").asText()).isEqualTo("fetch");
        assertThat(first.get("stepIndex").asInt()).isEqualTo(0);
        assertThat(first.get("attempts").asInt()).isEqualTo(1);
        assertThat(first.get("before").asText()).isEqualTo("in");
        assertThat(first.get("after").asText()).isEqualTo("mid");
        assertThat(first.get("durationMs").asLong()).isEqualTo(12L);
        assertThat(first.get("usage").get("promptTokens").asInt()).isEqualTo(10);
        assertThat(first.get("usage").get("completionTokens").asInt()).isEqualTo(5);
        assertThat(first.has("error")).isFalse();

        JsonNode second = MAPPER.readTree(lines.get(1));
        assertThat(second.get("nodeName").asText()).isEqualTo("format");
        assertThat(second.get("durationMs").asLong()).isEqualTo(3L);
        assertThat(second.has("usage")).isFalse();
    }

    @Test
    void writesAcrossMultipleTraces(@TempDir Path tmp) throws Exception {
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("e1", "i", "o", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "i", "o", Duration.ofMillis(1), null)),
                T0, T0.plusSeconds(1)));
        store.save(new ExecutionTrace<>("e2", "i", "o", Status.COMPLETED, null,
                List.of(
                        TraceStep.leaf(0, "a", 1, "i", "x", Duration.ofMillis(2), null),
                        TraceStep.leaf(1, "b", 1, "x", "o", Duration.ofMillis(3), null)
                ),
                T0, T0.plusSeconds(2)));
        Path out = tmp.resolve("traces.jsonl");

        int written = JsonlTraceExporter.export(store, out);

        assertThat(written).isEqualTo(3);
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(3);
        assertThat(lines).extracting(l -> MAPPER.readTree(l).get("executionId").asText())
                .containsExactlyInAnyOrder("e1", "e2", "e2");
    }

    @Test
    void serializesFailedStepWithError(@TempDir Path tmp) throws Exception {
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("boom", "i", null, Status.FAILED,
                new IllegalStateException("kaboom"),
                List.of(TraceStep.leaf(0, "explode", 2, "i", null,
                        Duration.ofMillis(7), new IllegalStateException("kaboom"))),
                T0, T0.plusSeconds(1)));
        Path out = tmp.resolve("failed.jsonl");

        JsonlTraceExporter.export(store, out);

        JsonNode line = MAPPER.readTree(Files.readAllLines(out).get(0));
        assertThat(line.get("attempts").asInt()).isEqualTo(2);
        assertThat(line.get("after").isNull()).isTrue();
        assertThat(line.get("error").get("className").asText())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(line.get("error").get("message").asText()).isEqualTo("kaboom");
    }

    @Test
    void writesEmptyFileForEmptyStore(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("empty.jsonl");

        int written = JsonlTraceExporter.export(new InMemoryTraceStore(), out);

        assertThat(written).isZero();
        assertThat(Files.exists(out)).isTrue();
        assertThat(Files.readAllBytes(out)).isEmpty();
    }

    @Test
    void truncatesExistingFile(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("traces.jsonl");
        Files.writeString(out, "stale content\n");
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("e", "i", "o", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "i", "o", Duration.ofMillis(1), null)),
                T0, T0.plusSeconds(1)));

        JsonlTraceExporter.export(store, out);

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).doesNotContain("stale");
    }

    @Test
    void createsParentDirectoryIfMissing(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("nested/sub/traces.jsonl");
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("e", "i", "o", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "i", "o", Duration.ofMillis(1), null)),
                T0, T0.plusSeconds(1)));

        JsonlTraceExporter.export(store, out);

        assertThat(Files.exists(out)).isTrue();
        assertThat(Files.readAllLines(out)).hasSize(1);
    }

    @Test
    void rejectsNullArguments(@TempDir Path tmp) {
        Path out = tmp.resolve("x.jsonl");
        assertThatThrownBy(() -> JsonlTraceExporter.export(null, out))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> JsonlTraceExporter.export(new InMemoryTraceStore(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
