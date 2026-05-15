package io.tracegraph.connectors.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    @Test
    void rendersAllVariables() {
        PromptTemplate t = PromptTemplate.of("Summarize {topic} in {language}.");
        String result = t.render(Map.of("topic", "AI safety", "language", "English"));
        assertThat(result).isEqualTo("Summarize AI safety in English.");
    }

    @Test
    void rendersNoVariables() {
        assertThat(PromptTemplate.of("Hello world.").render(Map.of()))
                .isEqualTo("Hello world.");
    }

    @Test
    void throwsOnMissingVariable() {
        PromptTemplate t = PromptTemplate.of("Hello {name}!");
        assertThatThrownBy(() -> t.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void fluentBuilderRendersCorrectly() {
        String result = PromptTemplate.of("Translate {text} to {lang}.")
                .with("text", "hello")
                .with("lang", "French")
                .render();
        assertThat(result).isEqualTo("Translate hello to French.");
    }

    @Test
    void equalTemplatesAreEqual() {
        assertThat(PromptTemplate.of("Hello {name}"))
                .isEqualTo(PromptTemplate.of("Hello {name}"));
    }

    @Test
    void differentTemplatesAreNotEqual() {
        assertThat(PromptTemplate.of("Hello {name}"))
                .isNotEqualTo(PromptTemplate.of("Hi {name}"));
    }

    @Test
    void repeatedVariableSubstitutedEachTime() {
        String result = PromptTemplate.of("{x} + {x} = two {x}s")
                .render(Map.of("x", "cat"));
        assertThat(result).isEqualTo("cat + cat = two cats");
    }

    @Test
    void rejectsNullTemplate() {
        assertThatThrownBy(() -> PromptTemplate.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullVariablesMap() {
        assertThatThrownBy(() -> PromptTemplate.of("Hello").render(null))
                .isInstanceOf(NullPointerException.class);
    }
}
