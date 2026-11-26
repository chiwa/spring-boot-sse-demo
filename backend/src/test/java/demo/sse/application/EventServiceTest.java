package demo.sse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import demo.sse.application.EventService.Channel;
import demo.sse.application.EventService.Client;
import demo.sse.application.EventService.Event;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class EventServiceTest {
    @Test
    void routesNormalizedBroadcastWithoutSpring() {
        var delivery = new CapturingDelivery();
        var service = new EventService(delivery, Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));

        assertThat(service.publishBroadcast("  " + "a".repeat(500) + " ").toCompletableFuture().join()).isEqualTo(2);
        assertThat(delivery.channel).isEqualTo(Channel.BROADCAST);
        assertThat(delivery.event.message()).isEqualTo("a".repeat(500));
        assertThat(delivery.event.timestamp()).isEqualTo(Instant.EPOCH);
        assertThatThrownBy(() -> service.publishBroadcast(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publishBroadcast("a".repeat(501))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortsClientsWithEqualTimestampsById() {
        var first = new Client(new UUID(0, 1), Channel.TICK, 1, Instant.EPOCH);
        var second = new Client(new UUID(0, 2), Channel.BROADCAST, null, Instant.EPOCH);
        var delivery = new CapturingDelivery();
        delivery.clients = List.of(second, first);

        assertThat(new EventService(delivery, Clock.systemUTC()).connectedClients()).containsExactly(first, second);
    }

    private static final class CapturingDelivery implements EventService.Delivery {
        private Channel channel;
        private Event event;
        private List<Client> clients = List.of();

        @Override
        public CompletableFuture<Integer> publish(Channel channel, Event event) {
            this.channel = channel;
            this.event = event;
            return CompletableFuture.completedFuture(2);
        }

        @Override
        public List<Client> clients() {
            return clients;
        }
    }
}
