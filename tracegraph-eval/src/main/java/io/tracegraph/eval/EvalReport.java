package io.tracegraph.eval;

import io.tracegraph.eval.baseline.EvalBaseline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EvalReport {

    private EvalReport() {}

    public static String toComparisonMarkdown(List<? extends EvalResult<?>> current, EvalBaseline baseline) {
        Objects.requireNonNull(baseline, "baseline");
        StringBuilder sb = new StringBuilder();
        sb.append("| Case ID | Metric | Baseline | Current | Δ |\n");
        sb.append("|---|---|---|---|---|\n");
        for (EvalResult<?> result : current) {
            String caseId = result.evalCase().id();
            Optional<List<MetricScore>> baselineScoresOpt = baseline.scoresFor(caseId);
            Map<String, MetricScore> baselineByMetric = baselineScoresOpt
                    .map(EvalReport::toMap)
                    .orElse(Map.of());
            for (MetricScore currentScore : result.scores()) {
                MetricScore baselineScore = baselineByMetric.get(currentScore.metricName());
                sb.append("| ").append(caseId)
                  .append(" | ").append(currentScore.metricName())
                  .append(" | ");
                if (baselineScoresOpt.isEmpty() || baselineScore == null) {
                    sb.append("— | ")
                      .append(String.format("%.2f", currentScore.score()))
                      .append(" | NEW |\n");
                } else {
                    double delta = currentScore.score() - baselineScore.score();
                    sb.append(String.format("%.2f", baselineScore.score()))
                      .append(" | ")
                      .append(String.format("%.2f", currentScore.score()))
                      .append(" | ").append(renderDelta(delta)).append(" |\n");
                }
            }
        }
        return sb.toString();
    }

    private static Map<String, MetricScore> toMap(List<MetricScore> scores) {
        return scores.stream().collect(java.util.stream.Collectors.toMap(
                MetricScore::metricName,
                s -> s,
                (a, b) -> b,
                java.util.LinkedHashMap::new));
    }

    private static String renderDelta(double delta) {
        if (delta > 0.0) {
            return String.format("↑ +%.2f", delta);
        }
        if (delta < 0.0) {
            return String.format("↓ %.2f", delta);
        }
        return "=";
    }

    public static String toMarkdown(List<? extends EvalResult<?>> results) {
        if (results.isEmpty()) {
            return "| Case ID | Passed | Latency (ms) |\n|---|---|---|\n";
        }
        List<String> metricNames = results.get(0).scores().stream()
                .map(MetricScore::metricName)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("| Case ID | Passed | Latency (ms)");
        for (String name : metricNames) {
            sb.append(" | ").append(name);
        }
        sb.append(" |\n");

        sb.append("|---|---|---");
        for (int i = 0; i < metricNames.size(); i++) {
            sb.append("|---");
        }
        sb.append("|\n");

        for (EvalResult<?> result : results) {
            sb.append("| ").append(result.evalCase().id())
              .append(" | ").append(result.passed() ? "PASS" : "FAIL")
              .append(" | ").append(result.latencyMs());
            for (MetricScore score : result.scores()) {
                sb.append(" | ").append(String.format("%.2f", score.score()));
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    public static String toJUnitXml(List<? extends EvalResult<?>> results, String suiteName) {
        long failures = results.stream().filter(r -> !r.passed()).count();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"").append(escape(suiteName))
          .append("\" tests=\"").append(results.size())
          .append("\" failures=\"").append(failures)
          .append("\">\n");

        for (EvalResult<?> result : results) {
            sb.append("  <testcase name=\"").append(escape(result.evalCase().id()))
              .append("\" time=\"").append(result.latencyMs() / 1000.0).append("\"");
            if (result.passed()) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                for (MetricScore score : result.scores()) {
                    if (!score.passed()) {
                        sb.append("    <failure message=\"")
                          .append(escape(score.metricName()))
                          .append(": ").append(escape(score.detail() != null ? score.detail() : "failed"))
                          .append("\"/>\n");
                    }
                }
                sb.append("  </testcase>\n");
            }
        }
        sb.append("</testsuite>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
