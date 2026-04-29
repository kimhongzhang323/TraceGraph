package io.tracegraph.core.viz;

import io.tracegraph.core.Graph;

public final class PlantUmlRenderer {
    private PlantUmlRenderer() {}

    public static String render(Graph<?> g) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("[*] --> ").append(g.entry()).append('\n');
        for (var e : g.edges()) {
            sb.append(e.from()).append(" --> ").append(e.to()).append('\n');
        }
        for (String t : g.terminals()) {
            sb.append(t).append(" --> [*]\n");
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
