package io.tracegraph.boot.web;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceController {

    private final TraceStore store;

    public TraceController(TraceStore store) {
        this.store = store;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionTrace<?>> get(@PathVariable("id") String id) {
        Optional<ExecutionTrace<?>> trace = store.load(id);
        return trace.<ResponseEntity<ExecutionTrace<?>>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
