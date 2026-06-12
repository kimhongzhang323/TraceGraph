package io.tracegraph.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreensTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String plain(List<String> lines) {
        return String.join("\n", lines.stream().map(Ansi::strip).toList());
    }

    @Test
    void traceListMarksSelectionAndShowsSummaries() throws Exception {
        var summary = MAPPER.readTree(
                "{\"status\":\"COMPLETED\",\"startedAt\":\"2026-06-12T01:00:00Z\",\"steps\":[{},{}]}");
        List<String> lines = Screens.traceList(List.of("exec-a", "exec-b"), List.of(summary, summary), 1, 100);

        String plain = plain(lines);
        assertThat(plain).contains("exec-a").contains("exec-b").contains("COMPLETED");
        assertThat(Ansi.strip(lines.get(5))).startsWith("▸");
        assertThat(Ansi.strip(lines.get(4))).doesNotContain("▸");
    }

    @Test
    void emptyTraceListExplainsItself() {
        String plain = plain(Screens.traceList(List.of(), List.of(), 0, 80));
        assertThat(plain).contains("no traces recorded yet");
    }

    @Test
    void traceDetailRendersStepsRetriesAndFailure() throws Exception {
        var trace = MAPPER.readTree("""
                {"status":"FAILED","startedAt":"2026-06-12T01:00:00Z","steps":[
                  {"nodeName":"validate","attempts":1},
                  {"nodeName":"charge","attempts":3,"error":{"message":"declined"}}
                ]}""");
        String plain = plain(Screens.traceDetail("exec-a", trace, 1, false, 100));

        assertThat(plain).contains("FAILED").contains("validate").contains("charge")
                .contains("↻3").contains("✕");
    }

    @Test
    void traceDetailStateToggleShowsBeforeAndFailureMarker() throws Exception {
        var trace = MAPPER.readTree("""
                {"status":"FAILED","steps":[
                  {"nodeName":"charge","attempts":1,"before":{"total":9},"after":null,
                   "error":{"message":"declined"}}
                ]}""");
        String plain = plain(Screens.traceDetail("exec-a", trace, 0, true, 100));

        assertThat(plain).contains("state · before → after").contains("\"total\" : 9")
                .contains("(no exit; node failed)");
    }

    @Test
    void liveEventLineColorsByTypeAndCarriesDetail() throws Exception {
        var event = MAPPER.readTree(
                "{\"executionId\":\"exec-1234567890ab\",\"type\":\"EXIT\",\"nodeName\":\"charge\",\"detail\":\"12ms\"}");
        String line = Screens.liveEventLine(event);

        assertThat(line).contains(Ansi.GREEN);
        assertThat(Ansi.strip(line)).contains("exec-1234567").contains("EXIT").contains("charge").contains("12ms");
    }

    @Test
    void liveTailKeepsOnlyTheNewestEventsWithinHeight() throws Exception {
        var events = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for (int i = 0; i < 50; i++) {
            events.add(MAPPER.readTree("{\"executionId\":\"e\",\"type\":\"ENTER\",\"nodeName\":\"n" + i + "\"}"));
        }
        String plain = plain(Screens.liveTail(events, true, 100, 10));

        assertThat(plain).contains("n49").doesNotContain("n10 ");
    }
}
