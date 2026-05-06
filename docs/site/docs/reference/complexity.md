# Graph Complexity

`GraphComplexity` is an immutable record returned by `graph.complexity()` and the `GET /tracegraph/ui/complexity` endpoint. It surfaces structural metrics about a compiled graph so you can reason about maintainability, testability, and runtime behaviour before deploying.

## Fields

| Field | Type | Description |
|-------|------|-------------|
| `nodeCount` | int | Total number of nodes in the graph, including parallel branches and subgraph nodes counted at the parent level. |
| `edgeCount` | int | Total number of directed edges, including conditional edges. |
| `maxFanOut` | int | Highest number of outgoing edges from any single node. A fan-out of 1 means linear flow; higher values indicate branching decision points. |
| `maxDepth` | int | Length of the longest path from the entry node to any terminal node, measured in node hops. |
| `cyclomaticComplexity` | int | Classic McCabe complexity: `edgeCount - nodeCount + 2`. Correlates with the minimum number of test cases needed for full path coverage. |
| `parallelBranches` | int | Total number of anonymous parallel branches across all `parallel(...)` and `sendAll` call sites. |
| `subgraphDepth` | int | Maximum nesting depth of embedded subgraphs. A flat graph has depth 0; one level of `subgraph(...)` gives depth 1. |
| `hotspots` | `List<String>` | Node names that exceed a heuristic threshold for complexity contribution — typically nodes with high fan-out or that appear on many longest paths. Review these nodes first when debugging or profiling. |

## When to act

| Metric | Warning threshold | Action |
|--------|-------------------|--------|
| `nodeCount` | > 30 | Consider breaking the graph into subgraphs. |
| `cyclomaticComplexity` | > 10 | Add tests for uncovered paths; consider simplifying routing logic. |
| `maxFanOut` | > 5 | A node is making too many routing decisions. Consider a dedicated `RoutingNode` or splitting into sub-decisions. |
| `maxDepth` | > 15 | Long chains are hard to debug. Checkpoint intermediate milestones and consider subgraph decomposition. |
| `subgraphDepth` | > 3 | Deep nesting makes trace navigation difficult. Flatten where possible. |
| `parallelBranches` | > 10 | High parallelism strains the executor. Verify the configured thread pool can handle the load. |

## Example output

```json
{
  "nodeCount": 8,
  "edgeCount": 10,
  "maxFanOut": 3,
  "maxDepth": 6,
  "cyclomaticComplexity": 4,
  "parallelBranches": 2,
  "subgraphDepth": 1,
  "hotspots": ["route", "enrich"]
}
```

A `cyclomaticComplexity` of 4 means at least 4 distinct execution paths through the graph. `hotspots: ["route", "enrich"]` tells you to start your review with those two nodes.

## Accessing complexity

**Programmatically:**

```java
GraphComplexity c = graph.complexity();
if (c.cyclomaticComplexity() > 10) {
    log.warn("Graph complexity {} exceeds threshold", c.cyclomaticComplexity());
}
```

**Via REST (requires `tracegraph-ui`):**

```
GET /tracegraph/ui/complexity
```

Returns the same JSON representation.
