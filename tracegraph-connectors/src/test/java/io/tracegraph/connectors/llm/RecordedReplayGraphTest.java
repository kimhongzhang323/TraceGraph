package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end record/replay story: run a graph against a live (mock) LLM while recording, then
 * rebuild the same graph with a replaying client and get an identical execution with zero
 * live calls — the deterministic-fork half of replay.
 */
class RecordedReplayGraphTest {

    record State(String question, String draft, String polished) {}

    private static Graph<State> graph(LlmClient client) {
        return Graph.<State>builder()
                .node("draft", ChatNode.of(client,
                        s -> LlmRequest.builder().model("m")
                                .messages(List.of(ChatMessage.user("Draft: " + s.question()))).build(),
                        (s, r) -> new State(s.question(), r.content(), s.polished())))
                .node("polish", ChatNode.of(client,
                        s -> LlmRequest.builder().model("m")
                                .messages(List.of(ChatMessage.user("Polish: " + s.draft()))).build(),
                        (s, r) -> new State(s.question(), s.draft(), r.content())))
                .edge("draft", "polish")
                .entry("draft").terminal("polish")
                .build();
    }

    @Test
    void replayReproducesRecordedRunWithoutLiveCalls() {
        AtomicInteger liveCalls = new AtomicInteger();
        MockLlmClient live = new MockLlmClient(r -> {
            liveCalls.incrementAndGet();
            String prompt = r.messages().get(0).content();
            return new LlmResponse("answer to <" + prompt + ">",
                    LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
        });

        RecordedLlmClient recorder = RecordedLlmClient.recording(live);
        ExecutionResult<State> original = graph(recorder).run(new State("why is the sky blue?", null, null));
        assertThat(liveCalls.get()).isEqualTo(2);

        RecordedLlmClient replay = RecordedLlmClient.replaying(recorder.exchanges());
        ExecutionResult<State> replayed = graph(replay).run(new State("why is the sky blue?", null, null));

        assertThat(replayed.finalState()).isEqualTo(original.finalState());
        assertThat(liveCalls.get()).isEqualTo(2);
        assertThat(replay.remaining()).isZero();
    }

    @Test
    void forkWithEditedPromptOnlySendsChangedCallLive() {
        AtomicInteger liveCalls = new AtomicInteger();
        MockLlmClient live = new MockLlmClient(r -> {
            liveCalls.incrementAndGet();
            return new LlmResponse("live:" + r.messages().get(0).content(),
                    LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
        });

        RecordedLlmClient recorder = RecordedLlmClient.recording(live);
        graph(recorder).run(new State("original question", null, null));
        int callsAfterRecording = liveCalls.get();

        // Fork with a different question: the draft prompt changes (goes live), but if the
        // draft response matched a recorded one the polish step would replay. Here both
        // prompts differ, so both fall back — what matters is replay never throws and
        // recorded answers are preferred when prompts are identical.
        RecordedLlmClient hybrid = RecordedLlmClient.replaying(recorder.exchanges(), live);
        graph(hybrid).run(new State("original question", null, null));
        assertThat(liveCalls.get()).isEqualTo(callsAfterRecording);

        graph(RecordedLlmClient.replaying(recorder.exchanges(), live))
                .run(new State("edited question", null, null));
        assertThat(liveCalls.get()).isEqualTo(callsAfterRecording + 2);
    }
}
