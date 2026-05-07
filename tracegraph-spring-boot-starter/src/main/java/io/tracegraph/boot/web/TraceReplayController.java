package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.ReplayRunner;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceReplayController {

    private final TraceStore store;
    private final Graph<?> graph;
    private final Semaphore replaySemaphore;

    public TraceReplayController(TraceStore store, Graph<?> graph, TraceGraphProperties props) {
        this.store = store;
        this.graph = graph;
        int max = props.getWeb().getReplay().getMaxConcurrent();
        this.replaySemaphore = max > 0 ? new Semaphore(max) : null;
    }

    /**
     * Re-executes a saved trace from a chosen step. Runs synchronously on the
     * (virtual) request thread — callers block until replay completes. Concurrent
     * replay is bounded by {@code tracegraph.web.replay.max-concurrent}.
     */
    @PostMapping("/{id}/replay")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<?> replay(
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
        if (replaySemaphore != null && !replaySemaphore.tryAcquire()) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "Too many concurrent replays — try again later"));
        }
        try {
            ReplayRunner runner = ReplayRunner.of((ExecutionTrace) trace, (Graph) graph);
            ExecutionResult result = runner.reRunFrom(stepIndex);
            return ResponseEntity.ok(new ReplayResponse(
                    result.executionId(), id, stepIndex, result.status().name()));
        } finally {
            if (replaySemaphore != null) replaySemaphore.release();
        }
    }

    @PostMapping("/{id}/resume")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<?> resume(@PathVariable("id") String id) {
        Optional<ExecutionTrace<?>> maybeTrace = store.load(id);
        if (maybeTrace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (maybeTrace.get().status() != Status.INTERRUPTED) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "Trace '" + id + "' is not in INTERRUPTED state (current: " + maybeTrace.get().status() + ")"));
        }
        Optional<ExecutionResult<?>> maybeResult = ((Graph) graph).resume(id);
        if (maybeResult.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "No checkpoint found for execution '" + id + "'"));
        }
        ExecutionResult<?> result = maybeResult.get();
        return ResponseEntity.ok(Map.of(
                "executionId", result.executionId(),
                "status", result.status().name()));
    }

    public record ReplayResponse(String executionId,
                                 String forkedFromExecutionId,
                                 int forkedFromStepIndex,
                                 String status) {}
}
