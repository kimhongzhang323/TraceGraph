package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.boot.TraceGraphProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = TraceGraphAutoConfiguration.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(TraceGraphProperties.class)
public class CorsAutoConfiguration {

    @Bean("tracegraphCorsConfigurer")
    @ConditionalOnMissingBean(name = "tracegraphCorsConfigurer")
    public WebMvcConfigurer tracegraphCorsConfigurer(TraceGraphProperties props) {
        TraceGraphProperties.Web.Cors cors = props.getWeb().getCors();
        if (!cors.isEnabled()) {
            return new WebMvcConfigurer() {};
        }
        List<String> origins = Arrays.asList(cors.getAllowedOrigins().split("\\s*,\\s*"));
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/tracegraph/**")
                        .allowedOrigins(origins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
