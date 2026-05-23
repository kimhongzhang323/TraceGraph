package io.tracegraph.connectors.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Aggregates the per-candidate states produced by a {@link VotingNode} into a single winning state.
 *
 * <p>Conceptually equivalent to {@link BiFunction}{@code <S, List<S>, S>}: the first argument is
 * the state passed into the voting node and the second is the candidate results in declaration
 * order. The return value becomes the next state of the outer graph.
 *
 * <p>Built-in factories cover the two patterns peer frameworks reach for most often:
 * <ul>
 *   <li>{@link #majority(Function)} — pick the candidate whose extracted key matches the
 *       most-common value across all candidates. Ties are broken by declaration order.</li>
 *   <li>{@link #firstNonNull(Function)} — pick the first candidate whose extracted key is non-null;
 *       fall back to the original input state if every candidate's key is {@code null}.</li>
 * </ul>
 *
 * @param <S> the graph state type
 */
@FunctionalInterface
public interface Tally<S> extends BiFunction<S, List<S>, S> {

    /**
     * Returns a {@link Tally} that picks the candidate whose extracted key matches the
     * most-common value across all candidates. Ties resolve to the first matching candidate in
     * declaration order.
     *
     * @param keyExtractor function that pulls the comparison key out of each candidate state;
     *                     a {@code null} key is treated as its own bucket but only wins if it is
     *                     strictly the most frequent value
     * @param <S> the graph state type
     * @return a tally that returns the winning candidate's state
     * @throws NullPointerException if {@code keyExtractor} is {@code null}
     */
    static <S> Tally<S> majority(Function<S, String> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        return (input, results) -> {
            Map<String, Integer> counts = new HashMap<>();
            for (S r : results) {
                String key = keyExtractor.apply(r);
                counts.merge(key, 1, Integer::sum);
            }
            String winningKey = null;
            int winningCount = -1;
            for (S r : results) {
                String key = keyExtractor.apply(r);
                int c = counts.get(key);
                if (c > winningCount) {
                    winningKey = key;
                    winningCount = c;
                }
            }
            for (S r : results) {
                if (Objects.equals(keyExtractor.apply(r), winningKey)) {
                    return r;
                }
            }
            return input;
        };
    }

    /**
     * Returns a {@link Tally} that picks the first candidate whose extracted key is non-null.
     * If every candidate's key is {@code null}, the original input state is returned unchanged.
     *
     * @param keyExtractor function that pulls a value out of each candidate state
     * @param <S> the graph state type
     * @return a tally that returns the first non-null candidate's state, or the input state
     * @throws NullPointerException if {@code keyExtractor} is {@code null}
     */
    static <S> Tally<S> firstNonNull(Function<S, String> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        return (input, results) -> {
            for (S r : results) {
                if (keyExtractor.apply(r) != null) return r;
            }
            return input;
        };
    }
}
