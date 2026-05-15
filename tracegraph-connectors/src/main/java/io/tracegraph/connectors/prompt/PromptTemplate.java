package io.tracegraph.connectors.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable string template with {@code {variable}} placeholder substitution.
 *
 * <p>Variables are enclosed in single braces: {@code {name}}, {@code {topic}}.
 * All declared variables must be supplied at render time or
 * {@link #render(Map)} throws {@link IllegalArgumentException}.
 *
 * <pre>{@code
 * PromptTemplate t = PromptTemplate.of("Summarize {topic} in {language}.");
 * String prompt = t.render(Map.of("topic", "AI safety", "language", "English"));
 * }</pre>
 */
public final class PromptTemplate {

    private static final Pattern VAR = Pattern.compile("\\{\\{([^{}]+)}}|\\{([^{}]+)}");

    private final String id;
    private final String version;
    private final String template;

    private PromptTemplate(String id, String version, String template) {
        this.id = id;
        this.version = version;
        this.template = Objects.requireNonNull(template, "template");
    }

    /** Anonymous template without a registry id or version. */
    public static PromptTemplate of(String template) {
        return new PromptTemplate(null, null, template);
    }

    /** Named and versioned template, typically loaded by {@link PromptLibrary}. */
    public static PromptTemplate of(String id, String version, String template) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        return new PromptTemplate(id, version, template);
    }

    /** Registry id, or {@code null} for anonymous templates. */
    public String id() {
        return id;
    }

    /** Version tag, or {@code null} for anonymous templates. */
    public String version() {
        return version;
    }

    /**
     * Render the template by substituting all {@code {variable}} placeholders.
     *
     * @param variables map from variable name to replacement value
     * @return rendered string
     * @throws IllegalArgumentException if any placeholder has no corresponding entry in the map
     */
    public String render(Map<String, String> variables) {
        Objects.requireNonNull(variables, "variables");
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Missing variable '" + key + "' — required by template: " + template);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Return a fluent builder for single-variable renders without constructing a map.
     *
     * @return builder seeded with this template
     */
    public RenderBuilder with(String variable, String value) {
        return new RenderBuilder(this).with(variable, value);
    }

    public String template() {
        return template;
    }

    /**
     * Return all unique placeholder variable names referenced in this template, in encounter order.
     *
     * @return ordered list of variable names; empty if the template has no placeholders
     */
    public List<String> variables() {
        List<String> vars = new ArrayList<>();
        Matcher m = VAR.matcher(template);
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            if (!vars.contains(key)) vars.add(key);
        }
        return List.copyOf(vars);
    }

    @Override
    public String toString() {
        return id != null ? "PromptTemplate{id=" + id + ", v=" + version + "}" : "PromptTemplate{" + template + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PromptTemplate p)) return false;
        return Objects.equals(id, p.id) && Objects.equals(version, p.version) && template.equals(p.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, template);
    }

    /**
     * Fluent accumulator for variable bindings.
     */
    public static final class RenderBuilder {
        private final PromptTemplate owner;
        private final Map<String, String> vars = new HashMap<>();

        private RenderBuilder(PromptTemplate owner) {
            this.owner = owner;
        }

        public RenderBuilder with(String variable, String value) {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(value, "value");
            vars.put(variable, value);
            return this;
        }

        public String render() {
            return owner.render(vars);
        }
    }
}
