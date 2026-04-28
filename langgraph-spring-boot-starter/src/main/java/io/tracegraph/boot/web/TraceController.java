package io.tracegraph.boot.web;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceController {

    private final TraceStore store;

    public TraceController(TraceStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<String>> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        if (offset < 0 || (limit != null && limit < 0)) {
            return ResponseEntity.badRequest().build();
        }
        List<String> all = store.listIds();
        int from = Math.min(offset, all.size());
        int to = limit == null ? all.size() : Math.min(from + limit, all.size());
        return ResponseEntity.ok()
                .header("X-Total-Count", Integer.toString(all.size()))
                .body(all.subList(from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionTrace<?>> get(@PathVariable("id") String id) {
        Optional<ExecutionTrace<?>> trace = store.load(id);
        return trace.<ResponseEntity<ExecutionTrace<?>>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (store.load(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

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
