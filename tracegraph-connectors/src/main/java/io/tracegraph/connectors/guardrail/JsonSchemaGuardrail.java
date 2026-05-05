package io.tracegraph.connectors.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.Guardrail;
import io.tracegraph.core.spi.GuardrailVerdict;

import java.util.List;

/**
 * Blocks input that is not valid JSON or is missing required top-level fields.
 *
 * <p>Jackson ({@code jackson-databind}) must be on the classpath. It is already an
 * {@code <optional>} dependency of {@code tracegraph-connectors}.
 */
public final class JsonSchemaGuardrail implements Guardrail<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> requiredFields;

    public JsonSchemaGuardrail(List<String> requiredFields) {
        this.requiredFields = List.copyOf(requiredFields);
    }

    @Override
    public GuardrailVerdict check(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailVerdict.block("Input is null or blank — not valid JSON");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(input);
        } catch (Exception e) {
            return GuardrailVerdict.block("Input is not valid JSON: " + e.getMessage());
        }
        for (String field : requiredFields) {
            if (!root.has(field)) {
                return GuardrailVerdict.block("Required field missing: " + field);
            }
        }
        return GuardrailVerdict.allow();
    }
}
