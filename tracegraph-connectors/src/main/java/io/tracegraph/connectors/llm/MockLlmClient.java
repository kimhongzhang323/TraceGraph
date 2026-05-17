package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;

public final class MockLlmClient implements LlmClient {

    private final Function<LlmRequest, LlmResponse> responder;
    private final List<LlmRequest> calls = new CopyOnWriteArrayList<>();
    private final boolean simulateStreaming;
    private final Function<LlmRequest, Flow.Publisher<LlmStreamChunk>> streamFn;

    public MockLlmClient(Function<LlmRequest, LlmResponse> responder) {
        this(responder, false, null);
    }

    private MockLlmClient(Function<LlmRequest, LlmResponse> responder, boolean simulateStreaming,
            Function<LlmRequest, Flow.Publisher<LlmStreamChunk>> streamFn) {
        this.responder = Objects.requireNonNull(responder, "responder");
        this.simulateStreaming = simulateStreaming;
        this.streamFn = streamFn;
    }

    /**
     * Creates a streaming mock that emits tool-call deltas on the first call (one chunk per
     * tool call: name delta, then arguments delta), then streams the final reply word-by-word
     * on subsequent calls.  Useful for testing streaming ReAct consumers.
     *
     * @param toolCalls  tool calls to stream as deltas on the first invocation
     * @param finalReply text to stream on subsequent invocations
     */
    public static MockLlmClient streamingWithToolCalls(List<ToolCall> toolCalls, String finalReply) {
        List<ToolCall> safe = List.copyOf(toolCalls);
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Function<LlmRequest, Flow.Publisher<LlmStreamChunk>> streamFn = req -> sub -> {
            SubmissionPublisher<LlmStreamChunk> pub = new SubmissionPublisher<>(
                    ForkJoinPool.commonPool(), Flow.defaultBufferSize());
            pub.subscribe(sub);
            int n = callCount.incrementAndGet();
            Thread.startVirtualThread(() -> {
                try {
                    if (n == 1) {
                        for (int i = 0; i < safe.size(); i++) {
                            ToolCall tc = safe.get(i);
                            pub.submit(LlmStreamChunk.toolCallDelta(i, tc.name(), ""));
                            pub.submit(LlmStreamChunk.toolCallDelta(i, "", tc.arguments()));
                        }
                        pub.submit(LlmStreamChunk.done(LlmResponse.FinishReason.TOOL_CALLS));
                    } else {
                        String[] words = finalReply.split("(?<=\\s)");
                        for (int i = 0; i < words.length; i++) {
                            if (i == words.length - 1) {
                                pub.submit(LlmStreamChunk.done(words[i], LlmResponse.FinishReason.STOP));
                            } else {
                                pub.submit(LlmStreamChunk.content(words[i]));
                            }
                        }
                        if (words.length == 0) pub.submit(LlmStreamChunk.done(LlmResponse.FinishReason.STOP));
                    }
                    Thread.sleep(10);
                    pub.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pub.closeExceptionally(e);
                } catch (Throwable t) {
                    pub.closeExceptionally(t);
                }
            });
        };
        Function<LlmRequest, LlmResponse> responder = new Function<>() {
            private volatile boolean toolCallsDone = false;
            @Override
            public LlmResponse apply(LlmRequest req) {
                if (!toolCallsDone) {
                    toolCallsDone = true;
                    return new LlmResponse("", LlmResponse.FinishReason.TOOL_CALLS,
                            new LlmResponse.Usage(10, 5), safe);
                }
                return new LlmResponse(finalReply, LlmResponse.FinishReason.STOP,
                        new LlmResponse.Usage(20, 10));
            }
        };
        return new MockLlmClient(responder, false, streamFn);
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

    /**
     * Creates a mock that returns tool calls on the first invocation, then a final
     * text response on subsequent calls. Useful for testing ReAct loops.
     *
     * @param toolCalls  the tool calls to return on the first LLM call
     * @param finalReply the text response to return after tool results are provided
     */
    public static MockLlmClient withToolCalls(List<ToolCall> toolCalls, String finalReply) {
        return new MockLlmClient(new Function<LlmRequest, LlmResponse>() {
            private volatile boolean toolCallsReturned = false;

            @Override
            public LlmResponse apply(LlmRequest req) {
                if (!toolCallsReturned) {
                    toolCallsReturned = true;
                    return new LlmResponse("", LlmResponse.FinishReason.TOOL_CALLS,
                            new LlmResponse.Usage(10, 5), toolCalls);
                }
                return new LlmResponse(finalReply, LlmResponse.FinishReason.STOP,
                        new LlmResponse.Usage(20, 10));
            }
        });
    }

    /**
     * Creates a streaming mock that emits the response word-by-word, simulating
     * real token streaming. Useful for testing streaming propagation.
     *
     * @param text the full text to stream word-by-word
     */
    public static MockLlmClient streaming(String text) {
        return new MockLlmClient(req -> new LlmResponse(text,
                LlmResponse.FinishReason.STOP, new LlmResponse.Usage(0, text.length())), true, null);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        calls.add(request);
        return responder.apply(request);
    }

    @Override
    public Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) {
        if (streamFn != null) return streamFn.apply(request);
        if (!simulateStreaming) {
            return LlmClient.super.stream(request);
        }
        return subscriber -> {
            calls.add(request);
            LlmResponse full = responder.apply(request);
            SubmissionPublisher<LlmStreamChunk> pub = new SubmissionPublisher<>(
                    ForkJoinPool.commonPool(), Flow.defaultBufferSize());
            pub.subscribe(subscriber);
            Thread.startVirtualThread(() -> {
                try {
                    // Emit word-by-word for realistic streaming simulation
                    String[] words = full.content().split("(?<=\\s)");
                    for (int i = 0; i < words.length; i++) {
                        if (i == words.length - 1) {
                            pub.submit(LlmStreamChunk.done(words[i], full.finish()));
                        } else {
                            pub.submit(LlmStreamChunk.content(words[i]));
                        }
                    }
                    if (words.length == 0) {
                        pub.submit(LlmStreamChunk.done(full.finish()));
                    }
                    Thread.sleep(10);
                    pub.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pub.closeExceptionally(e);
                } catch (Throwable t) {
                    pub.closeExceptionally(t);
                }
            });
        };
    }

    public List<LlmRequest> calls() {
        return List.copyOf(calls);
    }
}
