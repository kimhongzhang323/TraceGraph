package io.tracegraph.cli;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static io.tracegraph.cli.Ansi.CYAN;
import static io.tracegraph.cli.Ansi.GRAY;
import static io.tracegraph.cli.Ansi.GREEN;
import static io.tracegraph.cli.Ansi.MAGENTA;
import static io.tracegraph.cli.Ansi.RED;
import static io.tracegraph.cli.Ansi.YELLOW;
import static io.tracegraph.cli.Ansi.bold;
import static io.tracegraph.cli.Ansi.color;
import static io.tracegraph.cli.Ansi.dim;
import static io.tracegraph.cli.Ansi.padVisible;
import static io.tracegraph.cli.Ansi.statusColor;

/** Pure frame renderers — each returns the full list of lines for one screen state. */
final class Screens {

    private Screens() {}

    static String header(String title, String hint, int width) {
        String left = " " + bold("tracegraph") + " " + dim(Glyphs.active.bullet()) + " " + title;
        String right = dim(hint) + " ";
        int gap = Math.max(1, width - Ansi.visibleLength(left) - Ansi.visibleLength(right));
        return Ansi.INVERSE + padVisible(left + " ".repeat(gap) + right, width) + Ansi.RESET;
    }

    static String rule(int width) {
        return dim(Glyphs.active.rule().repeat(Math.max(0, width)));
    }

    static List<String> traceList(List<String> ids, List<JsonNode> summaries, int selected, int width) {
        List<String> lines = new ArrayList<>();
        Glyphs g = Glyphs.active;
        lines.add(header("traces", g.up() + g.down() + " move " + g.bullet() + " " + g.enter()
                + " open " + g.bullet() + " w watch live " + g.bullet() + " r refresh "
                + g.bullet() + " q quit", width));
        lines.add("");
        if (ids.isEmpty()) {
            lines.add(dim("  no traces recorded yet " + Glyphs.active.dash()
                    + " run a graph, then press r"));
            return lines;
        }
        lines.add("  " + dim(padVisible("#", 5) + padVisible("EXECUTION", 40)
                + padVisible("STATUS", 14) + padVisible("STEPS", 7) + "STARTED"));
        lines.add("  " + rule(width - 4));
        for (int i = 0; i < ids.size(); i++) {
            JsonNode t = i < summaries.size() ? summaries.get(i) : null;
            String status = t == null ? "?" : t.path("status").asText("?");
            String steps = t == null ? g.dash() : String.valueOf(t.path("steps").size());
            String started = t == null ? g.dash() : t.path("startedAt").asText(g.dash());
            String marker = i == selected ? color(GREEN, g.marker() + " ") : "  ";
            String id = padVisible(ids.get(i), 40);
            String row = marker + padVisible(String.valueOf(i), 5)
                    + (i == selected ? bold(id) : id)
                    + color(statusColor(status), padVisible(status, 14))
                    + padVisible(steps, 7) + dim(started);
            lines.add(Ansi.truncateVisible(row, width));
        }
        return lines;
    }

    static List<String> traceDetail(String id, JsonNode trace, int selectedStep, boolean showState, int width) {
        List<String> lines = new ArrayList<>();
        Glyphs g = Glyphs.active;
        lines.add(header("trace " + id, g.up() + g.down() + " step " + g.bullet()
                + " s state " + g.bullet() + " esc back " + g.bullet() + " q quit", width));
        String status = trace.path("status").asText("?");
        lines.add("  " + color(statusColor(status), bold(status))
                + dim("  " + g.bullet() + "  started " + trace.path("startedAt").asText(g.dash())
                + "  " + g.bullet() + "  " + trace.path("steps").size() + " steps"));
        lines.add("");
        JsonNode steps = trace.path("steps");
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            boolean active = i == selectedStep;
            String marker = active ? color(GREEN, g.marker() + " ") : "  ";
            String attempts = step.path("attempts").asInt(1) > 1
                    ? color(YELLOW, " " + g.retry() + step.path("attempts").asInt()) : "";
            String error = step.path("error").isMissingNode() || step.path("error").isNull()
                    ? "" : color(RED, " " + g.cross());
            String name = padVisible(step.path("nodeName").asText("?"), 28);
            String row = marker + dim(padVisible("#" + i, 5))
                    + (active ? bold(name) : name) + attempts + error;
            lines.add(Ansi.truncateVisible(row, width));
        }
        if (showState && selectedStep >= 0 && selectedStep < steps.size()) {
            JsonNode step = steps.get(selectedStep);
            lines.add("");
            lines.add("  " + dim("state " + g.bullet() + " before " + g.arrow()
                    + " after  (step #" + selectedStep + ")"));
            lines.add("  " + rule(width - 4));
            for (String before : step.path("before").toPrettyString().split("\n")) {
                lines.add(color(GRAY, "  - " + before));
            }
            JsonNode after = step.path("after");
            if (after.isMissingNode() || after.isNull()) {
                lines.add(color(RED, "  ! (no exit; node failed)"));
            } else {
                for (String line : after.toPrettyString().split("\n")) {
                    lines.add(color(GREEN, "  + " + line));
                }
            }
        }
        return lines;
    }

    static List<String> liveTail(List<JsonNode> events, boolean connected, int width, int height) {
        List<String> lines = new ArrayList<>();
        Glyphs g = Glyphs.active;
        lines.add(header("live", "esc back " + g.bullet() + " q quit", width));
        lines.add("  " + (connected
                ? color(GREEN, g.dot()) + dim(" connected " + g.dash() + " events stream as nodes execute")
                : color(YELLOW, g.dot()) + dim(" connecting" + g.ellipsis())));
        lines.add("");
        int budget = Math.max(1, height - 4);
        int from = Math.max(0, events.size() - budget);
        for (int i = from; i < events.size(); i++) {
            lines.add(Ansi.truncateVisible("  " + liveEventLine(events.get(i)), width));
        }
        return lines;
    }

    static String liveEventLine(JsonNode event) {
        String type = event.path("type").asText("?");
        String execution = event.path("executionId").asText("?");
        String node = event.path("nodeName").isNull() ? "" : event.path("nodeName").asText("");
        String detail = event.path("detail").isNull() ? "" : event.path("detail").asText("");
        String typeColor = switch (type) {
            case "START" -> MAGENTA;
            case "ENTER" -> CYAN;
            case "EXIT" -> GREEN;
            case "RETRY" -> YELLOW;
            case "ERROR" -> RED;
            case "COMPLETE" -> GREEN;
            default -> GRAY;
        };
        StringBuilder sb = new StringBuilder();
        sb.append(dim(padVisible(execution.length() > 12 ? execution.substring(0, 12) : execution, 14)));
        sb.append(color(typeColor, padVisible(type, 10)));
        if (!node.isEmpty()) {
            sb.append(bold(padVisible(node, 22)));
        }
        if (!detail.isEmpty()) {
            sb.append(dim(detail));
        }
        return sb.toString();
    }
}
