package io.tracegraph.connectors.tool.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a parameter of a {@link ToolMethod}-annotated method with metadata
 * that is reflected into the tool's JSON schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /** Human-readable description of the parameter. */
    String description() default "";

    /** Whether the parameter is required in the JSON schema. */
    boolean required() default true;
}
