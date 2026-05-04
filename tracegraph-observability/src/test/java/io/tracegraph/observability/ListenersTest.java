package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListenersTest {

    @Test
    void emptyComposeReturnsNoop() {
        assertThat(Listeners.compose()).isSameAs(NodeListener.NOOP);
    }

    @Test
    void singleComposeReturnsSameInstance() {
        NodeListener single = new NodeListener() {};
        assertThat(Listeners.compose(single)).isSameAs(single);
    }

    @Test
    void delegatesInDeclarationOrder() {
        List<String> events = new ArrayList<>();
        NodeListener a = recordingListener("a", events);
        NodeListener b = recordingListener("b", events);
        NodeListener c = recordingListener("c", events);

        NodeListener composed = Listeners.compose(a, b, c);
        composed.onEnter("n", "s");
        composed.onExit("n", "s");

        assertThat(events).containsExactly(
                "a:enter:n", "b:enter:n", "c:enter:n",
                "a:exit:n",  "b:exit:n",  "c:exit:n"
        );
    }

    @Test
    void exceptionInOneDoesNotStopOthers() {
        List<String> events = new ArrayList<>();
        NodeListener throwing = new NodeListener() {
            @Override public void onEnter(String name, Object state) {
                throw new RuntimeException("boom");
            }
        };
        NodeListener good = recordingListener("good", events);

        NodeListener composed = Listeners.compose(throwing, good);
        composed.onEnter("n", "s");

        assertThat(events).containsExactly("good:enter:n");
    }

    @Test
    void onStatePropagatesInOrder() {
        List<String> events = new ArrayList<>();
        NodeListener a = recordingListener("a", events);
        NodeListener b = recordingListener("b", events);

        NodeListener composed = Listeners.compose(a, b);
        composed.onState("n", "before", "after");

        assertThat(events).containsExactly("a:state:n:before->after", "b:state:n:before->after");
    }

    @Test
    void onStateExceptionInOneDoesNotStopOthers() {
        List<String> events = new ArrayList<>();
        NodeListener throwing = new NodeListener() {
            @Override public void onState(String n, Object before, Object after) {
                throw new RuntimeException("boom");
            }
        };
        NodeListener good = recordingListener("good", events);

        NodeListener composed = Listeners.compose(throwing, good);
        composed.onState("n", "x", "y");

        assertThat(events).containsExactly("good:state:n:x->y");
    }

    @Test
    void retryAndErrorPropagate() {
        List<String> events = new ArrayList<>();
        NodeListener a = recordingListener("a", events);
        NodeListener b = recordingListener("b", events);

        NodeListener composed = Listeners.compose(a, b);
        composed.onRetry("n", 2, new RuntimeException("x"));
        composed.onError("n", new RuntimeException("y"));

        assertThat(events).containsExactly(
                "a:retry:n:2", "b:retry:n:2",
                "a:error:n",   "b:error:n"
        );
    }

    private static NodeListener recordingListener(String tag, List<String> events) {
        return new NodeListener() {
            @Override public void onEnter(String n, Object s)  { events.add(tag + ":enter:" + n); }
            @Override public void onExit(String n, Object s)   { events.add(tag + ":exit:" + n); }
            @Override public void onError(String n, Throwable t) { events.add(tag + ":error:" + n); }
            @Override public void onRetry(String n, int a, Throwable t) { events.add(tag + ":retry:" + n + ":" + a); }
            @Override public void onState(String n, Object before, Object after) {
                events.add(tag + ":state:" + n + ":" + before + "->" + after);
            }
        };
    }
}
