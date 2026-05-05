package io.tracegraph.core;

import java.util.List;

/**
 * Combines branch results produced by a parallel node into a single next state.
 *
 * <p>The executor passes the original input state alongside branch outputs so mergers can either
 * fold incremental changes or reconstruct a new aggregate object from scratch.
 *
 * @param <S> the graph state type
 */
@FunctionalInterface
public interface Merger<S> {
    /**
     * Merge branch outputs into the state that should continue through the graph.
     *
     * @param input the state passed into the parallel node
     * @param branchResults results produced by each branch, in declaration order
     * @return merged state
     */
    S merge(S input, List<S> branchResults);
}
