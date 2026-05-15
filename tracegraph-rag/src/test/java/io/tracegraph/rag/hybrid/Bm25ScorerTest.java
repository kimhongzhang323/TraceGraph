package io.tracegraph.rag.hybrid;

import io.tracegraph.rag.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Bm25ScorerTest {

    private static final Document DOC_A = Document.of("a", "the quick brown fox jumps over the lazy dog");
    private static final Document DOC_B = Document.of("b", "java is a programming language for the JVM");
    private static final Document DOC_C = Document.of("c", "machine learning with neural networks");

    private Bm25Scorer scorer() {
        return Bm25Scorer.builder()
                .addDocuments(List.of(DOC_A, DOC_B, DOC_C))
                .build();
    }

    @Test
    void ranksRelevantDocumentFirst() {
        List<Document> results = scorer().score("java programming", 3);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo("b");
    }

    @Test
    void returnsEmptyOnBlankQuery() {
        assertThat(scorer().score("  ", 5)).isEmpty();
    }

    @Test
    void returnsEmptyOnEmptyCorpus() {
        Bm25Scorer empty = Bm25Scorer.builder().build();
        assertThat(empty.score("anything", 5)).isEmpty();
    }

    @Test
    void respectsTopKLimit() {
        List<Document> results = scorer().score("the", 2);
        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void scoresArePositive() {
        List<Document> results = scorer().score("fox", 3);
        assertThat(results).allSatisfy(d -> assertThat(d.score()).isGreaterThan(0f));
    }

    @Test
    void rejectsInvalidK1() {
        assertThatThrownBy(() -> Bm25Scorer.builder().k1(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidB() {
        assertThatThrownBy(() -> Bm25Scorer.builder().b(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
