package io.tracegraph.a2a.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.a2a.AgentMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends {@link AgentMessage} instances to a remote A2A endpoint over HTTP.
 * Uses the JDK {@link HttpClient}; Jackson is required at runtime.
 */
public final class A2AHttpClient {

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public A2AHttpClient(String baseUrl) {
        this(baseUrl, HttpClient.newHttpClient());
    }

    public A2AHttpClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.mapper = new ObjectMapper();
    }

    public void send(AgentMessage message) {
        A2AMessage wire = A2AMessage.from(message);
        try {
            String body = mapper.writeValueAsString(wire);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/a2a/messages"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new A2AHttpException(response.statusCode(), response.body());
            }
        } catch (A2AHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AHttpException(0, e.getMessage(), e);
        }
    }
}
