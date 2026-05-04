package io.tracegraph.rag;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.core.spi.VectorStore.VectorMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ties an {@link EmbeddingClient} and a {@link VectorStore} together for ergonomic RAG workflows.
 *
 * <p>Text is stored in metadata under the key {@code __text__} so it survives the vector-store
 * round-trip without requiring a separate document store.
 */
public final class Retriever {

    static final String TEXT_KEY = "__text__";

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    private Retriever(VectorStore vectorStore, EmbeddingClient embeddingClient) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
    }

    public static Retriever of(VectorStore vectorStore, EmbeddingClient embeddingClient) {
        return new Retriever(vectorStore, embeddingClient);
    }

    public List<Document> retrieve(String scope, String query, int topK) {
        List<float[]> embeddings = embeddingClient.embed(List.of(query));
        float[] embedding = embeddings.get(0);
        List<VectorMatch> matches = vectorStore.query(scope, embedding, topK);
        List<Document> docs = new ArrayList<>(matches.size());
        for (VectorMatch match : matches) {
            String text = match.metadata().getOrDefault(TEXT_KEY, "");
            docs.add(new Document(match.id(), text, match.metadata(), match.score()));
        }
        return docs;
    }

    public void upsert(String scope, String id, String text, float[] embedding,
                       Map<String, String> metadata) {
        vectorStore.upsert(scope, id, embedding, metadata);
    }

    public void upsertText(String scope, String id, String text,
                           Map<String, String> extraMetadata) {
        List<float[]> embeddings = embeddingClient.embed(List.of(text));
        float[] embedding = embeddings.get(0);
        Map<String, String> merged = new HashMap<>(extraMetadata == null ? Map.of() : extraMetadata);
        merged.put(TEXT_KEY, text);
        vectorStore.upsert(scope, id, embedding, merged);
    }
}
