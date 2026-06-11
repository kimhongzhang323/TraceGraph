package io.tracegraph.rag;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.TraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagTraceIntegrationTest {

    private record RawIO(String nodeName, String rawInput, String rawOutput) {}

    private static final class CapturingRecorder implements TraceRecorder {
        final List<RawIO> rawIO = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void recordRawIO(String executionId, String nodeName, String rawInput, String rawOutput) {
            rawIO.add(new RawIO(nodeName, rawInput, rawOutput));
        }
    }

    @Test
    void pipelineRetrievalStepsAreVisibleInTheTrace() {
        CapturingRecorder recorder = new CapturingRecorder();
        Graph<RagState> graph = RagPipeline.builder()
                .retriever(query -> List.of("chunk-a", "chunk-b"))
                .traceRecorder(recorder)
                .sensitiveDataLogging(true)
                .build()
                .graph();

        ExecutionResult<RagState> result = graph.run(new RagState("what is tracegraph?", List.of(), List.of(), ""));

        assertThat(result.finalState().context()).contains("chunk-a");
        assertThat(recorder.rawIO).extracting(RawIO::nodeName).contains("retrieve", "rerank");
        RawIO retrieve = recorder.rawIO.stream().filter(r -> r.nodeName().equals("retrieve")).findFirst().orElseThrow();
        assertThat(retrieve.rawInput()).isEqualTo("what is tracegraph?");
        assertThat(retrieve.rawOutput()).isEqualTo("chunks=2");
        RawIO rerank = recorder.rawIO.stream().filter(r -> r.nodeName().equals("rerank")).findFirst().orElseThrow();
        assertThat(rerank.rawOutput()).startsWith("ranked=2");
    }

    @Test
    void contextAwareRetrieveReportsQueryAndRankedHits() {
        CapturingRecorder recorder = new CapturingRecorder();
        InMemoryVectorStore store = new InMemoryVectorStore();
        MockEmbeddingClient embeddings = new MockEmbeddingClient(3);
        Retriever retriever = Retriever.of(store, embeddings);
        retriever.upsertText("kb", "doc-1", "tracegraph is a jvm agent runtime", Map.of());

        Graph<String> graph = Graph.<String>builder()
                .node("lookup", (query, ctx) -> {
                    List<Document> docs = retriever.retrieve(ctx, "kb", query, 5);
                    return docs.isEmpty() ? "none" : docs.get(0).id();
                })
                .entry("lookup")
                .terminal("lookup")
                .traceRecorder(recorder)
                .sensitiveDataLogging(true)
                .build();

        ExecutionResult<String> result = graph.run("what is tracegraph?");

        assertThat(result.finalState()).isEqualTo("doc-1");
        assertThat(recorder.rawIO).hasSize(1);
        assertThat(recorder.rawIO.get(0).rawInput()).isEqualTo("what is tracegraph?");
        assertThat(recorder.rawIO.get(0).rawOutput()).startsWith("hits=1 doc-1:");
    }

    @Test
    void describeHitsFormatsIdAndScore() {
        String described = Retriever.describeHits(List.of(
                new Document("a", "t", Map.of(), 0.91234f),
                new Document("b", "t", Map.of(), 0.5f)));
        assertThat(described).isEqualTo("hits=2 a:0.9123 b:0.5000");
    }
}
