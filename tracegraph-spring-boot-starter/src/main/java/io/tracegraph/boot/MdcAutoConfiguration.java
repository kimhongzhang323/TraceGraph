package io.tracegraph.boot;

import io.tracegraph.observability.MdcNodeListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = TraceGraphAutoConfiguration.class)
@ConditionalOnClass({MDC.class, MdcNodeListener.class})
@ConditionalOnProperty(prefix = "tracegraph.mdc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MdcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MdcNodeListener.class)
    public MdcNodeListener mdcNodeListener() {
        return new MdcNodeListener();
    }
}
