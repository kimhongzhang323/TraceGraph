package io.tracegraph.observability;

/**
 * Snapshot passed to a {@code TerminationListener}'s predicate on every {@code onExit}.
 *
 * @param nodeName           the node that just completed
 * @param nodesEnteredSoFar  total number of {@code onEnter} events observed in this listener's
 *                           lifetime, including the current node
 * @param afterState         the state returned by the just-completed node
 * @param <S>                the graph's state type
 */
public record TerminationContext<S>(String nodeName, int nodesEnteredSoFar, S afterState) {
}
