package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.ReplayRunner;
import io.tracegraph.observability.replay.TraceStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceReplayController {

    private final TraceStore store;
    private final Graph<?> graph;
    private final Semaphore replaySemaphore;
    private final ObjectMapper objectMapper;

    public TraceReplayController(TraceStore store, Graph<?> graph,
                                 TraceGraphProperties props, ObjectMapper objectMapper) {
        this.store = store;
        this.graph = graph;
        int max = props.getWeb().getReplay().getMaxConcurrent();
        this.replaySemaphore = max > 0 ? new Semaphore(max) : null;
        this.objectMapper = objectMapper;
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

    /**
     * Forks a saved trace from a chosen step, optionally with a seed state override supplied
     * as a JSON request body. The fork gets a fresh executionId and lineage metadata.
     * 404 for unknown trace; 400 for out-of-range step or undeserializable body.
     */
    @PostMapping("/{id}/fork")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<?> fork(
            @PathVariable("id") String id,
            @RequestParam(name = "step", defaultValue = "-1") int stepIndex,
            HttpServletRequest request) throws IOException {

        Optional<ExecutionTrace<?>> parent = store.load(id);
        if (parent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExecutionTrace<?> trace = parent.get();
        int maxIndex = trace.steps().size() - 1;
        if (stepIndex < -1 || stepIndex > maxIndex) {
            return ResponseEntity.badRequest().build();
        }

        byte[] rawBodyBytes = request.getInputStream().readAllBytes();
        String rawBody = rawBodyBytes.length > 0
                ? new String(rawBodyBytes, StandardCharsets.UTF_8).trim() : null;

        ReplayRunner runner = ReplayRunner.of((ExecutionTrace) trace, (Graph) graph);
        ExecutionResult result;
        if (rawBody != null && !rawBody.isEmpty()) {
            @SuppressWarnings("rawtypes")
            Optional<Class> stateType = ((Graph) graph).stateType();
            if (stateType.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "stateType not configured on graph — cannot deserialize seed override"));
            }
            if (objectMapper == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No ObjectMapper available — cannot deserialize seed override"));
            }
            Object seed;
            try {
                //noinspection unchecked
                seed = objectMapper.readValue(rawBody, (Class<Object>) stateType.get());
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot deserialize seed override: " + e.getMessage()));
            }
            result = runner.reRunAfter(stepIndex, seed);
        } else {
            result = runner.reRunFrom(stepIndex);
        }

        return ResponseEntity.ok(new ForkResponse(
                result.executionId(), id, stepIndex, result.status().name(), result.finalState()));
    }

    /**
     * Resume an INTERRUPTED execution. When a JSON body is supplied it is deserialized to the
     * graph's state type (requires {@link Graph.Builder#stateType(Class)} to be configured) and
     * injected as the state override before execution continues — this is the HITL state-edit
     * entry point (LangGraph Studio parity). When no body is sent the saved checkpoint state is
     * used unchanged (backward-compatible).
     *
     * <p>Returns 400 when a body is present but the graph has no stateType configured, or when
     * the body cannot be deserialized to the configured state type. Returns 404 if the trace is
     * not found, 409 if the trace is not in INTERRUPTED status or has no checkpoint.
     */
    @PostMapping("/{id}/resume")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<?> resume(
            @PathVariable("id") String id,
            HttpServletRequest request) throws IOException {

        byte[] rawBodyBytes = request.getInputStream().readAllBytes();
        String rawBody = rawBodyBytes.length > 0
                ? new String(rawBodyBytes, StandardCharsets.UTF_8).trim()
                : null;

        Optional<ExecutionTrace<?>> maybeTrace = store.load(id);
        if (maybeTrace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (maybeTrace.get().status() != Status.INTERRUPTED) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "Trace '" + id + "' is not in INTERRUPTED state (current: " + maybeTrace.get().status() + ")"));
        }

        Optional<ExecutionResult<?>> maybeResult;
        if (rawBody != null && !rawBody.isEmpty()) {
            if (objectMapper == null) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Jackson ObjectMapper not available — add tools.jackson.core:jackson-databind to enable state editing"));
            }
            Optional<Class<?>> maybeType = ((Graph) graph).stateType();
            if (maybeType.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "stateType not configured — call Graph.Builder.stateType(Class) to enable state editing"));
            }
            Object decoded;
            try {
                decoded = objectMapper.readValue(rawBody, maybeType.get());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Cannot deserialize state body: " + e.getMessage()));
            }
            maybeResult = ((Graph) graph).resume(id, decoded);
        } else {
            maybeResult = ((Graph) graph).resume(id);
        }

        if (maybeResult.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "No checkpoint found for execution '" + id + "'"));
        }
        ExecutionResult<?> result = maybeResult.get();
        return ResponseEntity.ok(new ResumeResponse(
                result.executionId(), result.status().name(), result.finalState()));
    }

    public record ReplayResponse(String executionId,
                                 String forkedFromExecutionId,
                                 int forkedFromStepIndex,
                                 String status) {}

    public record ResumeResponse(String executionId,
                                 String status,
                                 Object finalState) {}

    public record ForkResponse(String executionId,
                               String forkedFromExecutionId,
                               int forkedFromStepIndex,
                               String status,
                               Object finalState) {}
}
