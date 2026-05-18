package io.tracegraph.eval.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.eval.EvalCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Loads {@link EvalCase}s from version-controllable data files (JSONL, CSV).
 *
 * <p>Both formats require an {@code id} field/column per row; everything else
 * is funnelled through the caller-supplied factory function so the loader stays
 * neutral about state shape.
 *
 * <p>JSONL: one JSON object per line; blank lines skipped. {@code expected}
 * and {@code metadata} fields are auto-mapped when present.
 *
 * <p>CSV: RFC-4180 minimal (header row required, double-quoted fields,
 * embedded commas/newlines/quotes supported). No external dependency.
 */
public final class EvalCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalCaseLoader() {}

    /**
     * Read {@code file} as JSONL and produce one {@link EvalCase} per non-blank line.
     *
     * @param <S>           state type
     * @param file          path to JSONL file
     * @param inputFactory  maps the full JSON node of a line to the case's input
     * @return immutable list of cases in file order
     * @throws EvalDatasetException if the file cannot be read or a line cannot be parsed
     */
    public static <S> List<EvalCase<S>> fromJsonl(Path file, Function<JsonNode, S> inputFactory) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(inputFactory, "inputFactory");
        List<String> lines = readAllLines(file);
        List<EvalCase<S>> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            JsonNode node = parseLine(raw, lineNumber);
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull()) {
                throw new EvalDatasetException("Missing required 'id' field at line " + lineNumber);
            }
            String id = idNode.asText();
            S input = inputFactory.apply(node);
            Object expected = extractExpected(node);
            Map<String, Object> metadata = extractMetadata(node);
            out.add(new EvalCase<>(id, input, expected, metadata));
        }
        return List.copyOf(out);
    }

    /**
     * Read {@code file} as CSV and produce one {@link EvalCase} per data row.
     *
     * <p>The first non-blank line is the header; the row map passed to
     * {@code inputFactory} is keyed by header column names in encounter order.
     * If the row carries an {@code expected} column, that string is used as
     * {@link EvalCase#expected()}; otherwise expected is {@code null}.
     *
     * @param <S>           state type
     * @param file          path to CSV file
     * @param inputFactory  maps the column map of a row to the case's input
     * @return immutable list of cases in file order
     * @throws EvalDatasetException if the file cannot be read or a row is malformed
     */
    public static <S> List<EvalCase<S>> fromCsv(Path file, Function<Map<String, String>, S> inputFactory) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(inputFactory, "inputFactory");
        String text = readAll(file);
        List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty()) {
            throw new EvalDatasetException("CSV file " + file + " has no header row");
        }
        List<String> header = rows.get(0);
        int idIndex = header.indexOf("id");
        if (idIndex < 0) {
            throw new EvalDatasetException("CSV header must include an 'id' column (got " + header + ")");
        }
        int expectedIndex = header.indexOf("expected");
        List<EvalCase<S>> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int lineNumber = r + 1;
            if (row.size() == 1 && row.get(0).isEmpty()) {
                continue;
            }
            if (row.size() != header.size()) {
                throw new EvalDatasetException(
                        "CSV row at line " + lineNumber + " has " + row.size()
                                + " field(s), expected " + header.size());
            }
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                rowMap.put(header.get(c), row.get(c));
            }
            String id = row.get(idIndex);
            S input = inputFactory.apply(rowMap);
            Object expected = expectedIndex >= 0 ? row.get(expectedIndex) : null;
            out.add(new EvalCase<>(id, input, expected, Map.of()));
        }
        return List.copyOf(out);
    }

    private static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EvalDatasetException("Failed to read " + file, e);
        }
    }

    private static String readAll(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EvalDatasetException("Failed to read " + file, e);
        }
    }

    private static JsonNode parseLine(String raw, int lineNumber) {
        try {
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new EvalDatasetException(
                    "Failed to parse JSON at line " + lineNumber + ": " + e.getOriginalMessage(), e);
        }
    }

    private static Object extractExpected(JsonNode node) {
        JsonNode expected = node.get("expected");
        if (expected == null || expected.isNull()) {
            return null;
        }
        if (expected.isTextual()) {
            return expected.asText();
        }
        return MAPPER.convertValue(expected, Object.class);
    }

    private static Map<String, Object> extractMetadata(JsonNode node) {
        JsonNode metadata = node.get("metadata");
        if (metadata == null || metadata.isNull() || !metadata.isObject()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        metadata.properties().forEach(e -> out.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class)));
        return out;
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
            } else {
                if (c == '"' && field.length() == 0) {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    i++;
                } else if (c == '\r') {
                    i++;
                } else if (c == '\n') {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return filterBlankRows(rows);
    }

    private static List<List<String>> filterBlankRows(List<List<String>> rows) {
        List<List<String>> out = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            if (row.size() == 1 && row.get(0).isEmpty()) {
                continue;
            }
            out.add(row);
        }
        return out;
    }
}
