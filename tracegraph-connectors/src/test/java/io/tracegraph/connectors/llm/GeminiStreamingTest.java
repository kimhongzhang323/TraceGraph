package io.tracegraph.connectors.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiStreamingTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<URI> requestedUri = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1beta/models/", ex -> {
            requestedUri.set(ex.getRequestURI());
            ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    private List<LlmStreamChunk> collect(Flow.Publisher<LlmStreamChunk> publisher) throws Exception {
        List<LlmStreamChunk> chunks = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<List<LlmStreamChunk>> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<LlmStreamChunk>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(LlmStreamChunk c) { chunks.add(c); }
            @Override public void onError(Throwable t) { done.completeExceptionally(t); }
            @Override public void onComplete() {
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                done.complete(List.copyOf(chunks));
            }
        });
        return done.get(5, TimeUnit.SECONDS);
    }

    private static LlmRequest request() {
        return LlmRequest.builder()
                .model("gemini-test").messages(List.of(ChatMessage.user("hi"))).build();
    }

    private GeminiLlmClient client() {
        return GeminiLlmClient.builder().apiKey("k").model("gemini-test").baseUrl(baseUrl).build();
    }

    @Test
    void streamsTextDeltasAndTerminalFinishReason() throws Exception {
        respond(200,
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}],\"role\":\"model\"}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" world\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks).filteredOn(c -> !c.isLast() && !c.isToolCallDelta())
                .extracting(LlmStreamChunk::delta)
                .containsExactly("Hello", " world");
        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(requestedUri.get().getPath()).endsWith("gemini-test:streamGenerateContent");
        assertThat(requestedUri.get().getQuery()).isEqualTo("alt=sse");
    }

    @Test
    void functionCallsStreamAsToolCallDeltasAndFinishAsToolCalls() throws Exception {
        respond(200,
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"checking\"}],\"role\":\"model\"}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"lookup\",\"args\":{\"id\":42}}}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        List<LlmStreamChunk> toolDeltas = chunks.stream().filter(LlmStreamChunk::isToolCallDelta).toList();
        assertThat(toolDeltas).hasSize(1);
        assertThat(toolDeltas.get(0).toolCallDelta().index()).isZero();
        assertThat(toolDeltas.get(0).toolCallDelta().nameDelta()).isEqualTo("lookup");
        assertThat(toolDeltas.get(0).toolCallDelta().argsDelta()).isEqualTo("{\"id\":42}");
        assertThat(chunks.get(chunks.size() - 1).finishReason())
                .isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
    }

    @Test
    void safetyFinishReasonMapsToRefused() throws Exception {
        respond(200,
                "data: {\"candidates\":[{\"content\":{\"parts\":[],\"role\":\"model\"},\"finishReason\":\"SAFETY\"}]}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.REFUSED);
    }

    @Test
    void non2xxSurfacesAsLlmHttpException() {
        respond(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}");

        assertThatThrownBy(() -> collect(client().stream(request())))
                .hasCauseInstanceOf(LlmHttpException.class)
                .satisfies(e -> assertThat(((LlmHttpException) e.getCause()).statusCode()).isEqualTo(429));
    }

    @Test
    void malformedSseDataIsSkippedAndStreamContinues() throws Exception {
        respond(200,
                "data: {not json}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks).anyMatch(c -> c.delta().equals("ok"));
        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
    }
}
