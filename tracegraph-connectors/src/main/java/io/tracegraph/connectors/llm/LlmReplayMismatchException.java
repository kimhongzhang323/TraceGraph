package io.tracegraph.connectors.llm;

/**
 * Thrown by {@link RecordedLlmClient} in replay mode when a request has no recorded response
 * and no fallback client is configured. The message carries a summary (model, message count),
 * never the prompt text itself — exception messages end up in logs.
 */
public final class LlmReplayMismatchException extends RuntimeException {

    public LlmReplayMismatchException(String message) {
        super(message);
    }

    public LlmReplayMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
