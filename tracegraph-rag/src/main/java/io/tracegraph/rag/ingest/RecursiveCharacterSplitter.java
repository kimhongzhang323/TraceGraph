package io.tracegraph.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Splits text recursively on a priority list of separators (paragraph → sentence → word boundary).
 *
 * <p>Strategy: try each separator in order; if a segment is still larger than {@code chunkSize},
 * recurse with the next separator. Overlap keeps the last {@code chunkOverlap} characters of the
 * previous chunk at the start of the next one so context isn't lost at boundaries.
 *
 * <p>Mirrors the behaviour of LangChain's {@code RecursiveCharacterTextSplitter} at the algorithm
 * level while staying dependency-free.
 */
public final class RecursiveCharacterSplitter implements TextSplitter {

    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", " ", "");

    private final int chunkSize;
    private final int chunkOverlap;
    private final List<String> separators;

    private RecursiveCharacterSplitter(Builder b) {
        this.chunkSize = b.chunkSize;
        this.chunkOverlap = b.chunkOverlap;
        this.separators = List.copyOf(b.separators);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience factory with sensible defaults (chunk=1000, overlap=200). */
    public static RecursiveCharacterSplitter defaults() {
        return builder().build();
    }

    @Override
    public List<String> split(String text) {
        Objects.requireNonNull(text, "text");
        List<String> chunks = new ArrayList<>();
        splitRecursive(text, separators, chunks);
        return List.copyOf(chunks);
    }

    private void splitRecursive(String text, List<String> seps, List<String> out) {
        if (text.length() <= chunkSize) {
            if (!text.isBlank()) out.add(text);
            return;
        }
        if (seps.isEmpty()) {
            // Hard-cut at chunkSize with overlap
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                String chunk = text.substring(start, end);
                if (!chunk.isBlank()) out.add(chunk);
                start = end - chunkOverlap;
                if (start >= end) break;
            }
            return;
        }

        String sep = seps.get(0);
        List<String> rest = seps.subList(1, seps.size());

        String[] parts = sep.isEmpty() ? splitByChar(text) : text.split(Pattern.quote(sep), -1);

        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String candidate = current.isEmpty() ? part : (current + (sep.isEmpty() ? "" : sep) + part);
            if (candidate.length() <= chunkSize) {
                if (!current.isEmpty() && !sep.isEmpty()) current.append(sep);
                current.append(part);
            } else {
                if (!current.isEmpty()) {
                    flushChunk(current.toString(), rest, out);
                    // keep overlap
                    String overlap = overlapSuffix(current.toString());
                    current = new StringBuilder(overlap);
                    if (!sep.isEmpty() && !overlap.isEmpty()) current.append(sep);
                }
                if (part.length() > chunkSize) {
                    splitRecursive(part, rest, out);
                    current = new StringBuilder();
                } else {
                    current.append(part);
                }
            }
        }
        if (!current.isEmpty()) {
            flushChunk(current.toString(), rest, out);
        }
    }

    private void flushChunk(String text, List<String> rest, List<String> out) {
        if (text.length() <= chunkSize) {
            if (!text.isBlank()) out.add(text);
        } else {
            splitRecursive(text, rest, out);
        }
    }

    private String overlapSuffix(String text) {
        if (chunkOverlap <= 0 || text.length() <= chunkOverlap) return text;
        return text.substring(text.length() - chunkOverlap);
    }

    private static String[] splitByChar(String text) {
        String[] arr = new String[text.length()];
        for (int i = 0; i < text.length(); i++) arr[i] = String.valueOf(text.charAt(i));
        return arr;
    }

    public static final class Builder {
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
        private List<String> separators = DEFAULT_SEPARATORS;

        private Builder() {}

        public Builder chunkSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
            this.chunkSize = size;
            return this;
        }

        public Builder chunkOverlap(int overlap) {
            if (overlap < 0) throw new IllegalArgumentException("chunkOverlap must be >= 0");
            this.chunkOverlap = overlap;
            return this;
        }

        public Builder separators(List<String> separators) {
            this.separators = Objects.requireNonNull(separators, "separators");
            return this;
        }

        public RecursiveCharacterSplitter build() {
            if (chunkOverlap >= chunkSize) {
                throw new IllegalArgumentException("chunkOverlap must be < chunkSize");
            }
            return new RecursiveCharacterSplitter(this);
        }
    }
}
