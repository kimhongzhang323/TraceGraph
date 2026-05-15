package io.tracegraph.rag.ingest;

import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionPipelineTest {

    /** VectorStore that records every upsert call. */
    private static VectorStore capturing(List<Map<String, String>> sink) {
        return new VectorStore() {
            @Override public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
                sink.add(metadata);
            }
            @Override public List<VectorStore.VectorMatch> query(String scope, float[] embedding, int topK) { return List.of(); }
            @Override public void delete(String scope, String id) {}
        };
    }

    @Test
    void splitsAndStoresAllChunks() {
        List<Map<String, String>> stored = new ArrayList<>();

        int count = IngestionPipeline.builder()
                .loader(() -> List.of(Document.of("doc1", "First paragraph.\n\nSecond paragraph.")))
                .splitter(RecursiveCharacterSplitter.builder().chunkSize(30).chunkOverlap(0).build())
                .embeddingClient(texts -> texts.stream().map(t -> new float[]{1.0f}).toList())
                .vectorStore(capturing(stored))
                .build()
                .run();

        assertThat(count).isGreaterThan(0);
        assertThat(stored).hasSize(count);
    }

    @Test
    void propagatesSourceIdMetadata() {
        List<Map<String, String>> capturedMeta = new ArrayList<>();

        IngestionPipeline.builder()
                .loader(() -> List.of(Document.of("my-source", "Short text.")))
                .splitter(text -> List.of(text))
                .embeddingClient(texts -> texts.stream().map(t -> new float[]{0.5f}).toList())
                .vectorStore(capturing(capturedMeta))
                .build()
                .run();

        assertThat(capturedMeta).hasSize(1);
        assertThat(capturedMeta.get(0)).containsEntry("source_id", "my-source");
    }

    @Test
    void rejectsBuilderWithoutLoader() {
        assertThatThrownBy(() -> IngestionPipeline.builder()
                .embeddingClient(t -> List.of())
                .vectorStore(VectorStore.noop())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("loader");
    }
}
