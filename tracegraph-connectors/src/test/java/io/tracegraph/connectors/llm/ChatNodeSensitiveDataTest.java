package io.tracegraph.connectors.llm;

import io.tracegraph.core.Context;
import io.tracegraph.core.Graph;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.VectorStore;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatNodeSensitiveDataTest {

    record State(String input, String output) {}

    /** Minimal Context that captures reportRawIO calls and exposes sensitiveDataLoggingEnabled. */
    static final class CapturingContext implements Context {
        private final boolean sensitiveLogging;
        final List<String[]> rawIoCalls = new ArrayList<>();

        CapturingContext(boolean sensitiveLogging) {
            this.sensitiveLogging = sensitiveLogging;
        }

        @Override public String executionId() { return "test-eid"; }
        @Override public String nodeName() { return "chat"; }
        @Override public int attempt() { return 1; }
        @Override public Logger logger() { return LoggerFactory.getLogger("test"); }
        @Override public MemoryStore memory() { return MemoryStore.noop(); }
        @Override public VectorStore vectorStore() { return VectorStore.noop(); }
        @Override public boolean sensitiveDataLoggingEnabled() { return sensitiveLogging; }

        @Override
        public void reportRawIO(String rawInput, String rawOutput) {
            rawIoCalls.add(new String[]{rawInput, rawOutput});
        }
    }

    @Test
    void chatNodeCallsReportRawIoWhenSensitiveLoggingEnabled() {
        MockLlmClient client = MockLlmClient.constant("hello response");
        CapturingContext ctx = new CapturingContext(true);

        ChatNode<State> node = ChatNode.of(client,
                s -> LlmRequest.builder()
                        .model("test")
                        .messages(List.of(ChatMessage.user(s.input())))
                        .build(),
                (s, r) -> new State(s.input(), r.content()));

        node.execute(new State("hello prompt", null), ctx);

        assertThat(ctx.rawIoCalls).hasSize(1);
        assertThat(ctx.rawIoCalls.get(0)[0]).contains("hello prompt");
        assertThat(ctx.rawIoCalls.get(0)[1]).isEqualTo("hello response");
    }

    @Test
    void chatNodeDoesNotCallReportRawIoWhenSensitiveLoggingDisabled() {
        MockLlmClient client = MockLlmClient.constant("hello response");
        CapturingContext ctx = new CapturingContext(false);

        ChatNode<State> node = ChatNode.of(client,
                s -> LlmRequest.builder()
                        .model("test")
                        .messages(List.of(ChatMessage.user(s.input())))
                        .build(),
                (s, r) -> new State(s.input(), r.content()));

        node.execute(new State("secret prompt", null), ctx);

        assertThat(ctx.rawIoCalls).isEmpty();
    }

    @Test
    void graphSensitiveDataLoggingFlagDefaultIsFalse() {
        MockLlmClient client = MockLlmClient.constant("resp");
        Graph<State> graph = Graph.<State>builder()
                .node("chat", ChatNode.of(client,
                        s -> LlmRequest.builder().model("m")
                                .messages(List.of(ChatMessage.user(s.input()))).build(),
                        (s, r) -> new State(s.input(), r.content())))
                .entry("chat").terminal("chat")
                .build();

        ExecutionResult<State> result = graph.run(new State("q", null));
        assertThat(result.finalState().output()).isEqualTo("resp");
    }
}
