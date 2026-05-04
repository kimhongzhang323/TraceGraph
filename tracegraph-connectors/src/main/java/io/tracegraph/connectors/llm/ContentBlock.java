package io.tracegraph.connectors.llm;

import java.util.Objects;

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
