package io.tracegraph.examples.rag;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.LlmResponse.FinishReason;
import io.tracegraph.core.Graph;
import io.tracegraph.rag.InMemoryVectorStore;
import io.tracegraph.rag.MockEmbeddingClient;
import io.tracegraph.rag.Retriever;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RagAgentMain {

    record RagState(String question, List<String> context, String answer) {}

    private static final String SCOPE = "docs";

    public static void main(String[] args) {
        // Set up knowledge base
        var vectorStore = new InMemoryVectorStore();
        var embeddingClient = new MockEmbeddingClient(4);
        var retriever = Retriever.of(vectorStore, embeddingClient);

        retriever.upsertText(SCOPE, "doc1", "TraceGraph is a production-grade JVM agent runtime.", Map.of());
        retriever.upsertText(SCOPE, "doc2", "TraceGraph supports typed graphs, durable memory, and deep observability.", Map.of());
        retriever.upsertText(SCOPE, "doc3", "TraceGraph is built for the JVM using JDK 21 with virtual threads.", Map.of());

        // Mock LLM that synthesizes an answer from context
        var mockClient = (io.tracegraph.connectors.llm.LlmClient) request -> {
            String contextText = request.messages().stream()
                    .filter(m -> m.role() == ChatMessage.Role.SYSTEM)
                    .map(ChatMessage::content)
                    .findFirst().orElse("(no context)");
            return new LlmResponse("Based on context: " + contextText.substring(0, Math.min(50, contextText.length())),
                    FinishReason.STOP, new LlmResponse.Usage(20, 15), List.of());
        };

        Graph<RagState> graph = Graph.<RagState>builder()
                .node("retrieve", (state, ctx) -> {
                    var docs = retriever.retrieve(SCOPE, state.question(), 2);
                    var texts = docs.stream()
                            .map(d -> d.metadata().getOrDefault("__text__", d.id()))
                            .collect(Collectors.toList());
                    return new RagState(state.question(), texts, null);
                })
                .node("answer", (state, ctx) -> {
                    String contextStr = String.join("\n", state.context());
                    var request = new LlmRequest(
                            List.of(
                                    ChatMessage.system(contextStr),
                                    ChatMessage.user(state.question())),
                            "mock-model", 0.0, 512);
                    var response = mockClient.complete(request);
                    return new RagState(state.question(), state.context(), response.content());
                })
                .entry("retrieve")
                .edge("retrieve", "answer")
                .build();

        var result = graph.run(new RagState("What is TraceGraph?", null, null));
        System.out.println("Answer: " + result.finalState().answer());
        System.out.println("Context used: " + result.finalState().context());
    }
}
