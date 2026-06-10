package io.tracegraph.boot.a2a;

import io.tracegraph.a2a.AgentBus;
import io.tracegraph.a2a.AgentMessage;
import io.tracegraph.a2a.http.A2AMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives inbound A2A messages over HTTP and delivers them to the local {@link AgentBus}.
 * Registered by {@code A2AAutoConfiguration} behind an API-key filter; requests are
 * rejected until {@code tracegraph.a2a.api-key} is configured.
 */
@RestController
public class A2AController {

    private final AgentBus bus;

    public A2AController(AgentBus bus) {
        this.bus = bus;
    }

    @PostMapping("/a2a/messages")
    public ResponseEntity<Void> receiveMessage(
            @RequestBody A2AMessage wire,
            @RequestHeader(value = "X-A2A-From", required = false) String fromId,
            @RequestHeader(value = "X-A2A-To", required = false) String toId) {
        AgentMessage msg = wire.toAgentMessage(
                fromId != null ? fromId : "unknown",
                toId != null ? toId : "local");
        bus.send(msg);
        return ResponseEntity.accepted().build();
    }
}
