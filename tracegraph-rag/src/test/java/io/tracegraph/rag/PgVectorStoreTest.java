package io.tracegraph.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgVectorStoreTest {

    @Test
    void toVectorLiteralFormatsCorrectly() {
        String lit = PgVectorStore.toVectorLiteral(new float[]{1.0f, -0.5f, 0.25f});
        assertThat(lit).isEqualTo("[1.0,-0.5,0.25]");
    }

    @Test
    void serializeMetaProducesJsonObject() {
        String json = PgVectorStore.serializeMeta(Map.of("k", "v"));
        assertThat(json).contains("\"k\"").contains("\"v\"");
    }

    @Test
    void serializeMetaReturnsEmptyObjectForNull() {
        assertThat(PgVectorStore.serializeMeta(null)).isEqualTo("{}");
    }

    @Test
    void deserializeMetaRoundTrips() {
        String json = PgVectorStore.serializeMeta(Map.of("a", "1", "b", "hello"));
        Map<String, String> result = PgVectorStore.deserializeMeta(json);
        assertThat(result).containsEntry("a", "1").containsEntry("b", "hello");
    }

    @Test
    void deserializeMetaHandlesEmptyJson() {
        assertThat(PgVectorStore.deserializeMeta("{}")).isEmpty();
        assertThat(PgVectorStore.deserializeMeta(null)).isEmpty();
        assertThat(PgVectorStore.deserializeMeta("")).isEmpty();
    }

    @Test
    void deserializeMetaHandlesEscapedQuotes() {
        String json = "{\"key\":\"val\\\"ue\"}";
        Map<String, String> result = PgVectorStore.deserializeMeta(json);
        assertThat(result).containsEntry("key", "val\"ue");
    }

    @Test
    void ofRequiresDataSource() {
        assertThatThrownBy(() -> PgVectorStore.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
