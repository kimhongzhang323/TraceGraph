# Graph Complexity

`GraphComplexity` is an immutable record returned by `graph.complexity()` and the `GET /tracegraph/ui/complexity` endpoint. It surfaces structural metrics about a compiled graph so you can reason about maintainability, testability, and runtime behaviour **before** deploying.

> 🌐 中文版： **[[zh-Graph-Complexity|图复杂度]]**

## Fields

| Field | Type | Description |
|---|---|---|
| `nodeCount` | int | Total nodes, including parallel branches and subgraph nodes counted at the parent level. |
| `edgeCount` | int | Total directed edges, including conditional edges. |
| `maxFanOut` | int | Highest number of outgoing edges from any single node. Fan-out 1 = linear; higher = branching decision points. |
| `maxDepth` | int | Longest path from entry to any terminal, in node hops. |
| `cyclomaticComplexity` | int | McCabe complexity: `edgeCount - nodeCount + 2`. Correlates with the minimum test cases for full path coverage. |
| `parallelBranches` | int | Total anonymous parallel branches across all `parallel(...)` and `sendAll` call sites. |
| `subgraphDepth` | int | Max nesting depth of embedded subgraphs. Flat = 0; one level of `subgraph(...)` = 1. |
| `hotspots` | `List<String>` | Node names exceeding a heuristic complexity threshold — typically high fan-out or on many longest paths. Review these first. |

## When to act

| Metric | Warning threshold | Action |
|---|---|---|
| `nodeCount` | > 30 | Break the graph into subgraphs. |
| `cyclomaticComplexity` | > 10 | Add tests for uncovered paths; simplify routing. |
| `maxFanOut` | > 5 | A node makes too many routing decisions — use a dedicated `RoutingNode` or split. |
| `maxDepth` | > 15 | Long chains are hard to debug — checkpoint milestones, decompose into subgraphs. |
| `subgraphDepth` | > 3 | Deep nesting makes traces hard to navigate — flatten where possible. |
| `parallelBranches` | > 10 | High parallelism strains the executor — verify the thread pool can handle it. |

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

A `cyclomaticComplexity` of 4 means at least 4 distinct execution paths. `hotspots: ["route", "enrich"]` tells you where to start a review.

## Accessing complexity

**Programmatically:**

```java
GraphComplexity c = graph.complexity();
if (c.cyclomaticComplexity() > 10) {
    log.warn("Graph complexity {} exceeds threshold", c.cyclomaticComplexity());
}
```

**Via REST** (requires `tracegraph-ui`):

```
GET /tracegraph/ui/complexity
```

Returns the same JSON. See **[[REST API Reference]]** and **[[Trace UI]]**.
