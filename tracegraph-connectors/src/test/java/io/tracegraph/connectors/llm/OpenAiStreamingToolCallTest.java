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

class OpenAiStreamingToolCallTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respondSse(String sseBody) throws IOException {
        byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
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

    @Test
    void streamsContentDeltas() throws Exception {
        respondSse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmRequest req = LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("hi"))).build();

        List<LlmStreamChunk> chunks = collect(client.stream(req));

        List<LlmStreamChunk> content = chunks.stream()
                .filter(c -> !c.isLast() && !c.isToolCallDelta()).toList();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).delta()).isEqualTo("Hello");
        assertThat(content.get(1).delta()).isEqualTo(" world");

        LlmStreamChunk last = chunks.get(chunks.size() - 1);
        assertThat(last.isLast()).isTrue();
        assertThat(last.finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
    }

    @Test
    void streamsToolCallDeltas() throws Exception {
        respondSse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\","
                + "\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]},"
                + "\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"{\\\"city\\\":\\\"NYC\\\"}\"}}]},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\n"
                + "data: [DONE]\n");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmRequest req = LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("weather?"))).build();

        List<LlmStreamChunk> chunks = collect(client.stream(req));

        List<LlmStreamChunk> toolDeltas = chunks.stream()
                .filter(LlmStreamChunk::isToolCallDelta).toList();
        assertThat(toolDeltas).isNotEmpty();
        assertThat(toolDeltas.get(0).toolCallDelta().nameDelta()).isEqualTo("get_weather");
        assertThat(toolDeltas.get(0).toolCallDelta().index()).isEqualTo(0);
        // second chunk carries arguments delta
        assertThat(toolDeltas.get(1).toolCallDelta().argsDelta()).contains("NYC");

        LlmStreamChunk last = chunks.get(chunks.size() - 1);
        assertThat(last.isLast()).isTrue();
        assertThat(last.finishReason()).isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
    }

    @Test
    void propagatesNon2xxErrorFromStreamEndpoint() throws IOException {
        byte[] errBytes = "{\"error\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(429, errBytes.length);
            ex.getResponseBody().write(errBytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmRequest req = LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("hi"))).build();

        assertThatThrownBy(() -> collect(client.stream(req)))
                .isInstanceOf(java.util.concurrent.ExecutionException.class)
                .hasCauseInstanceOf(LlmHttpException.class);
    }

    @Test
    void streamRequestBodyContainsStreamTrue() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        String sseBody = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n";
        byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmRequest req = LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("hi"))).build();
        collect(client.stream(req));

        assertThat(capturedBody.get()).contains("\"stream\":true");
    }

    @Test
    void streamSendsAuthorizationHeader() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        String sseBody = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n";
        byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).apiKey("sk-test").build();
        LlmRequest req = LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("hi"))).build();
        collect(client.stream(req));

        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-test");
    }
}
