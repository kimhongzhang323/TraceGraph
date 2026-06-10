package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Shared blocking JSON request/response cycle for LLM HTTP adapters: serialize, send,
 * surface non-2xx as {@link LlmHttpException}, parse the body as a JSON tree.
 */
final class JsonHttp {

    private JsonHttp() {}

    static byte[] writeBody(ObjectMapper mapper, Object body, String provider) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize " + provider + " request", e);
        }
    }

    static JsonNode sendForJson(HttpClient client, HttpRequest request, ObjectMapper mapper,
                                String provider) {
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(provider + " request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(provider + " request interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(),
                    new String(response.body(), StandardCharsets.UTF_8));
        }
        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + provider + " response", e);
        }
    }
}
