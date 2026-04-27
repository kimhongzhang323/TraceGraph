package io.tracegraph.connectors.llm;

@FunctionalInterface
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
