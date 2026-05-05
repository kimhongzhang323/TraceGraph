package io.tracegraph.connectors.tool.binding;

import io.tracegraph.connectors.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolMethodAdapterTest {

    // --- Simple target class ---

    static class Calculator {

        @ToolMethod(name = "add", description = "Adds two integers")
        public int add(
                @ToolParam(description = "first operand") int a,
                @ToolParam(description = "second operand") int b) {
            return a + b;
        }

        @ToolMethod(description = "Returns a greeting")
        public String greet(@ToolParam(description = "the name to greet") String name) {
            return "Hello, " + name + "!";
        }

        @ToolMethod(name = "divide", description = "Divides a by b")
        public double divide(double a, double b) {
            if (b == 0) throw new ArithmeticException("division by zero");
            return a / b;
        }

        @ToolMethod(name = "toggle", description = "Negates a boolean")
        public boolean toggle(@ToolParam(required = false) boolean value) {
            return !value;
        }

        // Not annotated — should be ignored by fromObject
        public void ignoredMethod() {}
    }

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    // --- fromObject ---

    @Test
    void fromObjectReturnsOneEntryPerAnnotatedMethod() {
        List<ToolMethodAdapter.RegisteredTool> tools = ToolMethodAdapter.fromObject(calculator);
        assertThat(tools).hasSize(4);
    }

    @Test
    void fromObjectUsesExplicitNameAndDescription() {
        List<ToolMethodAdapter.RegisteredTool> tools = ToolMethodAdapter.fromObject(calculator);
        ToolDefinition addDef = tools.stream()
                .map(ToolMethodAdapter.RegisteredTool::definition)
                .filter(d -> d.name().equals("add"))
                .findFirst()
                .orElseThrow();
        assertThat(addDef.description()).isEqualTo("Adds two integers");
    }

    @Test
    void fromObjectDefaultsNameToMethodName() {
        List<ToolMethodAdapter.RegisteredTool> tools = ToolMethodAdapter.fromObject(calculator);
        assertThat(tools.stream().map(t -> t.definition().name()))
                .contains("greet");
    }

    // --- schema generation ---

    @Test
    void schemaHasCorrectTypeForIntParameters() {
        ToolMethodAdapter.RegisteredTool addTool = toolByName("add");
        Map<String, Object> schema = addTool.definition().parametersSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> aProp = (Map<String, Object>) props.get("a");
        assertThat(aProp).containsEntry("type", "integer");
    }

    @Test
    void schemaHasDescriptionFromToolParam() {
        ToolMethodAdapter.RegisteredTool addTool = toolByName("add");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) addTool.definition().parametersSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> aProp = (Map<String, Object>) props.get("a");
        assertThat(aProp).containsEntry("description", "first operand");
    }

    @Test
    void schemaRequiredListContainsRequiredParams() {
        ToolMethodAdapter.RegisteredTool addTool = toolByName("add");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) addTool.definition().parametersSchema().get("required");
        assertThat(required).contains("a", "b");
    }

    @Test
    void schemaOptionalParamNotInRequired() {
        ToolMethodAdapter.RegisteredTool toggleTool = toolByName("toggle");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) toggleTool.definition().parametersSchema().get("required");
        assertThat(required == null || !required.contains("value")).isTrue();
    }

    @Test
    void schemaTypeForString() {
        ToolMethodAdapter.RegisteredTool greetTool = toolByName("greet");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) greetTool.definition().parametersSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
        assertThat(nameProp).containsEntry("type", "string");
    }

    @Test
    void schemaTypeForDouble() {
        ToolMethodAdapter.RegisteredTool divideTool = toolByName("divide");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) divideTool.definition().parametersSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> aProp = (Map<String, Object>) props.get("a");
        assertThat(aProp).containsEntry("type", "number");
    }

    // --- invocation ---

    @Test
    void invokesAddWithJsonArgs() throws Exception {
        ToolMethodAdapter.RegisteredTool addTool = toolByName("add");
        String result = addTool.tool().execute("{\"a\":3,\"b\":5}");
        assertThat(result).isEqualTo("8");
    }

    @Test
    void invokesGreetWithJsonArgs() throws Exception {
        ToolMethodAdapter.RegisteredTool greetTool = toolByName("greet");
        String result = greetTool.tool().execute("{\"name\":\"World\"}");
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void returnsErrorJsonOnException() throws Exception {
        ToolMethodAdapter.RegisteredTool divideTool = toolByName("divide");
        String result = divideTool.tool().execute("{\"a\":1.0,\"b\":0.0}");
        assertThat(result).contains("error");
    }

    @Test
    void invokesToggleWithBooleanArg() throws Exception {
        ToolMethodAdapter.RegisteredTool toggleTool = toolByName("toggle");
        String result = toggleTool.tool().execute("{\"value\":true}");
        assertThat(result).isEqualTo("false");
    }

    // --- fromMethod validation ---

    @Test
    void fromMethodThrowsWhenNotAnnotated() throws NoSuchMethodException {
        java.lang.reflect.Method m = Calculator.class.getMethod("ignoredMethod");
        assertThatThrownBy(() -> ToolMethodAdapter.fromMethod(calculator, m))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private ToolMethodAdapter.RegisteredTool toolByName(String name) {
        return ToolMethodAdapter.fromObject(calculator).stream()
                .filter(t -> t.definition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool named: " + name));
    }
}
