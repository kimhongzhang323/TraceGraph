package io.tracegraph.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive terminal trace viewer for a running TraceGraph application.
 *
 * <pre>{@code
 * java -jar tracegraph-cli.jar [--url http://localhost:8080] [--api-key KEY]
 * }</pre>
 *
 * <p>Browse recorded traces, inspect per-step state, and live-tail executions over the
 * {@code GET /tracegraph/stream} SSE endpoint. Arrow keys navigate, {@code ⏎} opens,
 * {@code w} watches live, {@code q} quits.
 */
public final class TraceGraphCli {

    private static final int KEY_UP = 1000;
    private static final int KEY_DOWN = 1001;
    private static final int KEY_ESC = 27;
    private static final int KEY_ENTER = 13;

    private final ApiClient api;
    private final Terminal terminal;

    TraceGraphCli(ApiClient api, Terminal terminal) {
        this.api = api;
        this.terminal = terminal;
    }

    public static void main(String[] args) throws IOException {
        String url = "http://localhost:8080";
        String apiKey = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = args[++i];
                case "--api-key" -> apiKey = args[++i];
                case "--help", "-h" -> {
                    System.out.println("usage: tracegraph-cli [--url http://localhost:8080] [--api-key KEY]");
                    return;
                }
                default -> {
                    System.err.println("unknown option: " + args[i]);
                    System.exit(2);
                }
            }
        }
        ApiClient api = new ApiClient(url, apiKey,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            terminal.enterRawMode();
            new TraceGraphCli(api, terminal).run();
        }
    }

    private static final class QuitRequested extends RuntimeException {
        QuitRequested() {
            super(null, null, false, false);
        }
    }

    void run() {
        try {
            while (true) {
                try {
                    if (!traceListScreen()) {
                        return;
                    }
                } catch (QuitRequested q) {
                    throw q;
                } catch (RuntimeException e) {
                    drawError(e);
                    int key = readKey(0);
                    if (key == 'q' || key == KEY_ESC) {
                        return;
                    }
                }
            }
        } catch (QuitRequested quit) {
            terminal.writer().print("\u001B[2J\u001B[H");
            terminal.flush();
        }
    }

    /** Returns false when the user quits. */
    private boolean traceListScreen() {
        List<String> ids = api.listTraceIds();
        List<JsonNode> summaries = new ArrayList<>();
        for (String id : ids) {
            try {
                summaries.add(api.trace(id));
            } catch (RuntimeException e) {
                summaries.add(null);
            }
        }
        int selected = 0;
        while (true) {
            draw(Screens.traceList(ids, summaries, selected, width()));
            int key = readKey(0);
            switch (key) {
                case 'q' -> {
                    return false;
                }
                case 'r' -> {
                    return true;
                }
                case 'w' -> liveScreen();
                case KEY_UP -> selected = Math.max(0, selected - 1);
                case KEY_DOWN -> selected = Math.min(Math.max(0, ids.size() - 1), selected + 1);
                case KEY_ENTER, '\n' -> {
                    if (!ids.isEmpty()) {
                        traceDetailScreen(ids.get(selected));
                    }
                }
                default -> { }
            }
        }
    }

    private void traceDetailScreen(String id) {
        JsonNode trace = api.trace(id);
        int step = 0;
        boolean showState = false;
        int steps = trace.path("steps").size();
        while (true) {
            draw(Screens.traceDetail(id, trace, step, showState, width()));
            int key = readKey(0);
            switch (key) {
                case KEY_ESC -> {
                    return;
                }
                case 'q' -> quit();
                case 's' -> showState = !showState;
                case KEY_UP -> step = Math.max(0, step - 1);
                case KEY_DOWN -> step = Math.min(Math.max(0, steps - 1), step + 1);
                default -> { }
            }
        }
    }

    private void liveScreen() {
        List<JsonNode> events = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean connected = new AtomicBoolean(false);
        Thread tail = Thread.startVirtualThread(() -> {
            try {
                connected.set(true);
                api.streamLive(null, events::add, cancelled::get);
            } catch (RuntimeException ignored) {
                connected.set(false);
            }
        });
        try {
            while (true) {
                draw(Screens.liveTail(List.copyOf(events), connected.get(), width(), height()));
                int key = readKey(250);
                if (key == KEY_ESC) {
                    return;
                }
                if (key == 'q') {
                    quit();
                }
            }
        } finally {
            cancelled.set(true);
            tail.interrupt();
        }
    }

    private void quit() {
        throw new QuitRequested();
    }

    private void draw(List<String> lines) {
        var writer = terminal.writer();
        writer.print("\u001B[2J\u001B[H");
        for (String line : lines) {
            writer.println(line);
        }
        terminal.flush();
    }

    private void drawError(RuntimeException e) {
        draw(List.of(
                Screens.header("error", "any key retries · q quits", width()),
                "",
                Ansi.color(Ansi.RED, "  " + e.getMessage()),
                "",
                Ansi.dim("  is the TraceGraph app running and --url correct?")));
    }

    private int width() {
        int w = terminal.getWidth();
        return w > 0 ? w : 100;
    }

    private int height() {
        int h = terminal.getHeight();
        return h > 0 ? h : 30;
    }

    /** Reads one key, decoding arrow-key escape sequences. {@code timeoutMs <= 0} blocks. */
    private int readKey(int timeoutMs) {
        try {
            NonBlockingReader reader = terminal.reader();
            int c = timeoutMs > 0 ? reader.read(timeoutMs) : reader.read();
            if (c == KEY_ESC) {
                int next = reader.read(40);
                if (next == '[') {
                    int code = reader.read(40);
                    if (code == 'A') return KEY_UP;
                    if (code == 'B') return KEY_DOWN;
                    return -1;
                }
                return KEY_ESC;
            }
            return c;
        } catch (IOException e) {
            return 'q';
        }
    }
}
