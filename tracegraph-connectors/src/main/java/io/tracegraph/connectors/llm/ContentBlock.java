package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

// Jackson annotations keep RecordedLlmClient.save/load round-trips type-safe; Jackson stays an
// optional dependency — annotations are ignored by the JVM when Jackson is absent at runtime.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image")
})
public sealed interface ContentBlock permits ContentBlock.TextBlock, ContentBlock.ImageBlock {

    record TextBlock(String text) implements ContentBlock {
        public TextBlock {
            Objects.requireNonNull(text, "text");
        }
    }

    record ImageBlock(String mimeType, String base64Data) implements ContentBlock {
        public ImageBlock {
            Objects.requireNonNull(mimeType, "mimeType");
            Objects.requireNonNull(base64Data, "base64Data");
            if (base64Data.isBlank()) {
                throw new IllegalArgumentException("base64Data must not be blank");
            }
        }
    }

    static ContentBlock text(String text) {
        return new TextBlock(text);
    }

    static ContentBlock image(String mimeType, String base64Data) {
        return new ImageBlock(mimeType, base64Data);
    }
}
