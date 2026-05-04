package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentBlockTest {

    @Test
    void textBlockFactory() {
        ContentBlock block = ContentBlock.text("hello");
        assertThat(block).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) block).text()).isEqualTo("hello");
    }

    @Test
    void imageBlockFactory() {
        ContentBlock block = ContentBlock.image("image/png", "abc123");
        assertThat(block).isInstanceOf(ContentBlock.ImageBlock.class);
        ContentBlock.ImageBlock ib = (ContentBlock.ImageBlock) block;
        assertThat(ib.mimeType()).isEqualTo("image/png");
        assertThat(ib.base64Data()).isEqualTo("abc123");
    }

    @Test
    void imageBlockRejectsBlankBase64() {
        assertThatThrownBy(() -> ContentBlock.image("image/png", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base64Data");
    }
}
