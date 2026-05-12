# AI Connectors Complete — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the remaining gaps in the AI connector layer: Gemini tool calling, DeepSeek client, four external VectorStore implementations, StructuredOutput retry loop, and Spring Boot auto-config wiring.

**Architecture:** All LLM clients stay in `tracegraph-connectors`; all VectorStore impls go in `tracegraph-rag` alongside the existing `InMemoryVectorStore`. Spring Boot wiring lives in `tracegraph-spring-boot-starter`. No new modules.

**Tech Stack:** JDK 21, JDK HttpClient + Jackson (already optional deps), JDBC (for PgVector), JUnit 5 + AssertJ, `com.sun.net.httpserver.HttpServer` for mock HTTP in tests.

**Build command:** `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot && mvn test`  
**Single-module test:** `set JAVA_HOME=... && mvn -pl tracegraph-connectors test` (or `-pl tracegraph-rag`)

---

## File Map

### Create
- `tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/DeepSeekLlmClient.java`
- `tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/DeepSeekLlmClientTest.java`
- `tracegraph-rag/src/main/java/io/tracegraph/rag/QdrantVectorStore.java`
- `tracegraph-rag/src/main/java/io/tracegraph/rag/WeaviateVectorStore.java`
- `tracegraph-rag/src/main/java/io/tracegraph/rag/PineconeVectorStore.java`
- `tracegraph-rag/src/main/java/io/tracegraph/rag/PgVectorStore.java`
- `tracegraph-rag/src/test/java/io/tracegraph/rag/QdrantVectorStoreTest.java`
- `tracegraph-rag/src/test/java/io/tracegraph/rag/WeaviateVectorStoreTest.java`
- `tracegraph-rag/src/test/java/io/tracegraph/rag/PineconeVectorStoreTest.java`
- `tracegraph-rag/src/test/java/io/tracegraph/rag/PgVectorStoreTest.java`
- `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/rag/VectorStoreAutoConfiguration.java`

### Modify
- `tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/GeminiLlmClient.java` — update DEFAULT_MODEL, add tool calling
- `tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/GeminiLlmClientTest.java` — add tool calling tests
- `tracegraph-connectors/src/main/java/io/tracegraph/connectors/structured/StructuredOutput.java` — add `extractWithRetry`
- `tracegraph-connectors/src/test/java/io/tracegraph/connectors/structured/StructuredOutputTest.java` — add retry tests
- `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/TraceGraphProperties.java` — add GEMINI, DEEPSEEK to Provider enum; add VectorStore properties
- `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/llm/LlmAutoConfiguration.java` — add GEMINI, DEEPSEEK beans
- `tracegraph-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — register VectorStoreAutoConfiguration

---

## Task 1: Update GeminiLlmClient default model + add tool calling

**Files:**
- Modify: `tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/GeminiLlmClient.java`
- Modify: `tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/GeminiLlmClientTest.java`

- [ ] **Step 1: Write failing test for Gemini tool calling**

Add to `GeminiLlmClientTest.java`:

```java
@Test
void sendsToolDefinitionsAndParsesToolCall() {
    // Gemini returns functionCall in the part
    respond(200, """
            {"candidates":[{"content":{"parts":[{"functionCall":{"name":"get_weather","args":{"city":"London"}}}],
             "role":"model"},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}""");

    ToolDefinition tool = new ToolDefinition("get_weather", "Get the weather",
            Map.of("type", "object", "properties",
                    Map.of("city", Map.of("type", "string"))));

    GeminiLlmClient client = GeminiLlmClient.builder().apiKey("k").build();
    LlmRequest request = LlmRequest.builder()
            .model("gemini-3-flash-preview")
            .messages(List.of(ChatMessage.user("What is the weather in London?")))
            .tools(List.of(tool))
            .build();
    LlmResponse r = client.complete(request);

    assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
    assertThat(r.toolCalls()).hasSize(1);
    assertThat(r.toolCalls().get(0).name()).isEqualTo("get_weather");
    assertThat(r.toolCalls().get(0).arguments()).contains("London");
}
```

- [ ] **Step 2: Run test to verify it fails**

```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot
mvn -pl tracegraph-connectors test -Dtest=GeminiLlmClientTest#sendsToolDefinitionsAndParsesToolCall
```

Expected: FAIL with `UnsupportedOperationException: Tool calling not yet supported`

- [ ] **Step 3: Update DEFAULT_MODEL and add tool calling to GeminiLlmClient**

Change line 22:
```java
private static final String DEFAULT_MODEL = "gemini-3-flash-preview";
```

Replace the `complete` method's tool guard and update `toRequestBody` and `parseResponse`:

```java
@Override
public LlmResponse complete(LlmRequest request) {
    // REMOVED: UnsupportedOperationException guard

    URI endpoint = URI.create(baseUrl + model + ":generateContent?key=" + apiKey);

    byte[] body;
    try {
        body = mapper.writeValueAsBytes(toRequestBody(request));
    } catch (IOException e) {
        throw new UncheckedIOException("Failed to serialize Gemini request", e);
    }

    HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (requestTimeout != null) rb.timeout(requestTimeout);

    HttpResponse<byte[]> response;
    try {
        response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
        throw new UncheckedIOException("Gemini request failed", e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Gemini request interrupted", e);
    }

    if (response.statusCode() / 100 != 2) {
        throw new LlmHttpException(response.statusCode(), new String(response.body()));
    }

    try {
        return parseResponse(mapper.readTree(response.body()));
    } catch (IOException e) {
        throw new UncheckedIOException("Failed to parse Gemini response", e);
    }
}
```

Replace `toRequestBody` to include tool declarations:

```java
private static Map<String, Object> toRequestBody(LlmRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("contents", toContents(request));
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("temperature", request.temperature());
    generationConfig.put("maxOutputTokens", request.maxTokens());
    body.put("generationConfig", generationConfig);

    if (request.hasTools()) {
        List<Map<String, Object>> functionDeclarations = new ArrayList<>();
        for (ToolDefinition td : request.tools()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", td.name());
            fn.put("description", td.description());
            if (!td.parametersSchema().isEmpty()) {
                fn.put("parameters", td.parametersSchema());
            }
            functionDeclarations.add(fn);
        }
        body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
    }

    return body;
}
```

Replace `parseResponse` to handle `functionCall` parts:

```java
private static LlmResponse parseResponse(JsonNode root) {
    JsonNode candidates = root.path("candidates");
    if (!candidates.isArray() || candidates.isEmpty()) {
        throw new IllegalStateException("Gemini response missing 'candidates'");
    }
    JsonNode first = candidates.get(0);
    JsonNode parts = first.path("content").path("parts");

    StringBuilder sb = new StringBuilder();
    List<ToolCall> toolCalls = new ArrayList<>();

    if (parts.isArray()) {
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.path("text").asText(""));
            } else if (part.has("functionCall")) {
                JsonNode fc = part.path("functionCall");
                String name = fc.path("name").asText("");
                String args = fc.path("args").toString();
                toolCalls.add(new ToolCall("", name, args));
            }
        }
    }

    String finishReasonRaw = first.path("finishReason").asText("");
    LlmResponse.FinishReason finish;
    if (!toolCalls.isEmpty()) {
        finish = LlmResponse.FinishReason.TOOL_CALLS;
    } else {
        finish = switch (finishReasonRaw) {
            case "STOP" -> LlmResponse.FinishReason.STOP;
            case "MAX_TOKENS" -> LlmResponse.FinishReason.LENGTH;
            default -> LlmResponse.FinishReason.OTHER;
        };
    }

    JsonNode usageMeta = root.path("usageMetadata");
    int promptTokens = usageMeta.path("promptTokenCount").asInt(0);
    int completionTokens = usageMeta.path("candidatesTokenCount").asInt(0);

    return new LlmResponse(sb.toString(), finish,
            new LlmResponse.Usage(promptTokens, completionTokens),
            List.copyOf(toolCalls));
}
```

Also add these imports to GeminiLlmClient.java:
```java
import io.tracegraph.connectors.llm.ToolCall;
import io.tracegraph.connectors.llm.ToolDefinition;
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-connectors test -Dtest=GeminiLlmClientTest
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/GeminiLlmClient.java
git add tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/GeminiLlmClientTest.java
git commit -m "feat(connectors): Gemini tool calling + update default model to gemini-3-flash-preview"
```

---

## Task 2: Add DeepSeekLlmClient

**Files:**
- Create: `tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/DeepSeekLlmClient.java`
- Create: `tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/DeepSeekLlmClientTest.java`

- [ ] **Step 1: Write failing test**

Create `DeepSeekLlmClientTest.java`:

```java
package io.tracegraph.connectors.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekLlmClientTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void respond(int status, String body) {
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @Test
    void completesSuccessfully() {
        respond(200, """
                {"choices":[{"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":1}}""");

        DeepSeekLlmClient client = DeepSeekLlmClient.builder()
                .apiKey("sk-test")
                .endpoint(endpoint)
                .build();

        LlmResponse r = client.complete(LlmRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(ChatMessage.user("Hi")))
                .build());

        assertThat(r.content()).isEqualTo("Hello");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(5);
    }

    @Test
    void propagatesHttpError() {
        respond(401, """
                {"error":{"message":"Incorrect API key"}}""");

        DeepSeekLlmClient client = DeepSeekLlmClient.builder()
                .apiKey("bad-key")
                .endpoint(endpoint)
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                client.complete(LlmRequest.builder()
                        .model("deepseek-chat")
                        .messages(List.of(ChatMessage.user("Hi")))
                        .build()))
                .isInstanceOf(LlmHttpException.class)
                .extracting("statusCode").isEqualTo(401);
    }

    @Test
    void defaultEndpointIsDeepSeek() {
        DeepSeekLlmClient client = DeepSeekLlmClient.builder().apiKey("k").build();
        assertThat(client.endpoint().toString())
                .isEqualTo("https://api.deepseek.com/v1/chat/completions");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-connectors test -Dtest=DeepSeekLlmClientTest
```

Expected: FAIL with compilation error (class does not exist)

- [ ] **Step 3: Implement DeepSeekLlmClient**

Create `tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/DeepSeekLlmClient.java`:

```java
package io.tracegraph.connectors.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * {@link LlmClient} for the DeepSeek chat API.
 *
 * <p>DeepSeek's API is fully compatible with the OpenAI chat-completions format.
 * This client is a thin builder wrapper around {@link OpenAiLlmClient} that defaults
 * the endpoint to {@code https://api.deepseek.com/v1/chat/completions}.
 */
public final class DeepSeekLlmClient implements LlmClient {

    static final URI DEFAULT_ENDPOINT =
            URI.create("https://api.deepseek.com/v1/chat/completions");

    private final URI endpoint;
    private final OpenAiLlmClient delegate;

    private DeepSeekLlmClient(Builder b) {
        this.endpoint = b.endpoint;
        this.delegate = OpenAiLlmClient.builder()
                .endpoint(b.endpoint)
                .apiKey(b.apiKey)
                .httpClient(b.httpClient)
                .requestTimeout(b.requestTimeout)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    /** Returns the configured endpoint URI (useful for testing). */
    public URI endpoint() { return endpoint; }

    @Override
    public LlmResponse complete(LlmRequest request) {
        return delegate.complete(request);
    }

    public static final class Builder {
        private URI endpoint = DEFAULT_ENDPOINT;
        private String apiKey;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            return this;
        }

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public DeepSeekLlmClient build() { return new DeepSeekLlmClient(this); }
    }
}
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-connectors test -Dtest=DeepSeekLlmClientTest
```

Expected: all 3 tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-connectors/src/main/java/io/tracegraph/connectors/llm/DeepSeekLlmClient.java
git add tracegraph-connectors/src/test/java/io/tracegraph/connectors/llm/DeepSeekLlmClientTest.java
git commit -m "feat(connectors): add DeepSeekLlmClient — delegates to OpenAI-compatible endpoint"
```

---

## Task 3: StructuredOutput.extractWithRetry

**Files:**
- Modify: `tracegraph-connectors/src/main/java/io/tracegraph/connectors/structured/StructuredOutput.java`
- Modify: `tracegraph-connectors/src/test/java/io/tracegraph/connectors/structured/StructuredOutputTest.java`

- [ ] **Step 1: Write failing test**

Add to `StructuredOutputTest.java`:

```java
@Test
void extractWithRetrySucceedsOnSecondAttempt() {
    // First response: invalid JSON. Second response: valid JSON.
    AtomicInteger calls = new AtomicInteger(0);
    LlmClient client = request -> {
        int call = calls.incrementAndGet();
        if (call == 1) {
            return new LlmResponse("not json at all", LlmResponse.FinishReason.STOP,
                    new LlmResponse.Usage(5, 3), List.of());
        }
        // Second call: check the error feedback was injected
        String lastMsg = request.messages().get(request.messages().size() - 1).content();
        assert lastMsg.contains("not be parsed") : "expected error feedback in prompt";
        return new LlmResponse("{\"value\":42}", LlmResponse.FinishReason.STOP,
                new LlmResponse.Usage(5, 5), List.of());
    };

    record Box(int value) {}
    StructuredOutput<Box> so = StructuredOutput.of(Box.class);
    LlmRequest request = LlmRequest.builder()
            .model("test-model")
            .messages(List.of(ChatMessage.user("give me a box")))
            .build();

    Box result = so.extractWithRetry(client, request, 3);

    assertThat(result.value()).isEqualTo(42);
    assertThat(calls.get()).isEqualTo(2);
}

@Test
void extractWithRetryThrowsAfterMaxAttempts() {
    LlmClient client = req -> new LlmResponse("bad", LlmResponse.FinishReason.STOP,
            new LlmResponse.Usage(1, 1), List.of());

    record Box(int value) {}
    StructuredOutput<Box> so = StructuredOutput.of(Box.class);
    LlmRequest request = LlmRequest.builder()
            .model("test-model")
            .messages(List.of(ChatMessage.user("give me a box")))
            .build();

    assertThatThrownBy(() -> so.extractWithRetry(client, request, 2))
            .isInstanceOf(StructuredOutputException.class);
}
```

Note: Add these imports to the test class:
```java
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.ChatMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-connectors test -Dtest=StructuredOutputTest
```

Expected: FAIL with compilation error (method does not exist)

- [ ] **Step 3: Implement extractWithRetry**

Add to `StructuredOutput.java` (new imports needed: `LlmClient`, `LlmRequest`, `ChatMessage`, `ArrayList`):

```java
import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import java.util.ArrayList;
import java.util.List;
```

Add method to `StructuredOutput<T>`:

```java
/**
 * Call {@code client} with {@code request}, attempt to parse the response, and on failure
 * inject the parse error back into the conversation and retry. Throws
 * {@link StructuredOutputException} after {@code maxAttempts} consecutive failures.
 *
 * @param client      LLM client to call
 * @param request     initial request; messages are extended on each retry
 * @param maxAttempts maximum number of LLM calls (must be >= 1)
 * @return parsed result
 */
public T extractWithRetry(LlmClient client, LlmRequest request, int maxAttempts) {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
    List<ChatMessage> messages = new ArrayList<>(request.messages());
    StructuredOutputException lastError = null;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
        LlmRequest currentRequest = LlmRequest.builder()
                .model(request.model())
                .messages(List.copyOf(messages))
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .tools(request.tools())
                .build();
        LlmResponse response = client.complete(currentRequest);
        try {
            return extract(response);
        } catch (StructuredOutputException e) {
            lastError = e;
            messages.add(ChatMessage.assistant(response.content()));
            messages.add(ChatMessage.user(
                    "Your previous response could not be parsed: " + e.getMessage()
                    + ". Please respond with valid JSON matching the expected schema."));
        }
    }
    throw lastError;
}
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-connectors test -Dtest=StructuredOutputTest
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-connectors/src/main/java/io/tracegraph/connectors/structured/StructuredOutput.java
git add tracegraph-connectors/src/test/java/io/tracegraph/connectors/structured/StructuredOutputTest.java
git commit -m "feat(connectors): StructuredOutput.extractWithRetry — inject parse error feedback into prompt"
```

---

## Task 4: QdrantVectorStore

**Files:**
- Create: `tracegraph-rag/src/main/java/io/tracegraph/rag/QdrantVectorStore.java`
- Create: `tracegraph-rag/src/test/java/io/tracegraph/rag/QdrantVectorStoreTest.java`

The existing `VectorStore` SPI (in `tracegraph-core`):
```java
// scope maps to the Qdrant collection name
void upsert(String scope, String id, float[] embedding, Map<String, String> metadata)
List<VectorMatch> query(String scope, float[] embedding, int topK)
void delete(String scope, String id)
record VectorMatch(String id, float score, Map<String, String> metadata) {}
```

Qdrant REST API:
- Upsert: `PUT http://{host}/collections/{collection}/points`  
  Body: `{"points": [{"id": "<id>", "vector": [...], "payload": {"key":"val",...}}]}`
- Search: `POST http://{host}/collections/{collection}/points/search`  
  Body: `{"vector": [...], "limit": N, "with_payload": true}`  
  Response: `{"result": [{"id":"...", "score": 0.9, "payload": {...}}]}`
- Delete: `POST http://{host}/collections/{collection}/points/delete`  
  Body: `{"points": ["<id>"]}`

- [ ] **Step 1: Write failing test**

Create `QdrantVectorStoreTest.java`:

```java
package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import io.tracegraph.core.spi.VectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantVectorStoreTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void upsertSendsCorrectPayload() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"result\":{\"operation_id\":0,\"status\":\"acknowledged\"},\"status\":\"ok\",\"time\":0.01}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();

        VectorStore store = QdrantVectorStore.builder().baseUrl(baseUrl).build();
        store.upsert("my-collection", "doc-1", new float[]{0.1f, 0.2f, 0.3f},
                Map.of("source", "test"));

        String body = capturedBody.get();
        assertThat(body).contains("\"doc-1\"");
        assertThat(body).contains("\"source\"");
        assertThat(body).contains("0.1");
    }

    @Test
    void queryReturnsMatches() throws Exception {
        server.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            String resp = """
                    {"result":[{"id":"doc-1","score":0.95,"payload":{"source":"test"}}],
                     "status":"ok","time":0.01}""";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        VectorStore store = QdrantVectorStore.builder().baseUrl(baseUrl).build();
        List<VectorStore.VectorMatch> results =
                store.query("my-collection", new float[]{0.1f, 0.2f, 0.3f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("doc-1");
        assertThat(results.get(0).score()).isEqualTo(0.95f);
        assertThat(results.get(0).metadata()).containsEntry("source", "test");
    }

    @Test
    void deleteSendsPointId() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"result\":{\"operation_id\":1,\"status\":\"acknowledged\"},\"status\":\"ok\",\"time\":0.01}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();

        VectorStore store = QdrantVectorStore.builder().baseUrl(baseUrl).build();
        store.delete("my-collection", "doc-1");

        assertThat(capturedBody.get()).contains("doc-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-rag test -Dtest=QdrantVectorStoreTest
```

Expected: FAIL with compilation error (class does not exist)

- [ ] **Step 3: Implement QdrantVectorStore**

Create `tracegraph-rag/src/main/java/io/tracegraph/rag/QdrantVectorStore.java`:

```java
package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.VectorStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link VectorStore} backed by a Qdrant instance.
 *
 * <p>{@code scope} maps to the Qdrant collection name. The collection must already exist
 * before calling {@link #upsert}; this client does not create collections automatically.
 */
public final class QdrantVectorStore implements VectorStore {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private QdrantVectorStore(Builder b) {
        this.baseUrl = b.baseUrl.replaceAll("/$", "");
        this.apiKey = b.apiKey;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", id);
        point.put("vector", toList(embedding));
        point.put("payload", metadata);
        Map<String, Object> body = Map.of("points", List.of(point));

        send("PUT", baseUrl + "/collections/" + scope + "/points", body);
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", toList(embedding));
        body.put("limit", topK);
        body.put("with_payload", true);

        JsonNode root = send("POST", baseUrl + "/collections/" + scope + "/points/search", body);
        JsonNode result = root.path("result");
        List<VectorMatch> matches = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                String matchId = node.path("id").asText();
                float score = (float) node.path("score").asDouble();
                Map<String, String> meta = new HashMap<>();
                node.path("payload").fields().forEachRemaining(e ->
                        meta.put(e.getKey(), e.getValue().asText()));
                matches.add(new VectorMatch(matchId, score, Map.copyOf(meta)));
            }
        }
        return matches;
    }

    @Override
    public void delete(String scope, String id) {
        Map<String, Object> body = Map.of("points", List.of(id));
        send("POST", baseUrl + "/collections/" + scope + "/points/delete", body);
    }

    private JsonNode send(String method, String url, Map<String, Object> body) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Qdrant request", e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (apiKey != null) rb.header("api-key", apiKey);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Qdrant request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new VectorStoreException(response.statusCode(), new String(response.body()));
        }

        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Qdrant response", e);
        }
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:6333";
        private String apiKey;
        private HttpClient httpClient;

        private Builder() {}

        public Builder baseUrl(String baseUrl) { this.baseUrl = Objects.requireNonNull(baseUrl); return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }

        public QdrantVectorStore build() { return new QdrantVectorStore(this); }
    }
}
```

Also create `tracegraph-rag/src/main/java/io/tracegraph/rag/VectorStoreException.java`:

```java
package io.tracegraph.rag;

public final class VectorStoreException extends RuntimeException {

    private final int statusCode;

    public VectorStoreException(int statusCode, String body) {
        super("Vector store HTTP " + statusCode + ": " + body);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-rag test -Dtest=QdrantVectorStoreTest
```

Expected: all 3 tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-rag/src/main/java/io/tracegraph/rag/QdrantVectorStore.java
git add tracegraph-rag/src/main/java/io/tracegraph/rag/VectorStoreException.java
git add tracegraph-rag/src/test/java/io/tracegraph/rag/QdrantVectorStoreTest.java
git commit -m "feat(rag): QdrantVectorStore — REST adapter for Qdrant vector database"
```

---

## Task 5: WeaviateVectorStore

**Files:**
- Create: `tracegraph-rag/src/main/java/io/tracegraph/rag/WeaviateVectorStore.java`
- Create: `tracegraph-rag/src/test/java/io/tracegraph/rag/WeaviateVectorStoreTest.java`

Weaviate REST API:
- Upsert: `POST http://{host}/v1/objects` Body: `{"class":"<scope>","id":"<id>","vector":[...],"properties":{"_text":"...","key":"val",...}}`
- Search: `POST http://{host}/v1/graphql` Body: `{"query":"{ Get { <scope>(nearVector: {vector: [...]}, limit: N) { _additional { id distance } <props> } } }"}`
- Delete: `DELETE http://{host}/v1/objects/<scope>/<id>`

- [ ] **Step 1: Write failing test**

Create `WeaviateVectorStoreTest.java`:

```java
package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import io.tracegraph.core.spi.VectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WeaviateVectorStoreTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void upsertSendsObjectWithVector() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/objects", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"id\":\"doc-1\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();

        VectorStore store = WeaviateVectorStore.builder().baseUrl(baseUrl).build();
        store.upsert("Articles", "doc-1", new float[]{0.1f, 0.2f}, Map.of("author", "alice"));

        String body = capturedBody.get();
        assertThat(body).contains("\"Articles\"");
        assertThat(body).contains("\"doc-1\"");
        assertThat(body).contains("\"author\"");
    }

    @Test
    void queryUsesGraphql() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        server.createContext("/v1/graphql", ex -> {
            capturedPath.set(ex.getRequestURI().getPath());
            ex.getRequestBody().readAllBytes();
            String resp = """
                    {"data":{"Get":{"Articles":[
                      {"_additional":{"id":"doc-1","distance":0.05}}
                    ]}}}""";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        VectorStore store = WeaviateVectorStore.builder().baseUrl(baseUrl).build();
        List<VectorStore.VectorMatch> results =
                store.query("Articles", new float[]{0.1f, 0.2f}, 3);

        assertThat(capturedPath.get()).isEqualTo("/v1/graphql");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("doc-1");
        // distance 0.05 → score = 1 - 0.05 = 0.95
        assertThat(results.get(0).score()).isCloseTo(0.95f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void deleteCallsDeleteEndpoint() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        server.createContext("/v1/objects/", ex -> {
            capturedMethod.set(ex.getRequestMethod());
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();

        VectorStore store = WeaviateVectorStore.builder().baseUrl(baseUrl).build();
        store.delete("Articles", "doc-1");

        assertThat(capturedMethod.get()).isEqualTo("DELETE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-rag test -Dtest=WeaviateVectorStoreTest
```

Expected: FAIL with compilation error

- [ ] **Step 3: Implement WeaviateVectorStore**

Create `tracegraph-rag/src/main/java/io/tracegraph/rag/WeaviateVectorStore.java`:

```java
package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.VectorStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link VectorStore} backed by a Weaviate instance.
 *
 * <p>{@code scope} maps to the Weaviate class name (case-sensitive, must start with uppercase).
 * The class must already exist in the schema before calling {@link #upsert}.
 */
public final class WeaviateVectorStore implements VectorStore {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private WeaviateVectorStore(Builder b) {
        this.baseUrl = b.baseUrl.replaceAll("/$", "");
        this.apiKey = b.apiKey;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("class", scope);
        body.put("id", id);
        body.put("vector", toList(embedding));
        body.put("properties", metadata);

        sendJson("POST", baseUrl + "/v1/objects", body);
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        String vectorStr = toList(embedding).stream()
                .map(Object::toString).collect(Collectors.joining(","));
        String gql = String.format(
                "{ Get { %s(nearVector: {vector: [%s]}, limit: %d) { _additional { id distance } } } }",
                scope, vectorStr, topK);

        Map<String, Object> body = Map.of("query", gql);
        JsonNode root = sendJson("POST", baseUrl + "/v1/graphql", body);

        JsonNode items = root.path("data").path("Get").path(scope);
        List<VectorMatch> matches = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                JsonNode additional = item.path("_additional");
                String matchId = additional.path("id").asText();
                float distance = (float) additional.path("distance").asDouble();
                float score = Math.max(0f, 1f - distance);
                // metadata: all fields except _additional
                Map<String, String> meta = new HashMap<>();
                item.fields().forEachRemaining(e -> {
                    if (!"_additional".equals(e.getKey())) {
                        meta.put(e.getKey(), e.getValue().asText());
                    }
                });
                matches.add(new VectorMatch(matchId, score, Map.copyOf(meta)));
            }
        }
        return matches;
    }

    @Override
    public void delete(String scope, String id) {
        HttpRequest request = buildRequest("DELETE",
                baseUrl + "/v1/objects/" + scope + "/" + id, null);
        send(request);
    }

    private JsonNode sendJson(String method, String url, Map<String, Object> body) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Weaviate request", e);
        }
        HttpRequest request = buildRequest(method, url, bytes);
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() / 100 != 2) {
            throw new VectorStoreException(response.statusCode(), new String(response.body()));
        }
        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Weaviate response", e);
        }
    }

    private HttpRequest buildRequest(String method, String url, byte[] body) {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json");
        if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
        if (body != null) {
            rb.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return rb.build();
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Weaviate request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Weaviate request interrupted", e);
        }
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:8080";
        private String apiKey;
        private HttpClient httpClient;

        private Builder() {}

        public Builder baseUrl(String baseUrl) { this.baseUrl = Objects.requireNonNull(baseUrl); return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }

        public WeaviateVectorStore build() { return new WeaviateVectorStore(this); }
    }
}
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-rag test -Dtest=WeaviateVectorStoreTest
```

Expected: all 3 tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-rag/src/main/java/io/tracegraph/rag/WeaviateVectorStore.java
git add tracegraph-rag/src/test/java/io/tracegraph/rag/WeaviateVectorStoreTest.java
git commit -m "feat(rag): WeaviateVectorStore — REST + GraphQL adapter for Weaviate"
```

---

## Task 6: PineconeVectorStore

**Files:**
- Create: `tracegraph-rag/src/main/java/io/tracegraph/rag/PineconeVectorStore.java`
- Create: `tracegraph-rag/src/test/java/io/tracegraph/rag/PineconeVectorStoreTest.java`

Pinecone REST API (uses index host, not a base URL):
- Upsert: `POST https://{indexHost}/vectors/upsert`  
  Body: `{"vectors":[{"id":"<id>","values":[...],"metadata":{"k":"v"},"namespace":"<scope>"}],"namespace":"<scope>"}`
- Query: `POST https://{indexHost}/query`  
  Body: `{"vector":[...],"topK":N,"namespace":"<scope>","includeMetadata":true}`  
  Response: `{"matches":[{"id":"...","score":0.9,"metadata":{"k":"v"}}]}`
- Delete: `POST https://{indexHost}/vectors/delete`  
  Body: `{"ids":["<id>"],"namespace":"<scope>"}`

- [ ] **Step 1: Write failing test**

Create `PineconeVectorStoreTest.java`:

```java
package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import io.tracegraph.core.spi.VectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PineconeVectorStoreTest {

    private HttpServer server;
    private String indexHost;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        indexHost = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void upsertSendsNamespacedVector() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/vectors/upsert", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"upsertedCount\":1}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();

        VectorStore store = PineconeVectorStore.builder().indexHost(indexHost).apiKey("test-key").build();
        store.upsert("prod-ns", "v-1", new float[]{0.1f, 0.9f}, Map.of("tag", "news"));

        String body = capturedBody.get();
        assertThat(body).contains("\"prod-ns\"");
        assertThat(body).contains("\"v-1\"");
        assertThat(body).contains("\"tag\"");
    }

    @Test
    void queryReturnsMatchesWithScore() throws Exception {
        server.createContext("/query", ex -> {
            ex.getRequestBody().readAllBytes();
            String resp = """
                    {"matches":[{"id":"v-1","score":0.88,"metadata":{"tag":"news"}}]}""";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        VectorStore store = PineconeVectorStore.builder().indexHost(indexHost).apiKey("test-key").build();
        List<VectorStore.VectorMatch> results =
                store.query("prod-ns", new float[]{0.1f, 0.9f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("v-1");
        assertThat(results.get(0).score()).isCloseTo(0.88f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(results.get(0).metadata()).containsEntry("tag", "news");
    }

    @Test
    void deleteSendsNamespacedId() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/vectors/delete", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();

        VectorStore store = PineconeVectorStore.builder().indexHost(indexHost).apiKey("test-key").build();
        store.delete("prod-ns", "v-1");

        String body = capturedBody.get();
        assertThat(body).contains("\"prod-ns\"");
        assertThat(body).contains("\"v-1\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-rag test -Dtest=PineconeVectorStoreTest
```

Expected: FAIL with compilation error

- [ ] **Step 3: Implement PineconeVectorStore**

Create `tracegraph-rag/src/main/java/io/tracegraph/rag/PineconeVectorStore.java`:

```java
package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.VectorStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link VectorStore} backed by a Pinecone index.
 *
 * <p>{@code scope} maps to the Pinecone namespace. Requires the index host URL
 * (e.g. {@code https://my-index-abc123.svc.pinecone.io}) and an API key.
 */
public final class PineconeVectorStore implements VectorStore {

    private final String indexHost;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private PineconeVectorStore(Builder b) {
        this.indexHost = Objects.requireNonNull(b.indexHost, "indexHost").replaceAll("/$", "");
        this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey");
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("id", id);
        vector.put("values", toList(embedding));
        vector.put("metadata", metadata);
        vector.put("namespace", scope);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", List.of(vector));
        body.put("namespace", scope);

        send("POST", indexHost + "/vectors/upsert", body);
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", toList(embedding));
        body.put("topK", topK);
        body.put("namespace", scope);
        body.put("includeMetadata", true);

        JsonNode root = send("POST", indexHost + "/query", body);
        JsonNode matches = root.path("matches");
        List<VectorMatch> results = new ArrayList<>();
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                String matchId = m.path("id").asText();
                float score = (float) m.path("score").asDouble();
                Map<String, String> meta = new HashMap<>();
                m.path("metadata").fields().forEachRemaining(e ->
                        meta.put(e.getKey(), e.getValue().asText()));
                results.add(new VectorMatch(matchId, score, Map.copyOf(meta)));
            }
        }
        return results;
    }

    @Override
    public void delete(String scope, String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(id));
        body.put("namespace", scope);
        send("POST", indexHost + "/vectors/delete", body);
    }

    private JsonNode send(String method, String url, Map<String, Object> body) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Pinecone request", e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Api-Key", apiKey)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Pinecone request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Pinecone request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new VectorStoreException(response.statusCode(), new String(response.body()));
        }

        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Pinecone response", e);
        }
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    public static final class Builder {
        private String indexHost;
        private String apiKey;
        private HttpClient httpClient;

        private Builder() {}

        public Builder indexHost(String indexHost) { this.indexHost = indexHost; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }

        public PineconeVectorStore build() { return new PineconeVectorStore(this); }
    }
}
```

- [ ] **Step 4: Run tests**

```
mvn -pl tracegraph-rag test -Dtest=PineconeVectorStoreTest
```

Expected: all 3 tests PASS

- [ ] **Step 5: Commit**

```
git add tracegraph-rag/src/main/java/io/tracegraph/rag/PineconeVectorStore.java
git add tracegraph-rag/src/test/java/io/tracegraph/rag/PineconeVectorStoreTest.java
git commit -m "feat(rag): PineconeVectorStore — REST adapter for Pinecone vector database"
```

---

## Task 7: PgVectorStore

**Files:**
- Create: `tracegraph-rag/src/main/java/io/tracegraph/rag/PgVectorStore.java`
- Create: `tracegraph-rag/src/test/java/io/tracegraph/rag/PgVectorStoreTest.java`

Schema (must already have pgvector extension installed):
```sql
CREATE TABLE IF NOT EXISTS tracegraph_vectors (
  scope    TEXT NOT NULL,
  id       TEXT NOT NULL,
  embedding VECTOR(N),
  metadata  JSONB,
  PRIMARY KEY (scope, id)
);
```

Because JDBC drivers don't know the `vector` type natively, pass vectors as cast strings: `?::vector` with value `[0.1,0.2,0.3]`. For tests use H2 which doesn't support pgvector — we test schema init and SQL structure with a mock `DataSource`.

- [ ] **Step 1: Write failing test**

Create `PgVectorStoreTest.java`:

```java
package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PgVectorStoreTest {

    @Test
    void initSchemaExecutesCreateTable() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenReturn(false);

        PgVectorStore store = PgVectorStore.builder().dataSource(ds).dimension(3).build();
        store.initSchema();

        verify(stmt).execute(argThat(sql -> sql.contains("tracegraph_vectors")));
        verify(conn).close();
    }

    @Test
    void upsertCallsPreparedStatement() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);

        PgVectorStore store = PgVectorStore.builder().dataSource(ds).dimension(3).build();
        store.upsert("ns", "id-1", new float[]{0.1f, 0.2f, 0.3f}, Map.of("k", "v"));

        verify(ps).setString(1, "ns");
        verify(ps).setString(2, "id-1");
        verify(ps).executeUpdate();
        verify(conn).close();
    }

    @Test
    void queryCallsPreparedStatementAndReturnsMatches() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("id-1");
        when(rs.getFloat("score")).thenReturn(0.9f);
        when(rs.getString("metadata")).thenReturn("{\"k\":\"v\"}");

        PgVectorStore store = PgVectorStore.builder().dataSource(ds).dimension(3).build();
        List<VectorStore.VectorMatch> matches =
                store.query("ns", new float[]{0.1f, 0.2f, 0.3f}, 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).id()).isEqualTo("id-1");
        assertThat(matches.get(0).score()).isEqualTo(0.9f);
        assertThat(matches.get(0).metadata()).containsEntry("k", "v");
    }
}
```

Note: this test uses Mockito. Add to `tracegraph-rag/pom.xml` test scope:
```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
</dependency>
```

Check the parent POM for the Mockito version property first — if `mockito.version` is defined there, use `${mockito.version}` instead of a hardcoded version.

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl tracegraph-rag test -Dtest=PgVectorStoreTest
```

Expected: FAIL with compilation error

- [ ] **Step 3: Implement PgVectorStore**

Create `tracegraph-rag/src/main/java/io/tracegraph/rag/PgVectorStore.java`:

```java
package io.tracegraph.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.VectorStore;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link VectorStore} backed by PostgreSQL with the pgvector extension.
 *
 * <p>{@code scope} maps to a namespace column. A single table stores all scopes.
 * The pgvector extension must be installed and the table created via {@link #initSchema()}
 * before first use. Vectors are passed as cast strings ({@code '[0.1,0.2]'::vector}).
 *
 * <p>JDBC is an optional runtime dep — consumers must ensure a JDBC driver is on the classpath.
 */
public final class PgVectorStore implements VectorStore {

    private static final TypeReference<Map<String, String>> META_TYPE =
            new TypeReference<>() {};

    private final DataSource dataSource;
    private final String table;
    private final int dimension;
    private final ObjectMapper mapper;

    private PgVectorStore(Builder b) {
        this.dataSource = Objects.requireNonNull(b.dataSource, "dataSource");
        this.table = b.table;
        this.dimension = b.dimension;
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Creates the {@code tracegraph_vectors} table if it does not exist.
     * Idempotent — safe to call on every startup.
     */
    public void initSchema() {
        String sql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                  scope     TEXT NOT NULL,
                  id        TEXT NOT NULL,
                  embedding vector(%d),
                  metadata  JSONB,
                  PRIMARY KEY (scope, id)
                )""", table, dimension);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init PgVectorStore schema", e);
        }
    }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        String sql = String.format("""
                INSERT INTO %s (scope, id, embedding, metadata)
                VALUES (?, ?, ?::vector, ?::jsonb)
                ON CONFLICT (scope, id) DO UPDATE
                  SET embedding = EXCLUDED.embedding,
                      metadata  = EXCLUDED.metadata""", table);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, id);
            ps.setString(3, toVectorLiteral(embedding));
            ps.setString(4, mapper.writeValueAsString(metadata));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PgVectorStore upsert failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize metadata", e);
        }
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        String sql = String.format("""
                SELECT id, 1 - (embedding <=> ?::vector) AS score, metadata
                FROM %s
                WHERE scope = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?""", table);
        String vectorLiteral = toVectorLiteral(embedding);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorLiteral);
            ps.setString(2, scope);
            ps.setString(3, vectorLiteral);
            ps.setInt(4, topK);
            try (ResultSet rs = ps.executeQuery()) {
                List<VectorMatch> matches = new ArrayList<>();
                while (rs.next()) {
                    String matchId = rs.getString("id");
                    float score = rs.getFloat("score");
                    String metaJson = rs.getString("metadata");
                    Map<String, String> meta = metaJson == null ? Map.of()
                            : mapper.readValue(metaJson, META_TYPE);
                    matches.add(new VectorMatch(matchId, score, meta));
                }
                return matches;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PgVectorStore query failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse metadata", e);
        }
    }

    @Override
    public void delete(String scope, String id) {
        String sql = String.format("DELETE FROM %s WHERE scope = ? AND id = ?", table);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PgVectorStore delete failed", e);
        }
    }

    private static String toVectorLiteral(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static final class Builder {
        private DataSource dataSource;
        private String table = "tracegraph_vectors";
        private int dimension = 1536;

        private Builder() {}

        public Builder dataSource(DataSource dataSource) { this.dataSource = dataSource; return this; }
        public Builder table(String table) { this.table = table; return this; }
        public Builder dimension(int dimension) { this.dimension = dimension; return this; }

        public PgVectorStore build() { return new PgVectorStore(this); }
    }
}
```

- [ ] **Step 4: Check parent POM for Mockito version, add dep to tracegraph-rag/pom.xml**

```
mvn -pl . help:effective-pom | grep -i mockito
```

If `mockito.version` is defined, use `${mockito.version}` in the dependency. Otherwise use `5.11.0`.

Add to `tracegraph-rag/pom.xml` inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 5: Run tests**

```
mvn -pl tracegraph-rag test -Dtest=PgVectorStoreTest
```

Expected: all 3 tests PASS

- [ ] **Step 6: Commit**

```
git add tracegraph-rag/src/main/java/io/tracegraph/rag/PgVectorStore.java
git add tracegraph-rag/src/test/java/io/tracegraph/rag/PgVectorStoreTest.java
git add tracegraph-rag/pom.xml
git commit -m "feat(rag): PgVectorStore — JDBC adapter for PostgreSQL + pgvector extension"
```

---

## Task 8: Spring Boot wiring — GEMINI, DEEPSEEK + VectorStoreAutoConfiguration

**Files:**
- Modify: `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/TraceGraphProperties.java`
- Modify: `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/llm/LlmAutoConfiguration.java`
- Create: `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/rag/VectorStoreAutoConfiguration.java`
- Modify: `tracegraph-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Add GEMINI, DEEPSEEK to Provider enum and add VectorStore properties**

In `TraceGraphProperties.java`, find `public enum Provider { OPENAI, ANTHROPIC }` and replace with:

```java
public enum Provider { OPENAI, ANTHROPIC, GEMINI, DEEPSEEK }
```

Then add `VectorStore` inner class at the bottom of `TraceGraphProperties`, before the closing `}`:

```java
private final VectorStore vectorStore = new VectorStore();

public VectorStore getVectorStore() { return vectorStore; }

public static class VectorStore {
    public enum Provider { QDRANT, WEAVIATE, PINECONE, PGVECTOR }

    private Provider provider;
    private String url;
    private String apiKey;
    private String collection;
    private int dimension = 1536;

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
}
```

- [ ] **Step 2: Add GEMINI and DEEPSEEK beans to LlmAutoConfiguration**

Add these imports to `LlmAutoConfiguration.java`:
```java
import io.tracegraph.connectors.llm.DeepSeekLlmClient;
import io.tracegraph.connectors.llm.GeminiLlmClient;
```

Add two new `@Bean` methods after the existing `anthropicLlmClient` bean:

```java
@Bean
@ConditionalOnMissingBean(LlmClient.class)
@ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "gemini")
public LlmClient geminiLlmClient(TraceGraphProperties properties) {
    TraceGraphProperties.Llm llm = properties.getLlm();
    GeminiLlmClient.Builder b = GeminiLlmClient.builder();
    if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
    if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
    return b.build();
}

@Bean
@ConditionalOnMissingBean(LlmClient.class)
@ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "deepseek")
public LlmClient deepSeekLlmClient(TraceGraphProperties properties) {
    TraceGraphProperties.Llm llm = properties.getLlm();
    DeepSeekLlmClient.Builder b = DeepSeekLlmClient.builder();
    if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
    if (llm.getEndpoint() != null) b.endpoint(URI.create(llm.getEndpoint()));
    if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
    return b.build();
}
```

- [ ] **Step 3: Create VectorStoreAutoConfiguration**

Create `tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/rag/VectorStoreAutoConfiguration.java`:

```java
package io.tracegraph.boot.rag;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.PgVectorStore;
import io.tracegraph.rag.PineconeVectorStore;
import io.tracegraph.rag.QdrantVectorStore;
import io.tracegraph.rag.WeaviateVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Auto-configures a {@link VectorStore} bean based on
 * {@code tracegraph.vectorstore.provider}.
 *
 * <p>Supported providers: {@code qdrant}, {@code weaviate}, {@code pinecone}, {@code pgvector}.
 * No bean is registered when {@code tracegraph.vectorstore.provider} is not set.
 */
@AutoConfiguration
@ConditionalOnClass(VectorStore.class)
@EnableConfigurationProperties(TraceGraphProperties.class)
public class VectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.vectorstore", name = "provider", havingValue = "qdrant")
    public VectorStore qdrantVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.VectorStore vs = properties.getVectorStore();
        QdrantVectorStore.Builder b = QdrantVectorStore.builder();
        if (vs.getUrl() != null) b.baseUrl(vs.getUrl());
        if (vs.getApiKey() != null) b.apiKey(vs.getApiKey());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.vectorstore", name = "provider", havingValue = "weaviate")
    public VectorStore weaviateVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.VectorStore vs = properties.getVectorStore();
        WeaviateVectorStore.Builder b = WeaviateVectorStore.builder();
        if (vs.getUrl() != null) b.baseUrl(vs.getUrl());
        if (vs.getApiKey() != null) b.apiKey(vs.getApiKey());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.vectorstore", name = "provider", havingValue = "pinecone")
    public VectorStore pineconeVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.VectorStore vs = properties.getVectorStore();
        return PineconeVectorStore.builder()
                .indexHost(vs.getUrl())
                .apiKey(vs.getApiKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnProperty(prefix = "tracegraph.vectorstore", name = "provider", havingValue = "pgvector")
    public VectorStore pgVectorStore(TraceGraphProperties properties, DataSource dataSource) {
        TraceGraphProperties.VectorStore vs = properties.getVectorStore();
        PgVectorStore store = PgVectorStore.builder()
                .dataSource(dataSource)
                .dimension(vs.getDimension())
                .build();
        store.initSchema();
        return store;
    }
}
```

- [ ] **Step 4: Register in AutoConfiguration.imports**

Add to the end of `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.tracegraph.boot.rag.VectorStoreAutoConfiguration
```

- [ ] **Step 5: Run starter tests**

```
mvn -pl tracegraph-spring-boot-starter test
```

Expected: all tests PASS (no existing tests should break)

- [ ] **Step 6: Run full build**

```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot
mvn -B test
```

Expected: BUILD SUCCESS, all modules green

- [ ] **Step 7: Commit**

```
git add tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/TraceGraphProperties.java
git add tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/llm/LlmAutoConfiguration.java
git add tracegraph-spring-boot-starter/src/main/java/io/tracegraph/boot/rag/VectorStoreAutoConfiguration.java
git add tracegraph-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git commit -m "feat(starter): auto-config for GEMINI, DEEPSEEK LLM providers and Qdrant/Weaviate/Pinecone/PgVector VectorStore"
```

---

## Self-Review Checklist

- [x] **Spec coverage:**
  - Gemini tool calling → Task 1 ✅
  - Default model updates → Task 1 (Gemini), DeepSeek uses model-per-request (consistent with OpenAI/Anthropic) ✅
  - DeepSeekLlmClient → Task 2 ✅
  - StructuredOutput.extractWithRetry → Task 3 ✅
  - QdrantVectorStore → Task 4 ✅
  - WeaviateVectorStore → Task 5 ✅
  - PineconeVectorStore → Task 6 ✅
  - PgVectorStore → Task 7 ✅
  - Spring Boot auto-config → Task 8 ✅

- [x] **Placeholder scan:** All steps have actual code. No TBDs.

- [x] **Type consistency:**
  - `VectorStore.VectorMatch` used consistently — it's a nested record in the SPI, so always `VectorStore.VectorMatch` in tests and `VectorMatch` inside implementations.
  - `DeepSeekLlmClient.endpoint()` accessor used in test — defined in implementation ✅
  - `StructuredOutput.extractWithRetry` signature: `(LlmClient, LlmRequest, int)` — consistent across test and impl ✅
  - `VectorStoreAutoConfiguration` references `TraceGraphProperties.VectorStore` — defined in Task 8 Step 1 before used in Step 3 ✅
