package io.tracegraph.rag.ingest;

import java.util.List;

/**
 * SPI for splitting raw text into chunks suitable for embedding and retrieval.
 *
 * <p>Implementations must be thread-safe and stateless (or use local state per call).
 */
@FunctionalInterface
public interface TextSplitter {

    /**
     * Split {@code text} into an ordered list of non-overlapping (or overlapping, depending on
     * strategy) chunks.
     *
     * @param text source text; never {@code null}
     * @return ordered chunks; never {@code null}, may be a single-element list
     */
    List<String> split(String text);
}
