package io.tracegraph.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.OpenAiLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class WireMockLlmIT {

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {"role": "assistant", "content": "Hello from mock!"},
                                    "finish_reason": "stop"
                                  }],
                                  "usage": {"prompt_tokens": 10, "completion_tokens": 5}
                                }
                                """)));
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void openAiClientCallsWireMockedEndpoint() {
        OpenAiLlmClient client = OpenAiLlmClient.builder()
                .apiKey("test-key")
                .endpoint(URI.create("http://localhost:" + wireMock.port() + "/v1/chat/completions"))
                .build();

        LlmRequest request = LlmRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.user("Say hello")))
                .build();

        LlmResponse response = client.complete(request);

        assertThat(response.content()).isEqualTo("Hello from mock!");
        assertThat(response.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
    }
}
