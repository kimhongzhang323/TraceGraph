package io.tracegraph.ui;

import io.tracegraph.core.Graph;
import io.tracegraph.core.analysis.GraphComplexity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes graph complexity metrics as JSON.
 *
 * <p>Delegates to {@link GraphComplexity#of(Graph)} which requires
 * {@code Graph.parallelBranchCounts()} — added by the companion agent task.
 */
@RestController
@RequestMapping("/tracegraph/ui")
@ConditionalOnSingleCandidate(Graph.class)
public class ComplexityController {

    private final Graph<?> graph;

    public ComplexityController(Graph<?> graph) {
        this.graph = graph;
    }

    /**
     * Returns structural complexity metrics for the current graph as JSON.
     */
    @GetMapping("/complexity")
    public ResponseEntity<GraphComplexity> complexity() {
        return ResponseEntity.ok(computeComplexity(graph));
    }

    private static <S> GraphComplexity computeComplexity(Graph<S> g) {
        return GraphComplexity.of(g);
    }
}
