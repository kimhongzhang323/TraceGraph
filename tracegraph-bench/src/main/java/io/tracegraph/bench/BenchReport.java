package io.tracegraph.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a JMH JSON results file (argv[0]) and prints a Markdown table to stdout.
 *
 * <p>Usage: {@code java -cp benchmarks.jar io.tracegraph.bench.BenchReport results.json}
 *
 * <p>Parses the JMH JSON array without external dependencies using regex extraction.
 * Each element is expected to have: {@code benchmark}, {@code params} (object, may be absent),
 * {@code primaryMetric.score}, and {@code primaryMetric.scoreError}.
 */
public final class BenchReport {

    private BenchReport() {}

    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);

    private static final Pattern BENCHMARK_PATTERN =
            Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern SCORE_PATTERN =
            Pattern.compile("\"score\"\\s*:\\s*([0-9.Ee+\\-]+)");

    private static final Pattern SCORE_ERROR_PATTERN =
            Pattern.compile("\"scoreError\"\\s*:\\s*([0-9.Ee+\\-NaA]+)");

    private static final Pattern PARAMS_BLOCK_PATTERN =
            Pattern.compile("\"params\"\\s*:\\s*\\{([^}]*)\\}");

    private static final Pattern PARAM_ENTRY_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: BenchReport <jmh-results.json>");
            System.exit(1);
        }

        String json = Files.readString(Paths.get(args[0]));
        List<Row> rows = parse(json);

        if (rows.isEmpty()) {
            System.out.println("No benchmark results found.");
            return;
        }

        // Determine column widths
        int benchWidth = "Benchmark".length();
        int paramsWidth = "Params".length();
        for (Row r : rows) {
            benchWidth = Math.max(benchWidth, r.benchmark().length());
            paramsWidth = Math.max(paramsWidth, r.params().length());
        }

        String fmt = "| %-" + benchWidth + "s | %-" + paramsWidth + "s | %18s | %12s |%n";
        String sep = "|-" + "-".repeat(benchWidth) + "-|-" + "-".repeat(paramsWidth)
                + "-|--------------------:|-------------:|";

        System.out.printf(fmt, "Benchmark", "Params", "Score (µs/op)", "Error");
        System.out.println(sep);
        for (Row r : rows) {
            System.out.printf(fmt, r.benchmark(), r.params(),
                    String.format("%.3f", r.score()), String.format("%.3f", r.error()));
        }
    }

    private static List<Row> parse(String json) {
        List<Row> rows = new ArrayList<>();
        Matcher entries = ENTRY_PATTERN.matcher(json);
        while (entries.find()) {
            String entry = entries.group();

            Matcher bm = BENCHMARK_PATTERN.matcher(entry);
            if (!bm.find()) continue;
            String benchmark = bm.group(1);
            // Strip package prefix for readability
            int dot = benchmark.lastIndexOf('.');
            String shortName = dot >= 0 ? benchmark.substring(dot + 1) : benchmark;

            String params = "";
            Matcher pb = PARAMS_BLOCK_PATTERN.matcher(entry);
            if (pb.find()) {
                StringBuilder sb = new StringBuilder();
                Matcher pe = PARAM_ENTRY_PATTERN.matcher(pb.group(1));
                while (pe.find()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(pe.group(1)).append('=').append(pe.group(2));
                }
                params = sb.toString();
            }

            // primaryMetric block: find score and scoreError after "primaryMetric"
            int pmIdx = entry.indexOf("\"primaryMetric\"");
            if (pmIdx < 0) continue;
            String afterPm = entry.substring(pmIdx);

            Matcher sm = SCORE_PATTERN.matcher(afterPm);
            if (!sm.find()) continue;
            double score = Double.parseDouble(sm.group(1));

            Matcher em = SCORE_ERROR_PATTERN.matcher(afterPm);
            double error = em.find() ? parseDoubleOrNaN(em.group(1)) : Double.NaN;

            rows.add(new Row(shortName, params, score, error));
        }
        return rows;
    }

    private static double parseDoubleOrNaN(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private record Row(String benchmark, String params, double score, double error) {}
}
