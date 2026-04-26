package io.tracegraph.core;

import java.util.List;

@FunctionalInterface
public interface Merger<S> {
    S merge(S input, List<S> branchResults);
}
