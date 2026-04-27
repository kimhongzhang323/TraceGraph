package io.tracegraph.boot.web;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<String> list() {
        return store.listIds();
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
