package io.tracegraph.a2a;

import java.util.function.Consumer;

/**
 * SPI for message routing between agents.
 */
public interface AgentBus {

    /** Send a message to its {@code toId} recipient. Fire-and-forget. */
    void send(AgentMessage message);

    /**
     * Subscribe a handler for messages addressed to {@code agentId}.
     *
     * @return a {@link Runnable} that, when invoked, cancels the subscription
     */
    Runnable subscribe(String agentId, Consumer<AgentMessage> handler);

    /**
     * Send a message and block until a reply whose {@code correlationId} matches
     * {@code message.id()} is received, or until {@code timeoutMs} elapses.
     *
     * @throws AgentTimeoutException if no reply arrives within the timeout
     * @throws InterruptedException  if the calling thread is interrupted while waiting
     */
    default AgentMessage sendAndAwait(AgentMessage message, long timeoutMs)
            throws InterruptedException {
        throw new UnsupportedOperationException("sendAndAwait not supported by this bus");
    }

    /**
     * Registers (or replaces, by {@link AgentCard#id()}) a capability descriptor for discovery.
     * Default is a no-op so existing buses keep compiling; {@code InMemoryAgentBus} stores cards.
     *
     * @param card the agent's capability descriptor
     */
    default void registerCard(AgentCard card) {}

    /**
     * Capability descriptors of the agents registered on this bus, ordered by id.
     * Default is empty for buses without discovery support.
     *
     * @return registered agent cards
     */
    default java.util.List<AgentCard> agentCards() {
        return java.util.List.of();
    }
}
