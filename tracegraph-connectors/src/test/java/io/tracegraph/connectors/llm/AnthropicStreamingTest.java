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

class AnthropicStreamingTest {

    private HttpServer server;
    private URI endpoint;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/messages", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().set("Content-Type", contentType);
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
                .model("claude-sonnet-4-6").messages(List.of(ChatMessage.user("hi"))).build();
    }

    private AnthropicLlmClient client() {
        return AnthropicLlmClient.builder().endpoint(endpoint).apiKey("k").build();
    }

    @Test
    void streamsTextDeltasAndTerminalFinishReason() throws Exception {
        respond(200, "text/event-stream",
                "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-sonnet-4-6\"}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks).filteredOn(c -> !c.isLast())
                .extracting(LlmStreamChunk::delta)
                .containsExactly("Hello", " world");
        assertThat(chunks.get(chunks.size() - 1).isLast()).isTrue();
        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(requestBody.get()).contains("\"stream\":true");
    }

    @Test
    void streamsToolCallDeltas() throws Exception {
        respond(200, "text/event-stream",
                "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"checking\"}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"lookup\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"id\\\":\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"42}\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        List<LlmStreamChunk> toolDeltas = chunks.stream().filter(LlmStreamChunk::isToolCallDelta).toList();
        assertThat(toolDeltas).hasSize(3);
        assertThat(toolDeltas.get(0).toolCallDelta().nameDelta()).isEqualTo("lookup");
        assertThat(toolDeltas.get(0).toolCallDelta().index()).isZero();
        String args = toolDeltas.stream().map(c -> c.toolCallDelta().argsDelta()).reduce("", String::concat);
        assertThat(args).isEqualTo("{\"id\":42}");
        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
    }

    @Test
    void refusalStopReasonMapsToRefused() throws Exception {
        respond(200, "text/event-stream",
                "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"refusal\"}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.REFUSED);
    }

    @Test
    void non2xxSurfacesAsLlmHttpException() {
        respond(429, "application/json", "{\"error\":{\"type\":\"rate_limit_error\"}}");

        assertThatThrownBy(() -> collect(client().stream(request())))
                .hasCauseInstanceOf(LlmHttpException.class)
                .satisfies(e -> assertThat(((LlmHttpException) e.getCause()).statusCode()).isEqualTo(429));
    }

    @Test
    void malformedSseDataIsSkippedAndStreamContinues() throws Exception {
        respond(200, "text/event-stream",
                "data: {not json}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n");

        List<LlmStreamChunk> chunks = collect(client().stream(request()));

        assertThat(chunks).anyMatch(c -> c.delta().equals("ok"));
        assertThat(chunks.get(chunks.size() - 1).finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
    }
}
