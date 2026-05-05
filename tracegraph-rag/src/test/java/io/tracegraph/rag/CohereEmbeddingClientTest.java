package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CohereEmbeddingClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/embed", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void embedReturnsParsedFloatArrays() {
        CohereEmbeddingClient client = CohereEmbeddingClient.builder()
                .apiKey("test-key")
                .httpClient(buildRedirectingClient(port))
                .build();

        List<float[]> result = client.embed(List.of("hello", "world"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(new float[]{0.1f, 0.2f}, within(1e-6f));
        assertThat(result.get(1)).containsExactly(new float[]{0.3f, 0.4f}, within(1e-6f));
    }

    @Test
    void requestBodyContainsTextsAndModel() {
        CohereEmbeddingClient client = CohereEmbeddingClient.builder()
                .apiKey("key")
                .model("embed-english-v3.0")
                .httpClient(buildRedirectingClient(port))
                .build();

        client.embed(List.of("sample"));

        String body = lastRequestBody.get();
        assertThat(body).contains("\"texts\":[\"sample\"]");
        assertThat(body).contains("\"model\":\"embed-english-v3.0\"");
        assertThat(body).contains("\"input_type\":\"search_document\"");
    }

    @Test
    void sendsAuthorizationHeader() {
        CohereEmbeddingClient client = CohereEmbeddingClient.builder()
                .apiKey("mykey")
                .httpClient(buildRedirectingClient(port))
                .build();

        client.embed(List.of("x"));

        assertThat(lastAuthHeader.get()).isEqualTo("Bearer mykey");
    }

    @Test
    void builderRequiresApiKey() {
        assertThatThrownBy(() -> CohereEmbeddingClient.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsEmbeddingHttpExceptionOnNonSuccess() throws Exception {
        HttpServer errServer = HttpServer.create(new InetSocketAddress(0), 0);
        int errPort = errServer.getAddress().getPort();
        errServer.createContext("/v1/embed", exchange -> {
            exchange.sendResponseHeaders(429, 0);
            exchange.close();
        });
        errServer.start();

        CohereEmbeddingClient client = CohereEmbeddingClient.builder()
                .apiKey("key")
                .httpClient(buildRedirectingClient(errPort))
                .build();

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(EmbeddingHttpException.class)
                .satisfies(ex -> assertThat(((EmbeddingHttpException) ex).statusCode()).isEqualTo(429));

        errServer.stop(0);
    }

    private static HttpClient buildRedirectingClient(int targetPort) {
        return new LocalRedirectingClient(targetPort);
    }

    private static float withPrecision(float precision) {
        return precision;
    }

    static final class LocalRedirectingClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newHttpClient();
        private final int targetPort;

        LocalRedirectingClient(int targetPort) {
            this.targetPort = targetPort;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws java.io.IOException, InterruptedException {
            URI original = request.uri();
            URI rewritten = URI.create("http://localhost:" + targetPort + original.getRawPath());
            HttpRequest rewrittenRequest = HttpRequest.newBuilder(request, (name, value) -> true)
                    .uri(rewritten)
                    .build();
            return delegate.send(rewrittenRequest, responseBodyHandler);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }

        @Override
        public java.util.Optional<Duration> connectTimeout() { return java.util.Optional.empty(); }

        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }

        @Override
        public javax.net.ssl.SSLContext sslContext() { return null; }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() { return null; }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }

        @Override
        public java.net.http.HttpClient.Version version() { return Version.HTTP_1_1; }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }
}
