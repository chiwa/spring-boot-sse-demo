package demo.sse.adapter;

import demo.sse.application.EventService;
import demo.sse.application.EventService.Channel;
import demo.sse.application.EventService.Client;
import demo.sse.application.EventService.Event;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class SseSessions implements EventService.Delivery, SmartLifecycle {
    private final ScheduledExecutorService scheduler;
    private final Executor writer;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, SseConnection> sessions = new ConcurrentHashMap<>();
    private volatile boolean running;

    public SseSessions(ScheduledExecutorService scheduler, Executor writer, Clock clock) {
        this.scheduler = scheduler;
        this.writer = writer;
        this.clock = clock;
    }

    public synchronized SseEmitter openTicks(int intervalSeconds, Supplier<Event> nextTick) throws IOException {
        var elapsed = new AtomicLong();
        return open(Channel.TICK, intervalSeconds, 1, () -> {
            long seconds = elapsed.incrementAndGet();
            if (seconds % intervalSeconds == 0) return event(Channel.TICK, nextTick.get());
            return seconds % 5 == 0 ? heartbeat() : null;
        });
    }

    public synchronized SseEmitter openBroadcast() throws IOException {
        return open(Channel.BROADCAST, null, 5, SseSessions::heartbeat);
    }

    private SseEmitter open(Channel channel, Integer intervalSeconds, int period, Supplier<SseEmitter.SseEventBuilder> next)
            throws IOException {
        if (!running) throw new NotRunningException();

        var emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().comment("stream opened").reconnectTime(3000));
        var client = new Client(UUID.randomUUID(), channel, intervalSeconds, clock.instant());
        var session = new SseConnection(client, emitter, writer, () -> sessions.remove(client.id()));
        emitter.onCompletion(() -> session.close(false));
        emitter.onTimeout(() -> session.close(true));
        emitter.onError(error -> session.close(false));
        sessions.put(client.id(), session);
        try {
            session.setSchedule(scheduler.scheduleAtFixedRate(() -> {
                var frame = next.get();
                if (frame != null) session.enqueue(frame, true);
            }, period, period, TimeUnit.SECONDS));
        } catch (RuntimeException schedulingFailure) {
            session.close(true);
            throw schedulingFailure;
        }
        return emitter;
    }

    @Override
    public CompletableFuture<Integer> publish(Channel channel, Event message) {
        var writes = sessions.values().stream()
                .filter(session -> session.client().channel() == channel)
                .map(session -> session.enqueue(event(channel, message), false)
                        .orTimeout(3, TimeUnit.SECONDS)
                        .exceptionally(timeout -> {
                            session.close(true);
                            return false;
                        }))
                .toList();
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> (int) writes.stream().filter(CompletableFuture::join).count());
    }

    @Override
    public List<Client> clients() {
        return sessions.values().stream().map(SseConnection::client).toList();
    }

    private static SseEmitter.SseEventBuilder event(Channel channel, Event message) {
        return SseEmitter.event().name(channel.eventName()).id(Long.toString(message.id()))
                .data(new EventPayload(message.id(), message.timestamp().toString(), message.message()), MediaType.APPLICATION_JSON);
    }

    private record EventPayload(long id, String timestamp, String message) {}

    public static final class NotRunningException extends IllegalStateException {
        private NotRunningException() {
            super("SSE sessions are not running");
        }
    }

    private static SseEmitter.SseEventBuilder heartbeat() {
        return SseEmitter.event().comment("keep-alive");
    }

    @Override
    public synchronized void start() {
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        List.copyOf(sessions.values()).forEach(session -> session.close(true));
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
