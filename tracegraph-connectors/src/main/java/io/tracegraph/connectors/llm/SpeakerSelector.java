package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Picks the next speaker in a {@link GroupChatAgent} rotation.
 *
 * <p>The selector receives the current state and the list of registered agent names
 * (in declaration order) and returns the name of the agent to dispatch to next.
 * Returning {@code null}, {@code "done"}, or an unregistered name terminates the
 * group-chat loop.
 *
 * @param <S> the shared graph state type
 */
@FunctionalInterface
public interface SpeakerSelector<S> {

    /**
     * Choose the next speaker.
     *
     * @param state      the current graph state
     * @param agentNames the registered agent names in declaration order; never empty
     * @return the chosen agent name, or {@code null} / {@code "done"} / an unregistered
     *         name to terminate the loop
     */
    String next(S state, List<String> agentNames);

    /**
     * Returns a round-robin selector that cycles through the agent names in order.
     *
     * <p>The returned selector holds an internal counter and is therefore stateful.
     * It is safe to use across concurrent {@code Graph.run} invocations in the sense
     * that it will not corrupt state, but the rotation order will interleave between
     * concurrent executions. Construct a fresh selector per graph when isolation is
     * required.
     *
     * @param <S> the state type
     */
    static <S> SpeakerSelector<S> roundRobin() {
        AtomicInteger counter = new AtomicInteger();
        return (state, agentNames) -> {
            if (agentNames.isEmpty()) {
                throw new IllegalArgumentException("agentNames must not be empty");
            }
            int idx = counter.getAndIncrement() % agentNames.size();
            return agentNames.get(idx);
        };
    }

    /**
     * Returns a selector that delegates the choice to an LLM.
     *
     * <p>On each call: builds a request via {@code requestFactory}, sends it through
     * {@code client.complete}, then extracts the chosen speaker name from the response
     * via {@code nameExtractor}. Returning {@code null} / {@code "done"} / an
     * unregistered name terminates the loop.
     *
     * @param client         the LLM client to call
     * @param requestFactory builds an {@link LlmRequest.Builder} from the current state
     * @param nameExtractor  extracts the chosen agent name from the LLM response
     * @param <S>            the state type
     */
    static <S> SpeakerSelector<S> llm(
            LlmClient client,
            Function<S, LlmRequest.Builder> requestFactory,
            Function<LlmResponse, String> nameExtractor) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(requestFactory, "requestFactory");
        Objects.requireNonNull(nameExtractor, "nameExtractor");
        return (state, agentNames) -> {
            LlmRequest request = requestFactory.apply(state).build();
            LlmResponse response = client.complete(request);
            return nameExtractor.apply(response);
        };
    }
}
