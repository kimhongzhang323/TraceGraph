package io.tracegraph.examples.react;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import java.util.Map;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.LlmResponse.FinishReason;
import io.tracegraph.connectors.llm.ReActAgent;
import io.tracegraph.connectors.llm.ToolCall;
import io.tracegraph.connectors.llm.ToolDefinition;
import io.tracegraph.connectors.llm.ToolResult;
import io.tracegraph.core.Graph;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReactAgentMain {

    record AgentState(String question, String answer) {}

    public static void main(String[] args) {
        AtomicInteger callCount = new AtomicInteger();

        LlmClient mockClient = request -> {
            if (callCount.getAndIncrement() == 0) {
                // First call: request tool use
                return new LlmResponse("", FinishReason.TOOL_CALLS,
                        new LlmResponse.Usage(10, 5),
                        List.of(new ToolCall("call-1", "calculator", "{\"expression\":\"6 * 7\"}")));
            }
            // Second call: return final answer
            return new LlmResponse("The answer is 42", FinishReason.STOP,
                    new LlmResponse.Usage(15, 8), List.of());
        };

        ToolDefinition calculatorDef = ToolDefinition.of(
                "calculator",
                "Evaluates a simple math expression like '2 + 3'",
                Map.of("type", "object", "properties",
                        Map.of("expression", Map.of("type", "string"))));

        Graph<AgentState> graph = ReActAgent.<AgentState>builder()
                .client(mockClient)
                .tool(calculatorDef, args2 -> evalExpression(args2))
                .requestFactory(state -> LlmRequest.builder()
                        .messages(List.of(ChatMessage.user(state.question())))
                        .model("mock-model")
                        .temperature(0.0)
                        .maxTokens(1024))
                .responseFolder((state, response) ->
                        new AgentState(state.question(), response.content()))
                .toolResultFolder((state, results) ->
                        new AgentState(state.question(),
                                results.isEmpty() ? "" : results.get(0).content()))
                .build()
                .buildGraph();

        var result = graph.run(new AgentState("What is 6 times 7?", null));
        System.out.println("Answer: " + result.finalState().answer());
    }

    private static String evalExpression(String argsJson) {
        // Parse {"expression":"6 * 7"} and evaluate
        String expr = argsJson.replaceAll(".*\"expression\"\\s*:\\s*\"([^\"]+)\".*", "$1").trim();
        String[] parts = expr.split("\\s*[+\\-*/]\\s*");
        if (parts.length != 2) return "Cannot evaluate: " + expr;
        double a = Double.parseDouble(parts[0].trim());
        double b = Double.parseDouble(parts[1].trim());
        char op = expr.replaceAll("[^+\\-*/]", "").charAt(0);
        double result = switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> a / b;
            default -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
        return result % 1 == 0 ? String.valueOf((long) result) : String.valueOf(result);
    }
}
