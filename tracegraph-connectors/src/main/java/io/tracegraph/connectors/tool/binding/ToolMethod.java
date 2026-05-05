package io.tracegraph.connectors.tool.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an LLM-callable tool.
 * <p>
 * Use together with {@link ToolParam} on parameters and {@link ToolMethodAdapter}
 * to produce a {@link io.tracegraph.connectors.llm.ToolDefinition} +
 * {@link io.tracegraph.connectors.llm.Tool} pair without writing boilerplate.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolMethod {

    /** Tool name; defaults to the method name when blank. */
    String name() default "";

    /** Human-readable description shown to the LLM. */
    String description() default "";
}
