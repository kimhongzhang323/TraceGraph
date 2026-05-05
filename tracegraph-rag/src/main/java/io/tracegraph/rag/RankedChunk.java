package io.tracegraph.rag;

/**
 * A retrieved text chunk paired with a relevance score.
 *
 * @param text chunk text
 * @param score relative relevance score, where larger usually means more relevant
 */
public record RankedChunk(String text, double score) {}
