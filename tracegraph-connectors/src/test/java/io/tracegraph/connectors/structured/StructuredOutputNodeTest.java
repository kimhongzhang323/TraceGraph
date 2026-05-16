package io.tracegraph.connectors.structured;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputNodeTest {

    record Sentiment(String label, double score) {}

    record AgentState(String input, Sentiment sentiment) {}

    private static LlmRequest requestFor(String model, String userMsg) {
        return LlmRequest.builder()
                .model(model)
                .messages(List.of(ChatMessage.user(userMsg)))
                .build();
    }

    private static LlmResponse responseWith(String content) {
        return new LlmResponse(content, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
    }

    private static ExecutionResult<AgentState> run(StructuredOutputNode<AgentState, Sentiment> node,
                                                    AgentState initial) {
        Graph<AgentState> g = Graph.<AgentState>builder()
                .node("extract", node)
                .entry("extract").terminal("extract")
                .build();
        return g.run(initial);
    }

    @Test
    void parsesResponseAndFoldsIntoState() {
        LlmClient client = req -> responseWith("{\"label\":\"positive\",\"score\":0.95}");

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .build();

        ExecutionResult<AgentState> result = run(node, new AgentState("great product", null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().sentiment().label()).isEqualTo("positive");
        assertThat(result.finalState().sentiment().score()).isEqualTo(0.95);
    }

    @Test
    void retriesOnParseFailureAndSucceedsOnSecondAttempt() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = req -> {
            if (calls.incrementAndGet() == 1) return responseWith("not json");
            return responseWith("{\"label\":\"neutral\",\"score\":0.5}");
        };

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .maxRetries(1)
                .build();

        ExecutionResult<AgentState> result = run(node, new AgentState("okay", null));

        assertThat(result.finalState().sentiment().label()).isEqualTo("neutral");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void throwsAfterExhaustingRetries() {
        LlmClient client = req -> responseWith("bad");

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .maxRetries(1)
                .build();

        ExecutionResult<AgentState> result = run(node, new AgentState("x", null));
        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error().getCause()).isInstanceOf(StructuredOutputException.class);
    }

    @Test
    void defaultMaxRetriesIsOne() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = req -> {
            calls.incrementAndGet();
            return responseWith("bad");
        };

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .build();

        ExecutionResult<AgentState> result = run(node, new AgentState("x", null));
        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error().getCause()).isInstanceOf(StructuredOutputException.class);
        // default maxRetries=1: 1 initial + 1 retry = 2 total calls
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void injectsJsonSchemaSystemMessage() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = req -> {
            calls.incrementAndGet();
            boolean hasSchemaInstruction = req.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.SYSTEM
                                   && m.content().contains("JSON"));
            assertThat(hasSchemaInstruction).isTrue();
            return responseWith("{\"label\":\"pos\",\"score\":0.9}");
        };

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .build();

        run(node, new AgentState("test", null));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void stripsMarkdownCodeFences() {
        LlmClient client = req -> responseWith("```json\n{\"label\":\"neg\",\"score\":0.1}\n```");

        StructuredOutputNode<AgentState, Sentiment> node = StructuredOutputNode
                .<AgentState, Sentiment>builder()
                .client(client)
                .outputType(Sentiment.class)
                .requestFactory(s -> requestFor("m", s.input()))
                .resultFolder((s, t) -> new AgentState(s.input(), t))
                .build();

        ExecutionResult<AgentState> result = run(node, new AgentState("bad", null));
        assertThat(result.finalState().sentiment().label()).isEqualTo("neg");
    }
}
