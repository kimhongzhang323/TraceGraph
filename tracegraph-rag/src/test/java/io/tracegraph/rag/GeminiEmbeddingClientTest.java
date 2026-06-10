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

class GeminiEmbeddingClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastUri = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            lastUri.set(exchange.getRequestURI().toString());
            byte[] response = "{\"embedding\":{\"values\":[0.5,0.6,0.7]}}".getBytes(StandardCharsets.UTF_8);
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
    void embedReturnsParsedFloatArray() throws Exception {
        // Use a custom HttpClient that redirects to our local server
        HttpClient delegatingClient = buildRedirectingClient(port);

        GeminiEmbeddingClient client = GeminiEmbeddingClient.builder()
                .apiKey("test-key")
                .model("text-embedding-004")
                .httpClient(delegatingClient)
                .build();

        List<float[]> result = client.embed(List.of("hello"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(new float[]{0.5f, 0.6f, 0.7f}, within(1e-6f));
    }

    @Test
    void requestBodyContainsTextPart() throws Exception {
        HttpClient delegatingClient = buildRedirectingClient(port);

        GeminiEmbeddingClient client = GeminiEmbeddingClient.builder()
                .apiKey("key")
                .httpClient(delegatingClient)
                .build();

        client.embed(List.of("some text"));

        assertThat(lastRequestBody.get()).contains("\"text\":\"some text\"");
    }

    @Test
    void sendsApiKeyAsHeaderNotQueryParameter() throws Exception {
        GeminiEmbeddingClient client = GeminiEmbeddingClient.builder()
                .apiKey("secret-key")
                .httpClient(buildRedirectingClient(port))
                .build();

        client.embed(List.of("hello"));

        assertThat(lastApiKeyHeader.get()).isEqualTo("secret-key");
        assertThat(lastUri.get()).doesNotContain("secret-key").doesNotContain("key=");
    }

    @Test
    void builderRequiresApiKey() {
        assertThatThrownBy(() -> GeminiEmbeddingClient.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsEmbeddingHttpExceptionOnNonSuccess() throws Exception {
        HttpServer errServer = HttpServer.create(new InetSocketAddress(0), 0);
        int errPort = errServer.getAddress().getPort();
        errServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(401, 0);
            exchange.close();
        });
        errServer.start();

        HttpClient redirecting = buildRedirectingClient(errPort);
        GeminiEmbeddingClient client = GeminiEmbeddingClient.builder()
                .apiKey("bad")
                .httpClient(redirecting)
                .build();

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(EmbeddingHttpException.class)
                .satisfies(ex -> assertThat(((EmbeddingHttpException) ex).statusCode()).isEqualTo(401));

        errServer.stop(0);
    }

    /**
     * Returns an HttpClient that rewrites any outbound request to target localhost:{port},
     * preserving path and query string.
     */
    private static HttpClient buildRedirectingClient(int targetPort) {
        return new RedirectingHttpClient(targetPort);
    }

    private static float withPrecision(float precision) {
        return precision;
    }

    /**
     * A minimal HttpClient decorator that rewrites requests to a local test server.
     * We can't subclass HttpClient easily, so we use a wrapper approach via a custom
     * proxy-style HttpClient that intercepts the URI.
     */
    static final class RedirectingHttpClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newHttpClient();
        private final int targetPort;

        RedirectingHttpClient(int targetPort) {
            this.targetPort = targetPort;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws java.io.IOException, InterruptedException {
            URI original = request.uri();
            URI rewritten = URI.create("http://localhost:" + targetPort + original.getRawPath()
                    + (original.getRawQuery() != null ? "?" + original.getRawQuery() : ""));
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
