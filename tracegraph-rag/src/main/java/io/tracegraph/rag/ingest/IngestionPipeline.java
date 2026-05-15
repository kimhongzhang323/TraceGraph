package io.tracegraph.rag.ingest;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent pipeline that loads, splits, embeds, and stores documents in one call.
 *
 * <pre>{@code
 * IngestionPipeline.builder()
 *     .loader(myLoader)
 *     .splitter(RecursiveCharacterSplitter.defaults())
 *     .embeddingClient(embeddingClient)
 *     .vectorStore(vectorStore)
 *     .build()
 *     .run();
 * }</pre>
 *
 * <p>Each source document is split into chunks; each chunk is embedded and stored as a separate
 * entry in the {@link VectorStore}, inheriting the source document's metadata plus a
 * {@code source_id} key.
 */
public final class IngestionPipeline {

    private final DocumentLoader loader;
    private final TextSplitter splitter;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    private IngestionPipeline(Builder b) {
        this.loader = b.loader;
        this.splitter = b.splitter;
        this.embeddingClient = b.embeddingClient;
        this.vectorStore = b.vectorStore;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Execute the full load → split → embed → store pipeline.
     *
     * @return total number of chunks stored
     */
    public int run() {
        List<Document> docs = loader.load();
        List<Document> chunks = new ArrayList<>();

        for (Document doc : docs) {
            List<String> parts = splitter.split(doc.text());
            for (int i = 0; i < parts.size(); i++) {
                Map<String, String> meta = new HashMap<>(doc.metadata());
                meta.put("source_id", doc.id());
                meta.put("chunk_index", String.valueOf(i));
                chunks.add(new Document(UUID.randomUUID().toString(), parts.get(i), meta, 0f));
            }
        }

        // Batch-embed all chunk texts in one call
        List<String> texts = chunks.stream().map(Document::text).toList();
        List<float[]> embeddings = embeddingClient.embed(texts);

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            float[] embedding = i < embeddings.size() ? embeddings.get(i) : new float[0];
            vectorStore.upsert("default", chunk.id(), embedding, chunk.metadata());
        }

        return chunks.size();
    }

    public static final class Builder {
        private DocumentLoader loader;
        private TextSplitter splitter = RecursiveCharacterSplitter.defaults();
        private EmbeddingClient embeddingClient;
        private VectorStore vectorStore;

        private Builder() {}

        public Builder loader(DocumentLoader loader) {
            this.loader = Objects.requireNonNull(loader, "loader");
            return this;
        }

        public Builder splitter(TextSplitter splitter) {
            this.splitter = Objects.requireNonNull(splitter, "splitter");
            return this;
        }

        public Builder embeddingClient(EmbeddingClient embeddingClient) {
            this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
            return this;
        }

        public Builder vectorStore(VectorStore vectorStore) {
            this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
            return this;
        }

        public IngestionPipeline build() {
            Objects.requireNonNull(loader, "loader is required");
            Objects.requireNonNull(embeddingClient, "embeddingClient is required");
            Objects.requireNonNull(vectorStore, "vectorStore is required");
            return new IngestionPipeline(this);
        }
    }
}
