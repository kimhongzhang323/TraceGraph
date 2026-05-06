package io.tracegraph.ui;

import io.tracegraph.core.Graph;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the compiled graph structure as a Mermaid diagram source string.
 */
@RestController
@RequestMapping("/tracegraph/ui")
@ConditionalOnClass(Graph.class)
@ConditionalOnSingleCandidate(Graph.class)
public class GraphRenderController {

    private final Graph<?> graph;

    public GraphRenderController(Graph<?> graph) {
        this.graph = graph;
    }

    /**
     * Returns the Mermaid diagram source for the current graph.
     */
    @GetMapping(value = "/graph", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> mermaid() {
        return ResponseEntity.ok(graph.toMermaid());
    }
}
