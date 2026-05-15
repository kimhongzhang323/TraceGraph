package io.tracegraph.rag.ingest;

import io.tracegraph.rag.Document;

import java.util.List;

/**
 * SPI for loading raw documents from an external source.
 *
 * <p>Implementations may load from the filesystem, S3, a web URL, a database, etc.
 * Each returned {@link Document} carries an id, raw text, and optional metadata.
 */
@FunctionalInterface
public interface DocumentLoader {

    /**
     * Load and return all documents from the underlying source.
     *
     * @return non-null, possibly empty list of documents
     */
    List<Document> load();
}
