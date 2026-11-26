package demo.sse.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class EventService {
    private static final List<String> FOOD_MESSAGES = List.of(
            "I ate an apple.", "I ate a banana.", "I ate an orange.", "I ate a mango.",
            "I ate some pad thai.", "I ate a bowl of ramen.", "I ate a slice of pizza.",
            "I ate some mango sticky rice."
    );

    private final Delivery delivery;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public EventService(Delivery delivery, Clock clock) {
        this.delivery = delivery;
        this.clock = clock;
    }

    public CompletionStage<Integer> publishBroadcast(String input) {
        String message = input == null ? "" : input.strip();
        if (message.isEmpty() || message.length() > 500) {
            throw new IllegalArgumentException("message must contain 1–500 characters");
        }
        return delivery.publish(Channel.BROADCAST, event(message));
    }

    public List<Client> connectedClients() {
        return delivery.clients().stream()
                .sorted(Comparator.comparing(Client::connectedAt).thenComparing(Client::id))
                .toList();
    }

    public Event nextTick() {
        return event(FOOD_MESSAGES.get(ThreadLocalRandom.current().nextInt(FOOD_MESSAGES.size())));
    }

    private Event event(String message) {
        return new Event(sequence.incrementAndGet(), clock.instant(), message);
    }

    public enum Channel {
        TICK("tick"), BROADCAST("broadcast");

        private final String eventName;

        Channel(String eventName) {
            this.eventName = eventName;
        }

        public String eventName() {
            return eventName;
        }
    }

    public interface Delivery {
        CompletionStage<Integer> publish(Channel channel, Event event);

        List<Client> clients();
    }

    public record Event(long id, Instant timestamp, String message) {}

    public record Client(UUID id, Channel channel, Integer intervalSeconds, Instant connectedAt) {}
}
