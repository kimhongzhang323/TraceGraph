package io.tracegraph.connectors.llm;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * An {@link LlmClient} that tries an ordered list of clients, falling through to the next when a
 * call fails with a retryable error — provider failover for production resilience.
 *
 * <p>The default failover predicate covers transient failures: {@link LlmHttpException} with
 * status 408, 429, or any 5xx, and {@link UncheckedIOException} (network errors and timeouts).
 * Non-retryable errors (e.g. 401 bad credentials) propagate immediately without consulting later
 * clients, as do interruptions. Supply a custom predicate via {@link #of(List, Predicate)} to
 * change that policy.
 *
 * <p>When every client fails, the last failure propagates with earlier failures attached as
 * suppressed exceptions. Which provider actually answered is visible through the existing
 * {@link LlmResponse#servedModel()} / {@link LlmResponse#rerouted(String)} mechanism — responses
 * pass through unchanged.
 *
 * <p>Thread-safe: holds no mutable state.
 */
public final class FallbackLlmClient implements LlmClient {

    private final List<LlmClient> clients;
    private final Predicate<RuntimeException> failoverOn;

    private FallbackLlmClient(List<LlmClient> clients, Predicate<RuntimeException> failoverOn) {
        if (clients.isEmpty()) {
            throw new IllegalArgumentException("at least one client is required");
        }
        this.clients = List.copyOf(clients);
        this.failoverOn = failoverOn;
    }

    /** Creates a failover chain over {@code clients} with the default retryable-error predicate. */
    public static FallbackLlmClient of(LlmClient... clients) {
        return of(List.of(clients), FallbackLlmClient::retryable);
    }

    /** Creates a failover chain with a custom predicate deciding which failures fall through. */
    public static FallbackLlmClient of(List<LlmClient> clients, Predicate<RuntimeException> failoverOn) {
        Objects.requireNonNull(clients, "clients");
        Objects.requireNonNull(failoverOn, "failoverOn");
        return new FallbackLlmClient(clients, failoverOn);
    }

    private static boolean retryable(RuntimeException e) {
        if (e instanceof LlmHttpException http) {
            int status = http.statusCode();
            return status == 408 || status == 429 || status / 100 == 5;
        }
        return e instanceof UncheckedIOException;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        RuntimeException lastFailure = null;
        for (LlmClient client : clients) {
            try {
                return client.complete(request);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof InterruptedException || !failoverOn.test(e)) {
                    if (lastFailure != null) {
                        e.addSuppressed(lastFailure);
                    }
                    throw e;
                }
                if (lastFailure != null) {
                    e.addSuppressed(lastFailure);
                }
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    @Override
    public String systemName() {
        return clients.get(0).systemName();
    }
}
