package io.tracegraph.rag.hybrid;

import io.tracegraph.rag.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BM25 (Okapi BM25) scorer for keyword-based retrieval over an in-memory corpus.
 *
 * <p>BM25 is the standard TF-IDF variant used by Elasticsearch, Lucene, and most search engines.
 * This implementation is stateless with respect to any external system — build a {@link Bm25Scorer}
 * from a fixed corpus, then call {@link #score(String, int)} to retrieve the top-K documents by
 * keyword relevance.
 *
 * <p>Tokenisation is whitespace + punctuation split, lower-cased. Stop words are not removed —
 * add filtering upstream if needed.
 *
 * <p>Default parameters: {@code k1 = 1.2}, {@code b = 0.75} (Elasticsearch defaults).
 */
public final class Bm25Scorer {

    private final double k1;
    private final double b;
    private final List<Document> corpus;
    private final List<Map<String, Integer>> termFreqs;
    private final Map<String, Integer> docFreqs;
    private final double avgDocLen;

    private Bm25Scorer(Builder builder) {
        this.k1 = builder.k1;
        this.b = builder.b;
        this.corpus = List.copyOf(builder.corpus);
        this.termFreqs = new ArrayList<>(corpus.size());
        this.docFreqs = new HashMap<>();

        double totalLen = 0;
        for (Document doc : corpus) {
            Map<String, Integer> tf = termFrequencies(doc.text());
            termFreqs.add(tf);
            totalLen += tf.values().stream().mapToInt(Integer::intValue).sum();
            for (String term : tf.keySet()) {
                docFreqs.merge(term, 1, Integer::sum);
            }
        }
        this.avgDocLen = corpus.isEmpty() ? 1.0 : totalLen / corpus.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the top-{@code k} documents scored by BM25 for {@code query}.
     *
     * @param query raw query text
     * @param k     maximum results
     * @return documents in descending score order; scores stored in {@link Document#score()}
     */
    public List<Document> score(String query, int k) {
        if (corpus.isEmpty() || query == null || query.isBlank()) return List.of();
        String[] queryTerms = tokenise(query);
        int n = corpus.size();

        double[] scores = new double[n];
        for (String term : queryTerms) {
            int df = docFreqs.getOrDefault(term, 0);
            if (df == 0) continue;
            double idf = Math.log((n - df + 0.5) / (df + 0.5) + 1.0);
            for (int i = 0; i < n; i++) {
                int tf = termFreqs.get(i).getOrDefault(term, 0);
                if (tf == 0) continue;
                double docLen = termFreqs.get(i).values().stream().mapToInt(Integer::intValue).sum();
                double tfNorm = (tf * (k1 + 1))
                        / (tf + k1 * (1 - b + b * docLen / avgDocLen));
                scores[i] += idf * tfNorm;
            }
        }

        List<int[]> indexed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) indexed.add(new int[]{i});
        indexed.sort((a, b2) -> Double.compare(scores[b2[0]], scores[a[0]]));

        List<Document> result = new ArrayList<>(Math.min(k, n));
        for (int i = 0; i < Math.min(k, n); i++) {
            int idx = indexed.get(i)[0];
            if (scores[idx] <= 0) break;
            Document d = corpus.get(idx);
            result.add(new Document(d.id(), d.text(), d.metadata(), (float) scores[idx]));
        }
        return List.copyOf(result);
    }

    private static Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> tf = new LinkedHashMap<>();
        for (String token : tokenise(text)) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    private static String[] tokenise(String text) {
        return text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
    }

    public static final class Builder {
        private double k1 = 1.2;
        private double b = 0.75;
        private final List<Document> corpus = new ArrayList<>();

        private Builder() {}

        public Builder k1(double k1) {
            if (k1 < 0) throw new IllegalArgumentException("k1 must be >= 0");
            this.k1 = k1;
            return this;
        }

        public Builder b(double b) {
            if (b < 0 || b > 1) throw new IllegalArgumentException("b must be in [0,1]");
            this.b = b;
            return this;
        }

        public Builder addDocument(Document doc) {
            corpus.add(doc);
            return this;
        }

        public Builder addDocuments(List<Document> docs) {
            corpus.addAll(docs);
            return this;
        }

        public Bm25Scorer build() {
            return new Bm25Scorer(this);
        }
    }
}
