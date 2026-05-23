package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Node;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VotingNodeTest {

    record VoteState(String answer, List<String> log) {
        VoteState withAnswer(String a) { return new VoteState(a, log); }
    }

    private Graph<VoteState> trivialAgent(String answer) {
        return Graph.<VoteState>builder()
                .node("run", (state, ctx) -> {
                    List<String> log = new ArrayList<>(state.log());
                    log.add("ran:" + answer);
                    return new VoteState(answer, log);
                })
                .entry("run")
                .terminal("run")
                .build();
    }

    @Test
    void majorityTallySelectsMostCommonAnswerRedRedBlue() {
        // The spec'd canonical case: three agents return red, red, blue → majority "red"
        Graph<VoteState> alice = trivialAgent("red");
        Graph<VoteState> bob = trivialAgent("red");
        Graph<VoteState> carol = trivialAgent("blue");

        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("alice", alice)
                .candidate("bob", bob)
                .candidate("carol", carol)
                .tally(Tally.majority(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        ExecutionResult<VoteState> result = graph.run(new VoteState(null, new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().answer()).isEqualTo("red");
    }

    @Test
    void majorityTallyBreaksTiesByDeclarationOrder() {
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("alice", trivialAgent("apple"))
                .candidate("bob", trivialAgent("banana"))
                .tally(Tally.majority(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        ExecutionResult<VoteState> result = graph.run(new VoteState(null, new ArrayList<>()));
        assertThat(result.finalState().answer()).isEqualTo("apple");
    }

    @Test
    void majorityTallyAllUniquePicksFirstCandidate() {
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("a", trivialAgent("x"))
                .candidate("b", trivialAgent("y"))
                .candidate("c", trivialAgent("z"))
                .tally(Tally.majority(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        assertThat(graph.run(new VoteState(null, new ArrayList<>())).finalState().answer()).isEqualTo("x");
    }

    @Test
    void firstNonNullTallyReturnsFirstNonNullCandidate() {
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("a", trivialAgent(null))
                .candidate("b", trivialAgent("hit"))
                .candidate("c", trivialAgent("ignored"))
                .tally(Tally.firstNonNull(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        assertThat(graph.run(new VoteState(null, new ArrayList<>())).finalState().answer()).isEqualTo("hit");
    }

    @Test
    void firstNonNullTallyFallsBackToInputWhenAllNull() {
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("a", trivialAgent(null))
                .candidate("b", trivialAgent(null))
                .tally(Tally.firstNonNull(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        VoteState seed = new VoteState("initial", new ArrayList<>());
        assertThat(graph.run(seed).finalState().answer()).isEqualTo("initial");
    }

    @Test
    void singleCandidateProducesItsResult() {
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("only", trivialAgent("solo"))
                .tally(Tally.majority(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        assertThat(graph.run(new VoteState(null, new ArrayList<>())).finalState().answer()).isEqualTo("solo");
    }

    @Test
    void tallyReceivesInputAndCandidateResultsInDeclarationOrder() {
        List<String> seenAnswers = new ArrayList<>();
        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("a", trivialAgent("first"))
                .candidate("b", trivialAgent("second"))
                .candidate("c", trivialAgent("third"))
                .tally((input, results) -> {
                    for (VoteState r : results) seenAnswers.add(r.answer());
                    return input.withAnswer("tallied");
                })
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        VoteState result = graph.run(new VoteState("seed", new ArrayList<>())).finalState();
        assertThat(seenAnswers).containsExactly("first", "second", "third");
        assertThat(result.answer()).isEqualTo("tallied");
    }

    @Test
    void candidatesRunConcurrently() throws Exception {
        int n = 4;
        CountDownLatch allEntered = new CountDownLatch(n);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        VotingNode.Builder<VoteState> builder = VotingNode.<VoteState>builder()
                .tally(Tally.majority(VoteState::answer));
        for (int i = 0; i < n; i++) {
            final String name = "c" + i;
            Graph<VoteState> g = Graph.<VoteState>builder()
                    .node("run", (state, ctx) -> {
                        allEntered.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("release latch timed out");
                        }
                        completed.incrementAndGet();
                        return state.withAnswer("done");
                    })
                    .entry("run")
                    .terminal("run")
                    .build();
            builder.candidate(name, g);
        }
        Node<VoteState> voter = builder.build();

        Graph<VoteState> outer = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        Thread t = new Thread(() -> outer.run(new VoteState(null, new ArrayList<>())));
        t.setDaemon(true);
        t.start();

        assertThat(allEntered.await(5, TimeUnit.SECONDS))
                .as("all %d candidates should be entered concurrently", n)
                .isTrue();
        release.countDown();
        t.join(5_000);
        assertThat(completed.get()).isEqualTo(n);
    }

    @Test
    void candidateFailurePropagatesAsNodeException() {
        Graph<VoteState> failing = Graph.<VoteState>builder()
                .node("run", (state, ctx) -> { throw new IllegalStateException("boom"); })
                .entry("run")
                .terminal("run")
                .build();

        Node<VoteState> voter = VotingNode.<VoteState>builder()
                .candidate("ok", trivialAgent("ok"))
                .candidate("bad", failing)
                .tally(Tally.majority(VoteState::answer))
                .build();

        Graph<VoteState> graph = Graph.<VoteState>builder()
                .node("vote", voter)
                .entry("vote")
                .terminal("vote")
                .build();

        ExecutionResult<VoteState> result = graph.run(new VoteState(null, new ArrayList<>()));
        assertThat(result.status()).isEqualTo(Status.FAILED);
    }

    @Test
    void builderRejectsNoCandidates() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .tally(Tally.majority(VoteState::answer))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one candidate");
    }

    @Test
    void builderRejectsDuplicateCandidateName() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .candidate("dup", trivialAgent("a"))
                .candidate("dup", trivialAgent("b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void builderRejectsNullTally() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .candidate("a", trivialAgent("a"))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tally");
    }

    @Test
    void builderRejectsNullCandidateName() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .candidate(null, trivialAgent("a")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void builderRejectsNullCandidateGraph() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .candidate("a", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("candidate");
    }

    @Test
    void builderRejectsBlankCandidateName() {
        assertThatThrownBy(() -> VotingNode.<VoteState>builder()
                .candidate("", trivialAgent("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void majorityFactoryRejectsNullKeyExtractor() {
        assertThatThrownBy(() -> Tally.<VoteState>majority(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void firstNonNullFactoryRejectsNullKeyExtractor() {
        assertThatThrownBy(() -> Tally.<VoteState>firstNonNull(null))
                .isInstanceOf(NullPointerException.class);
    }
}
