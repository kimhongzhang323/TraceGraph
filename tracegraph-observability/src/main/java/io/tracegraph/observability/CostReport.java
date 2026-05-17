package io.tracegraph.observability;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable per-execution cost snapshot produced by {@link LlmCostListener#snapshot(String)}.
 * Carries the executionId, a per-node breakdown, and the rolled-up total — suitable for
 * billing, dashboards, or post-run audit.
 *
 * <p>{@link #usageByNode()} is an unmodifiable {@link Map}; mutating it throws
 * {@link UnsupportedOperationException}.
 */
public record CostReport(String executionId, Map<String, Usage> usageByNode, Usage totalUsage) {

    public CostReport {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(usageByNode, "usageByNode");
        Objects.requireNonNull(totalUsage, "totalUsage");
        usageByNode = Map.copyOf(usageByNode);
    }
}
