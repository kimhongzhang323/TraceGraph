package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * An {@link LlmClient} that records and replays LLM interactions for deterministic testing.
 *
 * <p>In <em>record</em> mode the client delegates to a real {@link LlmClient}, captures each
 * {@code (LlmRequest, LlmResponse)} pair, and writes the cassette to a JSON file via
 * {@link #save()}.
 *
 * <p>In <em>replay</em> mode the client loads a previously recorded cassette and returns
 * responses in FIFO order, ignoring the actual request content. Throws
 * {@link CassetteExhaustedException} when the queue runs out.
 *
 * <p>Cassette format: a JSON array of {@code {"request":{...},"response":{...}}} entries.
 */
public final class CassetteLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = buildMapper();

    private final LlmClient delegate;
    private final Path cassetteFile;
    private final List<CassetteEntry> recorded = new ArrayList<>();
    private final Queue<LlmResponse> replayQueue;
    private final boolean recordMode;

    private CassetteLlmClient(LlmClient delegate, Path cassetteFile, Queue<LlmResponse> replayQueue) {
        this.delegate = delegate;
        this.cassetteFile = cassetteFile;
        this.replayQueue = replayQueue;
        this.recordMode = delegate != null;
    }

    /**
     * Creates a recording client that delegates to {@code delegate} and accumulates cassette
     * entries. Call {@link #save()} after use to persist the cassette.
     */
    public static CassetteLlmClient recording(LlmClient delegate, Path cassetteFile) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (cassetteFile == null) throw new IllegalArgumentException("cassetteFile must not be null");
        return new CassetteLlmClient(delegate, cassetteFile, null);
    }

    /**
     * Creates a replaying client from a cassette file on the filesystem.
     */
    public static CassetteLlmClient replaying(Path cassetteFile) {
        if (cassetteFile == null) throw new IllegalArgumentException("cassetteFile must not be null");
        try {
            Queue<LlmResponse> queue = loadQueue(Files.readAllBytes(cassetteFile));
            return new CassetteLlmClient(null, cassetteFile, queue);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load cassette: " + cassetteFile, e);
        }
    }

    /**
     * Creates a replaying client from a cassette resource on the classpath.
     */
    public static CassetteLlmClient replayingFromClasspath(String resourcePath) {
        if (resourcePath == null) throw new IllegalArgumentException("resourcePath must not be null");
        try (InputStream is = CassetteLlmClient.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
            }
            byte[] bytes = is.readAllBytes();
            Queue<LlmResponse> queue = loadQueue(bytes);
            return new CassetteLlmClient(null, null, queue);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load cassette from classpath: " + resourcePath, e);
        }
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (recordMode) {
            LlmResponse response = delegate.complete(request);
            recorded.add(new CassetteEntry(request, response));
            return response;
        }
        LlmResponse response = replayQueue.poll();
        if (response == null) {
            throw new CassetteExhaustedException("Cassette exhausted — no more recorded responses");
        }
        return response;
    }

    /**
     * Writes all recorded entries to the cassette file (record mode only).
     * Uses an atomic temp-file + move for crash safety.
     */
    public void save() throws IOException {
        if (!recordMode) {
            throw new IllegalStateException("save() is only valid in record mode");
        }
        byte[] json = MAPPER.writeValueAsBytes(recorded);
        Path tmp = cassetteFile.resolveSibling(cassetteFile.getFileName() + ".tmp");
        Files.write(tmp, json);
        Files.move(tmp, cassetteFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // ---- internals ----

    private static Queue<LlmResponse> loadQueue(byte[] bytes) throws IOException {
        List<CassetteEntry> entries = MAPPER.readValue(bytes,
                MAPPER.getTypeFactory().constructCollectionType(List.class, CassetteEntry.class));
        Queue<LlmResponse> queue = new ArrayDeque<>(entries.size());
        for (CassetteEntry e : entries) {
            queue.add(e.response());
        }
        return queue;
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return m;
    }

    // ---- inner types ----

    record CassetteEntry(LlmRequest request, LlmResponse response) {}

    /** Thrown when a replaying cassette has no more recorded responses. */
    public static final class CassetteExhaustedException extends RuntimeException {
        public CassetteExhaustedException(String message) {
            super(message);
        }

        public CassetteExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
