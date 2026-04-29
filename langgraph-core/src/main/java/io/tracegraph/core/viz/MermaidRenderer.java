package io.tracegraph.core.viz;

import io.tracegraph.core.Graph;

public final class MermaidRenderer {
    private MermaidRenderer() {}

    public static String render(Graph<?> g) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n");
        sb.append("    [*] --> ").append(g.entry()).append('\n');
        for (var e : g.edges()) {
            sb.append("    ").append(e.from()).append(" --> ").append(e.to()).append('\n');
        }
        for (String t : g.terminals()) {
            sb.append("    ").append(t).append(" --> [*]\n");
        }
        return sb.toString();
    }
}
