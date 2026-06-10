package io.tracegraph.boot.web;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for listing, inspecting, diffing, and deleting recorded traces.
 */
@RestController
@RequestMapping("/tracegraph/traces")
public class TraceController {

    static final int MAX_PAGE_SIZE = 200;

    private final TraceStore store;

    public TraceController(TraceStore store) {
        this.store = store;
    }

    /**
     * Lists trace execution IDs with optional pagination.
     * Emits a short-lived ETag so repeated polls collapse to 304s when the list hasn't changed.
     */
    @GetMapping
    public ResponseEntity<List<String>> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            WebRequest webRequest) {
        if (offset < 0 || (limit != null && limit < 0)) {
            return ResponseEntity.badRequest().build();
        }
        if (limit != null && limit > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        List<String> all = store.listIds();
        String etag = "\"" + Integer.toHexString(all.hashCode()) + "\"";
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(304).build();
        }
        int from = Math.min(offset, all.size());
        int to = limit == null ? all.size() : Math.min(from + limit, all.size());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(2, TimeUnit.SECONDS).cachePublic())
                .eTag(etag)
                .header("X-Total-Count", Integer.toString(all.size()))
                .body(all.subList(from, to));
    }

    /**
     * Loads a trace by execution ID.
     * Completed traces are immutable — long-cache with ETag so browsers don't re-fetch.
     */
    @GetMapping("/{id}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<ExecutionTrace<?>> get(
            @PathVariable("id") String id,
            @RequestParam(name = "fromStep", required = false) Integer fromStep,
            @RequestParam(name = "maxSteps", required = false) Integer maxSteps,
            WebRequest webRequest) {
        if ((fromStep != null && fromStep < 0) || (maxSteps != null && maxSteps < 0)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ExecutionTrace<?>> trace = store.load(id);
        if (trace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExecutionTrace<?> t = trace.get();
        int totalSteps = t.steps().size();
        if (fromStep != null || maxSteps != null) {
            List steps = t.steps();
            int from = Math.min(fromStep == null ? 0 : fromStep, steps.size());
            int to = maxSteps == null ? steps.size() : Math.min(from + maxSteps, steps.size());
            t = new ExecutionTrace(t.executionId(), t.initialState(), t.finalState(),
                    t.status(), t.error(), steps.subList(from, to), t.startedAt(), t.completedAt(),
                    t.forkedFromExecutionId(), t.forkedFromStepIndex(),
                    t.parentExecutionId(), t.parentStepIndex(), t.correlationId());
        }
        String etag = "\"" + Integer.toHexString(t.hashCode()) + "\"";
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(304).build();
        }
        CacheControl cc = t.status() != null && t.status().name().equals("RUNNING")
                ? CacheControl.noStore()
                : CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic();
        return ResponseEntity.ok()
                .cacheControl(cc)
                .eTag(etag)
                .header("X-Total-Steps", Integer.toString(totalSteps))
                .body(t);
    }

    /**
     * Deletes a trace by execution ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (store.load(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Computes a structural diff between two traces.
     */
    @GetMapping("/{a}/diff/{b}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<TraceDiff<?>> diff(@PathVariable("a") String a, @PathVariable("b") String b) {
        Optional<ExecutionTrace<?>> left = store.load(a);
        Optional<ExecutionTrace<?>> right = store.load(b);
        if (left.isEmpty() || right.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TraceDiff diff = TraceDiff.between((ExecutionTrace) left.get(), (ExecutionTrace) right.get());
        return ResponseEntity.ok(diff);
    }
}
