package io.tracegraph.connectors.guardrail;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.core.spi.Guardrail;
import io.tracegraph.core.spi.GuardrailVerdict;

import java.util.List;
import java.util.ListIterator;

/**
 * Adapts a {@code Guardrail<String>} to check the last user message in an {@link LlmRequest}.
 *
 * <p>If the request has no user message, the verdict is {@code ALLOW} (nothing to block).
 */
public final class LlmRequestGuardrail implements Guardrail<LlmRequest> {

    private final Guardrail<String> textGuardrail;

    public LlmRequestGuardrail(Guardrail<String> textGuardrail) {
        this.textGuardrail = textGuardrail;
    }

    @Override
    public GuardrailVerdict check(LlmRequest request) {
        if (request == null) {
            return GuardrailVerdict.allow();
        }
        List<ChatMessage> messages = request.messages();
        // Walk backwards to find the last user message
        ListIterator<ChatMessage> it = messages.listIterator(messages.size());
        while (it.hasPrevious()) {
            ChatMessage msg = it.previous();
            if (msg.role() == ChatMessage.Role.USER) {
                return textGuardrail.check(msg.content());
            }
        }
        return GuardrailVerdict.allow();
    }
}
