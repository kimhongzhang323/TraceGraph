package io.tracegraph.boot.web;

import io.tracegraph.core.Graph;
import io.tracegraph.core.NodeEvent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Flow;

@RestController
@RequestMapping("/tracegraph/traces")
public class TraceStreamController<S> {

    private final Graph<S> graph;

    public TraceStreamController(Graph<S> graph) {
        this.graph = graph;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody S initial) {
        SseEmitter emitter = new SseEmitter(0L);
        graph.stream(initial).subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription sub;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.sub = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(NodeEvent<S> event) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.getClass().getSimpleName())
                            .data(event));
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                    sub.cancel();
                }
            }

            @Override
            public void onError(Throwable t) {
                emitter.completeWithError(t);
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }
        });
        return emitter;
    }
}
