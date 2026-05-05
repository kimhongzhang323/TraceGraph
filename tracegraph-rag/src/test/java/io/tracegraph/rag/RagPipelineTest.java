package io.tracegraph.rag;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPipelineTest {

    @Test
    void pipelineRetrievesReranksAndStuffsContext() {
        RagPipeline pipeline = RagPipeline.builder()
                .retriever(query -> List.of("Java is a language", "Python is also a language"))
                .reranker(new ScoreThresholdReranker(0.0))
                .build();

        ExecutionResult<RagState> result = pipeline.graph().run(RagState.of("Java language"));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().context()).contains("Java is a language");
    }

    @Test
    void pipelineFiltersLowScoredChunks() {
        RagPipeline pipeline = RagPipeline.builder()
                .retriever(query -> List.of("Java is a language", "irrelevant content here"))
                .reranker(new ScoreThresholdReranker(0.5))
                .build();

        ExecutionResult<RagState> result = pipeline.graph().run(RagState.of("Java language"));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().context()).contains("Java is a language");
        assertThat(result.finalState().context()).doesNotContain("irrelevant");
    }
}
