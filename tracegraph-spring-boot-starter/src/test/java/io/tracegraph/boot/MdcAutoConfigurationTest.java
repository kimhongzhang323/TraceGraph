package io.tracegraph.boot;

import io.tracegraph.observability.MdcNodeListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MdcAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MdcAutoConfiguration.class, TraceGraphAutoConfiguration.class));

    @Test
    void registersMdcNodeListenerByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(MdcNodeListener.class));
    }

    @Test
    void doesNotRegisterWhenDisabled() {
        runner.withPropertyValues("tracegraph.mdc.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MdcNodeListener.class));
    }

    @Test
    void userBeanTakesPrecedence() {
        MdcNodeListener custom = new MdcNodeListener("custom.key");
        runner.withBean("customMdc", MdcNodeListener.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(MdcNodeListener.class);
                    assertThat(ctx.getBean(MdcNodeListener.class)).isSameAs(custom);
                });
    }
}
