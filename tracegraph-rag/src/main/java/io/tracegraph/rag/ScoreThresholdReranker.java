package io.tracegraph.rag;

import java.util.ArrayList;
import java.util.List;

public final class ScoreThresholdReranker implements Reranker {

    private final double threshold;

    public ScoreThresholdReranker(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0, 1], was " + threshold);
        }
        this.threshold = threshold;
    }

    @Override
    public List<RankedChunk> rerank(String query, List<String> chunks) {
        // Assigns a score based on simple query-term overlap (case-insensitive word match).
        // A real implementation would call a cross-encoder model API.
        String[] queryTerms = query.toLowerCase().split("\\s+");
        List<RankedChunk> ranked = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            String lower = chunk.toLowerCase();
            long matches = 0;
            for (String term : queryTerms) {
                if (lower.contains(term)) matches++;
            }
            double score = queryTerms.length == 0 ? 0.0 : (double) matches / queryTerms.length;
            if (score >= threshold) {
                ranked.add(new RankedChunk(chunk, score));
            }
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return List.copyOf(ranked);
    }
}
