package io.tracegraph.connectors.tool.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.connectors.llm.Tool;
import io.tracegraph.connectors.llm.ToolDefinition;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scans an object for {@link ToolMethod}-annotated methods and produces
 * {@link RegisteredTool} pairs containing the {@link ToolDefinition} and a
 * reflective {@link Tool} implementation — no boilerplate required.
 * <p>
 * Requires Jackson ({@code jackson-databind}) on the classpath; it is an
 * {@code <optional>} dependency of {@code tracegraph-connectors}.
 */
public final class ToolMethodAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolMethodAdapter() {}

    /**
     * A {@link ToolDefinition} + {@link Tool} pair produced by scanning a
     * {@link ToolMethod}-annotated method.
     *
     * @param definition the tool metadata (name, description, JSON schema)
     * @param tool       the reflective executor that parses JSON args and invokes
     *                   the underlying method
     */
    public record RegisteredTool(ToolDefinition definition, Tool tool) {}

    /**
     * Scans all public {@link ToolMethod}-annotated methods on {@code target}
     * and returns one {@link RegisteredTool} per method.
     *
     * @param target the object whose public methods are scanned
     * @return list of registered tools in declaration order
     */
    public static List<RegisteredTool> fromObject(Object target) {
        List<RegisteredTool> result = new ArrayList<>();
        for (Method method : target.getClass().getMethods()) {
            if (method.isAnnotationPresent(ToolMethod.class)) {
                result.add(fromMethod(target, method));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Produces a {@link RegisteredTool} for a single {@link ToolMethod}-annotated
     * method on {@code target}.
     *
     * @param target the object on which the method will be invoked
     * @param method the method to adapt (must carry {@link ToolMethod})
     * @return the registered tool
     * @throws IllegalArgumentException if the method is not annotated with
     *                                  {@link ToolMethod}
     */
    public static RegisteredTool fromMethod(Object target, Method method) {
        ToolMethod annotation = method.getAnnotation(ToolMethod.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Method " + method.getName() + " is not annotated with @ToolMethod");
        }

        String name = annotation.name().isBlank() ? method.getName() : annotation.name();
        String description = annotation.description();

        String schema = buildSchema(method);
        Map<String, Object> schemaMap = parseSchemaToMap(schema);
        ToolDefinition definition = new ToolDefinition(name, description, schemaMap);

        Tool tool = args -> invokeMethod(target, method, args);
        return new RegisteredTool(definition, tool);
    }

    private static String buildSchema(Method method) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();
        ArrayNode required = MAPPER.createArrayNode();

        for (Parameter param : method.getParameters()) {
            String paramName = param.getName();
            ObjectNode propNode = MAPPER.createObjectNode();
            propNode.put("type", jsonType(param.getType()));

            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isBlank()) {
                propNode.put("description", toolParam.description());
            }
            properties.set(paramName, propNode);

            boolean isRequired = toolParam == null || toolParam.required();
            if (isRequired) {
                required.add(paramName);
            }
        }

        root.set("properties", properties);
        if (!required.isEmpty()) {
            root.set("required", required);
        }

        return root.toString();
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        return "string";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSchemaToMap(String schema) {
        try {
            return MAPPER.readValue(schema, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse generated schema", e);
        }
    }

    private static String invokeMethod(Object target, Method method, String args) {
        try {
            JsonNode argsNode = (args == null || args.isBlank())
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(args);

            Parameter[] params = method.getParameters();
            Object[] values = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                JsonNode valueNode = argsNode.get(paramName);
                values[i] = coerce(valueNode, params[i].getType());
            }

            Object result = method.invoke(target, values);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
            return "{\"error\":\"" + escapeJson(msg) + "\"}";
        }
    }

    private static Object coerce(JsonNode node, Class<?> type) {
        if (node == null || node.isNull()) {
            return defaultValue(type);
        }
        if (type == String.class) {
            return node.asText();
        }
        if (type == int.class || type == Integer.class) {
            return node.asInt();
        }
        if (type == long.class || type == Long.class) {
            return node.asLong();
        }
        if (type == double.class || type == Double.class) {
            return node.asDouble();
        }
        if (type == float.class || type == Float.class) {
            return (float) node.asDouble();
        }
        if (type == boolean.class || type == Boolean.class) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private static Object defaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
