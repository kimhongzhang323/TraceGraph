package io.tracegraph.connectors.llm;

public final class LlmHttpException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public LlmHttpException(int statusCode, String body) {
        super("LLM HTTP " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }
}
