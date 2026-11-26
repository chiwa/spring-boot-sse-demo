package demo.sse;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventController {
    private static final List<String> MESSAGES = List.of(
            "I ate an apple.",
            "I ate a banana.",
            "I ate an orange.",
            "I ate a mango.",
            "I ate some pad thai.",
            "I ate a bowl of ramen.",
            "I ate a slice of pizza.",
            "I ate some mango sticky rice."
    );

    private final ScheduledExecutorService scheduler;
    private final Map<UUID, Connection> clients = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public EventController(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping(path = "/api/clients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> connectedClients() {
        var snapshot = clients.values().stream()
                .map(Connection::client)
                .sorted(Comparator.comparing(Client::connectedAt))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot);
    }

    @PostMapping(path = "/api/broadcast", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BroadcastResult broadcast(@RequestBody BroadcastRequest request) {
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isEmpty() || message.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must contain 1–500 characters");
        }
        Tick event = new Tick(sequence.incrementAndGet(), Instant.now().toString(), message);
        int sent = 0;
        for (Connection connection : List.copyOf(clients.values())) {
            try {
                sendEvent(connection.emitter(), "broadcast", event);
                sent++;
            } catch (IOException | IllegalStateException disconnected) {
                clients.remove(connection.client().id());
            }
        }
        return new BroadcastResult(sent);
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @RequestParam(name = "intervalSeconds", defaultValue = "1") int intervalSeconds) throws IOException {
        if (intervalSeconds < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "intervalSeconds must be positive");
        }

        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().comment("stream opened").reconnectTime(3000));

        Client client = new Client(UUID.randomUUID(), intervalSeconds, Instant.now());
        clients.put(client.id(), new Connection(client, emitter));
        var elapsedSeconds = new AtomicLong();
        var task = scheduler.scheduleAtFixedRate(() -> {
            if (!clients.containsKey(client.id())) {
                throw new IllegalStateException("SSE client disconnected");
            }
            long elapsed = elapsedSeconds.incrementAndGet();
            try {
                if (elapsed % intervalSeconds == 0) {
                    sendTick(emitter);
                } else if (elapsed % 5 == 0) {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                }
            } catch (IOException | IllegalStateException disconnected) {
                clients.remove(client.id());
                // An exception stops the periodic task. The servlet container handles I/O errors.
                throw new IllegalStateException("SSE client disconnected", disconnected);
            }
        }, 1, 1, TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            clients.remove(client.id());
            task.cancel(false);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private void sendTick(SseEmitter emitter) throws IOException {
        String message = MESSAGES.get(ThreadLocalRandom.current().nextInt(MESSAGES.size()));
        Tick tick = new Tick(sequence.incrementAndGet(), Instant.now().toString(), message);

        sendEvent(emitter, "tick", tick);
    }

    private void sendEvent(SseEmitter emitter, String name, Tick tick) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(tick.id()))
                .name(name)
                .data(tick, MediaType.APPLICATION_JSON));
    }

    public record Client(UUID id, int intervalSeconds, Instant connectedAt) {}

    private record Connection(Client client, SseEmitter emitter) {}

    public record BroadcastRequest(String message) {}

    public record BroadcastResult(int sent) {}

    public record Tick(long id, String timestamp, String message) {}
}
