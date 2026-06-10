package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An {@link LlmClient} decorator that records full request/response exchanges during a live run
 * and serves them back deterministically on replay — the LLM half of replay forking.
 *
 * <p><em>Record mode</em> ({@link #recording(LlmClient)}) delegates every {@link #complete} call
 * and captures the {@link Exchange}. Persist with {@link #save(Path)} and reload with
 * {@link #load(Path)} (requires Jackson, an optional dependency of this module).
 *
 * <p><em>Replay mode</em> ({@link #replaying(List)}) matches incoming requests against recorded
 * exchanges by {@link LlmRequest} equality. Identical repeated requests are served in recorded
 * order. A request with no recorded response throws {@link LlmReplayMismatchException} — or, when
 * a fallback client is supplied via {@link #replaying(List, LlmClient)}, goes live instead. The
 * fallback is what makes mid-trace forks work: unchanged steps re-issue identical requests and
 * get recorded answers for free; the step you edited produces a new prompt and only that call
 * hits the API.
 *
 * <p>Thread-safe in both modes; replay matching never holds its lock across a fallback call.
 */
public final class RecordedLlmClient implements LlmClient {

    /** One recorded request/response pair. */
    public record Exchange(LlmRequest request, LlmResponse response) {
        public Exchange {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(response, "response");
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient delegate;
    private final ConcurrentLinkedQueue<Exchange> recorded;
    private final Map<LlmRequest, Deque<LlmResponse>> replayable;
    private final ReentrantLock replayLock;
    private final LlmClient fallback;

    private RecordedLlmClient(LlmClient delegate, List<Exchange> exchanges, LlmClient fallback) {
        this.delegate = delegate;
        if (delegate != null) {
            this.recorded = new ConcurrentLinkedQueue<>();
            this.replayable = null;
            this.replayLock = null;
            this.fallback = null;
        } else {
            this.recorded = null;
            this.replayable = new HashMap<>();
            for (Exchange e : exchanges) {
                this.replayable.computeIfAbsent(e.request(), r -> new ArrayDeque<>()).add(e.response());
            }
            this.replayLock = new ReentrantLock();
            this.fallback = fallback;
        }
    }

    /** Creates a recording client that delegates to {@code delegate} and captures each exchange. */
    public static RecordedLlmClient recording(LlmClient delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new RecordedLlmClient(delegate, null, null);
    }

    /**
     * Creates a replaying client. Unmatched requests throw {@link LlmReplayMismatchException}.
     */
    public static RecordedLlmClient replaying(List<Exchange> exchanges) {
        Objects.requireNonNull(exchanges, "exchanges");
        return new RecordedLlmClient(null, exchanges, null);
    }

    /**
     * Creates a replaying client that sends unmatched requests to {@code fallback} instead of
     * throwing — hybrid replay for forks that modify a prompt.
     */
    public static RecordedLlmClient replaying(List<Exchange> exchanges, LlmClient fallback) {
        Objects.requireNonNull(exchanges, "exchanges");
        Objects.requireNonNull(fallback, "fallback");
        return new RecordedLlmClient(null, exchanges, fallback);
    }

    @Override
    public String systemName() {
        return delegate != null ? delegate.systemName() : "recorded";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (delegate != null) {
            LlmResponse response = delegate.complete(request);
            recorded.add(new Exchange(request, response));
            return response;
        }
        LlmResponse match;
        replayLock.lock();
        try {
            Deque<LlmResponse> queue = replayable.get(request);
            match = queue == null ? null : queue.poll();
        } finally {
            replayLock.unlock();
        }
        if (match != null) {
            return match;
        }
        if (fallback != null) {
            return fallback.complete(request);
        }
        throw new LlmReplayMismatchException(
                "No recorded response for request (model=" + request.model()
                        + ", messages=" + request.messages().size()
                        + ", tools=" + request.tools().size() + ") and no fallback configured");
    }

    /** Exchanges captured so far, in call order (record mode only). */
    public List<Exchange> exchanges() {
        if (recorded == null) {
            throw new IllegalStateException("exchanges() is only valid in record mode");
        }
        return List.copyOf(recorded);
    }

    /** Number of recorded responses not yet served (replay mode only). */
    public int remaining() {
        if (replayable == null) {
            throw new IllegalStateException("remaining() is only valid in replay mode");
        }
        replayLock.lock();
        try {
            return replayable.values().stream().mapToInt(Deque::size).sum();
        } finally {
            replayLock.unlock();
        }
    }

    /**
     * Writes the captured exchanges to {@code file} as JSON (record mode only).
     * Atomic temp-file + move for crash safety.
     */
    public void save(Path file) throws IOException {
        if (recorded == null) {
            throw new IllegalStateException("save() is only valid in record mode");
        }
        byte[] json = MAPPER.writeValueAsBytes(List.copyOf(recorded));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, json);
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Loads exchanges previously written by {@link #save(Path)}. */
    public static List<Exchange> load(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            List<Exchange> exchanges = MAPPER.readValue(bytes,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Exchange.class));
            return new ArrayList<>(exchanges);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load recorded exchanges: " + file, e);
        }
    }
}
