package io.tracegraph.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieverTest {

    @Test
    void upsertTextAndRetrieveRoundTrip() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        MockEmbeddingClient client = new MockEmbeddingClient(3);
        Retriever retriever = Retriever.of(store, client);

        retriever.upsertText("docs", "doc1", "hello world", Map.of("author", "alice"));

        List<Document> results = retriever.retrieve("docs", "hello world", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo("doc1");
        assertThat(results.get(0).text()).isEqualTo("hello world");
        assertThat(results.get(0).metadata()).containsEntry("author", "alice");
    }
}
