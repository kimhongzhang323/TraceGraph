package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link VectorStore} backed by {@link ConcurrentHashMap}. Thread-safe.
 *
 * <p>Similarity is computed as cosine similarity. Zero-norm vectors score 0.
 */
public final class InMemoryVectorStore implements VectorStore {

    private record Entry(float[] embedding, Map<String, String> metadata) {}

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> scopes =
            new ConcurrentHashMap<>();

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        float[] copy = embedding.clone();
        Map<String, String> metaCopy = metadata == null ? Map.of() : Map.copyOf(metadata);
        scopes.computeIfAbsent(scope, s -> new ConcurrentHashMap<>())
              .put(id, new Entry(copy, metaCopy));
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        ConcurrentHashMap<String, Entry> entries = scopes.get(scope);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<VectorMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            float sim = cosineSimilarity(embedding, e.getValue().embedding());
            matches.add(new VectorMatch(e.getKey(), sim, e.getValue().metadata()));
        }
        matches.sort(Comparator.comparingDouble(VectorMatch::score).reversed());
        return List.copyOf(matches.subList(0, Math.min(topK, matches.size())));
    }

    @Override
    public void delete(String scope, String id) {
        ConcurrentHashMap<String, Entry> entries = scopes.get(scope);
        if (entries != null) {
            entries.remove(id);
        }
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
