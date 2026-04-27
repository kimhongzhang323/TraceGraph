package io.tracegraph.observability;

@FunctionalInterface
public interface StateRenderer {

    StateRenderer DEFAULT = String::valueOf;

    String render(Object state);
}
