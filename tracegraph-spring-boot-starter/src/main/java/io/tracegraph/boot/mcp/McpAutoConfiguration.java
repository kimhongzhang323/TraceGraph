package io.tracegraph.boot.mcp;

import io.tracegraph.connectors.mcp.McpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(McpClient.class)
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tracegraph.mcp", name = "base-uri")
    public McpClient mcpClient(McpProperties props) {
        return McpClient.builder()
                .baseUri(props.getBaseUri())
                .build();
    }
}
