package io.tracegraph.boot.llm;

import io.tracegraph.connectors.llm.AnthropicLlmClient;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.MockLlmClient;
import io.tracegraph.connectors.llm.OpenAiLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LlmAutoConfiguration.class));

    @Test
    void registersOpenAiClientWhenProviderIsOpenai() {
        runner.withPropertyValues(
                "tracegraph.llm.provider=openai",
                "tracegraph.llm.api-key=sk-test"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(LlmClient.class);
            assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiLlmClient.class);
        });
    }

    @Test
    void registersAnthropicClientWhenProviderIsAnthropic() {
        runner.withPropertyValues(
                "tracegraph.llm.provider=anthropic",
                "tracegraph.llm.api-key=sk-ant-test"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(LlmClient.class);
            assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(AnthropicLlmClient.class);
        });
    }

    @Test
    void registersNothingWhenProviderUnset() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void registersNothingWhenDisabled() {
        runner.withPropertyValues(
                "tracegraph.llm.enabled=false",
                "tracegraph.llm.provider=openai"
        ).run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClient.class));
    }

    @Test
    void userLlmClientOverridesAutoConfigured() {
        LlmClient custom = MockLlmClient.constant("hi");
        runner.withPropertyValues(
                "tracegraph.llm.provider=openai",
                "tracegraph.llm.api-key=sk-test"
        ).withBean("customLlm", LlmClient.class, () -> custom).run(ctx -> {
            assertThat(ctx).hasSingleBean(LlmClient.class);
            assertThat(ctx.getBean(LlmClient.class)).isSameAs(custom);
        });
    }
}
