package io.tracegraph.boot.web;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.ReplayRunner;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceReplayController {

    private final TraceStore store;
    private final Graph<?> graph;

    public TraceReplayController(TraceStore store, Graph<?> graph) {
        this.store = store;
        this.graph = graph;
    }

    @PostMapping("/{id}/replay")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<ReplayResponse> replay(
            @PathVariable("id") String id,
            @RequestParam(name = "step", defaultValue = "-1") int stepIndex) {

        Optional<ExecutionTrace<?>> parent = store.load(id);
        if (parent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExecutionTrace<?> trace = parent.get();
        int maxIndex = trace.steps().size() - 1;
        if (stepIndex < -1 || stepIndex > maxIndex) {
            return ResponseEntity.badRequest().build();
        }
        ReplayRunner runner = ReplayRunner.of((ExecutionTrace) trace, (Graph) graph);
        ExecutionResult result = runner.reRunFrom(stepIndex);
        return ResponseEntity.ok(new ReplayResponse(
                result.executionId(),
                id,
                stepIndex,
                result.status().name()));
    }

    public record ReplayResponse(String executionId,
                                 String forkedFromExecutionId,
                                 int forkedFromStepIndex,
                                 String status) {}
}
