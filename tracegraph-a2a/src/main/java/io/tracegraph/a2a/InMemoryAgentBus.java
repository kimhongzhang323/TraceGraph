package io.tracegraph.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * In-process {@link AgentBus} that delivers messages via virtual threads.
 * Thread-safe; all methods may be called concurrently.
 */
public final class InMemoryAgentBus implements AgentBus {

    private final Map<String, List<Consumer<AgentMessage>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();

    @Override
    public void registerCard(AgentCard card) {
        cards.put(card.id(), card);
    }

    @Override
    public List<AgentCard> agentCards() {
        return cards.values().stream()
                .sorted(java.util.Comparator.comparing(AgentCard::id))
                .toList();
    }

    @Override
    public void send(AgentMessage message) {
        List<Consumer<AgentMessage>> handlers =
                subscribers.getOrDefault(message.toId(), List.of());
        for (Consumer<AgentMessage> handler : handlers) {
            Thread.ofVirtual().start(() -> handler.accept(message));
        }
    }

    @Override
    public Runnable subscribe(String agentId, Consumer<AgentMessage> handler) {
        subscribers.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> {
            List<Consumer<AgentMessage>> list = subscribers.get(agentId);
            if (list != null) list.remove(handler);
        };
    }

    @Override
    public AgentMessage sendAndAwait(AgentMessage message, long timeoutMs)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AgentMessage> reply = new AtomicReference<>();
        Runnable cancel = subscribe(message.fromId(), incoming -> {
            if (message.id().equals(incoming.correlationId())) {
                reply.set(incoming);
                latch.countDown();
            }
        });
        send(message);
        try {
            boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!received) {
                throw new AgentTimeoutException(
                        "No reply for message " + message.id() + " within " + timeoutMs + "ms");
            }
        } finally {
            cancel.run();
        }
        return reply.get();
    }
}
