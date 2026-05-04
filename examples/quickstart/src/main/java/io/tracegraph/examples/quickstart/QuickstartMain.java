package io.tracegraph.examples.quickstart;

import io.tracegraph.core.Graph;

public class QuickstartMain {

    record GreetingState(String name, String greeting) {}

    public static void main(String[] args) {
        Graph<GreetingState> graph = Graph.<GreetingState>builder()
                .node("greet", (state, ctx) ->
                        new GreetingState(state.name(), "Hello, " + state.name() + "!"))
                .node("shout", (state, ctx) ->
                        new GreetingState(state.name(),
                                state.greeting() + " (SHOUTED: " + state.greeting().toUpperCase() + ")"))
                .entry("greet")
                .edge("greet", "shout")
                .build();

        var result = graph.run(new GreetingState("TraceGraph", null));
        System.out.println("Final greeting: " + result.finalState().greeting());
    }
}
