package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class MockLlmClient implements LlmClient {

    private final Function<LlmRequest, LlmResponse> responder;
    private final List<LlmRequest> calls = new CopyOnWriteArrayList<>();

    public MockLlmClient(Function<LlmRequest, LlmResponse> responder) {
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    public static MockLlmClient echoing() {
        return new MockLlmClient(req -> {
            String last = req.messages().get(req.messages().size() - 1).content();
            return new LlmResponse(last, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(0, 0));
        });
    }

    public static MockLlmClient constant(String text) {
        return new MockLlmClient(req -> new LlmResponse(text,
                LlmResponse.FinishReason.STOP, new LlmResponse.Usage(0, text.length())));
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        calls.add(request);
        return responder.apply(request);
    }

    public List<LlmRequest> calls() {
        return List.copyOf(calls);
    }
}
