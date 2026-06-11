package io.tracegraph.boot.web;

import io.tracegraph.observability.live.LiveTraceEvent;
import io.tracegraph.observability.live.LiveTraceFeed;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Flow;

/**
 * Live execution view: {@code GET /tracegraph/stream} subscribes to all executions,
 * {@code GET /tracegraph/stream?execution={id}} to a single one. Events come from the
 * {@link LiveTraceFeed} wired into the graph via {@code Graph.Builder.traceRecorder(...)}.
 * Consumable by {@code EventSource} — this is the GET counterpart to the run-starting
 * {@code POST /tracegraph/traces/stream}.
 */
@RestController
@RequestMapping("/tracegraph")
public class TraceLiveController {

    private final LiveTraceFeed feed;

    public TraceLiveController(LiveTraceFeed feed) {
        this.feed = feed;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(name = "execution", required = false) String executionId) {
        SseEmitter emitter = new SseEmitter(0L);
        feed.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription sub;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.sub = s;
                emitter.onTimeout(s::cancel);
                emitter.onCompletion(s::cancel);
                emitter.onError(ignored -> s.cancel());
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(LiveTraceEvent event) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.type().name())
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
        }, executionId);
        return emitter;
    }
}
