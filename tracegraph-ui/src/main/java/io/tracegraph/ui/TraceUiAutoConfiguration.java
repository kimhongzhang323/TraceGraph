package io.tracegraph.ui;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the TraceGraph UI module.
 * Registers the graph render and complexity controllers when the UI is enabled
 * and a web application context is present.
 */
@AutoConfiguration(afterName = "io.tracegraph.boot.web.TraceWebAutoConfiguration")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "tracegraph.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({GraphRenderController.class, ComplexityController.class})
public class TraceUiAutoConfiguration {
}
