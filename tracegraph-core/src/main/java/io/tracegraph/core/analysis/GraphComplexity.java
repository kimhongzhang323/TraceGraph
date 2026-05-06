package io.tracegraph.core.analysis;

import io.tracegraph.core.Edge;
import io.tracegraph.core.Graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural complexity metrics for a compiled {@link Graph}.
 *
 * <p>All fields are computed statically from graph topology — no execution required.
 * Obtain via {@link Graph#complexity()}.
 *
 * @param nodeCount           total number of registered nodes
 * @param edgeCount           total number of declared edges
 * @param maxFanOut           highest number of outgoing edges from any single node
 * @param maxDepth            length of the longest simple path from entry to any terminal
 * @param cyclomaticComplexity McCabe complexity: number of conditional edges + 1
 * @param parallelBranches    sum of branch counts across all parallel nodes
 * @param subgraphDepth       maximum nesting depth of embedded subgraphs (0 = none)
 * @param hotspots            node names whose fan-in or fan-out exceeds 4
 */
public record GraphComplexity(
        int nodeCount,
        int edgeCount,
        int maxFanOut,
        int maxDepth,
        int cyclomaticComplexity,
        int parallelBranches,
        int subgraphDepth,
        List<String> hotspots
) {

    /**
     * Compute complexity metrics for the given graph.
     *
     * @param graph graph to analyse; must not be null
     * @param <S>   graph state type
     * @return structural complexity record
     */
    public static <S> GraphComplexity of(Graph<S> graph) {
        List<Edge<S>> edges = graph.edges();
        Set<String> names = graph.nodeNames();

        Map<String, Integer> fanOut = new HashMap<>();
        Map<String, Integer> fanIn = new HashMap<>();
        for (String n : names) { fanOut.put(n, 0); fanIn.put(n, 0); }

        int conditionalEdges = 0;
        for (Edge<S> e : edges) {
            fanOut.merge(e.from(), 1, Integer::sum);
            fanIn.merge(e.to(), 1, Integer::sum);
            if (!e.isUnconditional()) conditionalEdges++;
        }

        int maxFanOut = fanOut.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        Map<String, List<String>> adjacency = new HashMap<>();
        for (Edge<S> e : edges) {
            adjacency.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        int maxDepth = longestPath(graph.entry(), adjacency, names.size());

        Map<String, Integer> branchCounts = graph.parallelBranchCounts();
        int parallelBranches = branchCounts.values().stream().mapToInt(Integer::intValue).sum();

        int subgraphDepth = subgraphDepth(graph);

        List<String> hotspots = new ArrayList<>();
        for (String n : names) {
            if (fanIn.getOrDefault(n, 0) > 4 || fanOut.getOrDefault(n, 0) > 4) {
                hotspots.add(n);
            }
        }

        return new GraphComplexity(
                names.size(),
                edges.size(),
                maxFanOut,
                maxDepth,
                conditionalEdges + 1,
                parallelBranches,
                subgraphDepth,
                List.copyOf(hotspots)
        );
    }

    /** DFS longest-simple-path length from {@code start}; cycle-safe via per-path visited set. */
    private static int longestPath(String start, Map<String, List<String>> adj, int nodeCount) {
        int[] max = {0};
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{start, 0, new HashSet<String>()});
        int cap = nodeCount; // simple paths can't exceed nodeCount steps
        while (!stack.isEmpty()) {
            Object[] frame = stack.pop();
            String node = (String) frame[0];
            int depth = (int) frame[1];
            @SuppressWarnings("unchecked")
            Set<String> visited = (Set<String>) frame[2];
            if (depth > max[0]) max[0] = depth;
            if (depth >= cap || visited.contains(node)) continue;
            Set<String> next = new HashSet<>(visited);
            next.add(node);
            for (String neighbour : adj.getOrDefault(node, List.of())) {
                stack.push(new Object[]{neighbour, depth + 1, next});
            }
        }
        return max[0];
    }

    private static <S> int subgraphDepth(Graph<S> graph) {
        int max = 0;
        for (String name : graph.nodeNames()) {
            if (graph.subgraph(name).isPresent()) {
                max = Math.max(max, 1 + subgraphDepth(graph.subgraph(name).get()));
            }
        }
        return max;
    }
}
