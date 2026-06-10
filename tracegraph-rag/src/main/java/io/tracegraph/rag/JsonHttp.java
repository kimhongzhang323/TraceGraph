package io.tracegraph.rag;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Shared blocking JSON request/response cycle for vector-store and embedding HTTP adapters.
 * The {@link Errors} strategy maps transport and status failures onto the module's public
 * exception types ({@link VectorStoreHttpException}, {@link EmbeddingHttpException}).
 */
final class JsonHttp {

    interface Errors {
        RuntimeException httpStatus(int statusCode, String body);

        RuntimeException failure(String message, Throwable cause);
    }

    static final Errors VECTOR_STORE = new Errors() {
        @Override
        public RuntimeException httpStatus(int statusCode, String body) {
            return new VectorStoreHttpException(statusCode, body);
        }

        @Override
        public RuntimeException failure(String message, Throwable cause) {
            return new VectorStoreHttpException(message, cause);
        }
    };

    static final Errors EMBEDDING = new Errors() {
        @Override
        public RuntimeException httpStatus(int statusCode, String body) {
            return new EmbeddingHttpException(statusCode, body);
        }

        @Override
        public RuntimeException failure(String message, Throwable cause) {
            return new EmbeddingHttpException(message, cause);
        }
    };

    private JsonHttp() {}

    static String send(HttpClient client, HttpRequest request, Errors errors) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw errors.failure("Request interrupted", e);
        } catch (Exception e) {
            throw errors.failure("HTTP request failed", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw errors.httpStatus(response.statusCode(), response.body());
        }
        return response.body();
    }

    static String serialize(ObjectMapper mapper, Object value, Errors errors) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw errors.failure("Failed to serialize request body", e);
        }
    }

    /**
     * Rejects values that could escape a URL path segment or a GraphQL/JSON context they are
     * interpolated into (collection names, class names). Letters, digits, {@code _ - .} only.
     */
    static String requireSafeIdentifier(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            if (!ok) {
                throw new IllegalArgumentException(
                        name + " contains unsupported character '" + c + "'");
            }
        }
        return value;
    }
}
