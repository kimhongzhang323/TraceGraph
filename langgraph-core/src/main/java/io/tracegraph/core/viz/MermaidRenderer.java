package io.tracegraph.core.viz;

import io.tracegraph.core.Edge;
import io.tracegraph.core.Graph;

public final class MermaidRenderer {

    private MermaidRenderer() {}

    public static String render(Graph<?> g) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n");
        render(g, sb, "    ");
        return sb.toString();
    }

    private static void render(Graph<?> g, StringBuilder sb, String indent) {
        sb.append(indent).append("[*] --> ").append(g.entry()).append('\n');
        for (String name : g.nodeNames()) {
            Graph<?> inner = g.subgraph(name).orElse(null);
            if (inner != null) {
                sb.append(indent).append("subgraph ").append(name).append('\n');
                render(inner, sb, indent + "    ");
                sb.append(indent).append("end\n");
            }
        }
        for (Edge<?> e : g.edges()) {
            sb.append(indent).append(e.from()).append(" --> ");
            if (e.condition() != null) {
                sb.append("|cond|");
            }
            sb.append(e.to()).append('\n');
        }
        for (String t : g.terminals()) {
            sb.append(indent).append(t).append(" --> [*]\n");
        }
    }
}
