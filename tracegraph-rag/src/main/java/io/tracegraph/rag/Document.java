package io.tracegraph.rag;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable value carrier for a retrieved or stored document.
 *
 * @param id       unique identifier
 * @param text     raw text content
 * @param metadata arbitrary string attributes
 * @param score    similarity score from a vector query; 0f when not from a query
 */
public record Document(String id, String text, Map<String, String> metadata, float score) {

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Document of(String id, String text) {
        return new Document(id, text, Map.of(), 0f);
    }

    public static Document of(String id, String text, Map<String, String> metadata) {
        return new Document(id, text, metadata, 0f);
    }
}
