package demo.sse;

import demo.sse.adapter.SseSessions;
import demo.sse.application.EventService;
import demo.sse.application.EventService.Client;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventController {
    private final EventService events;
    private final SseSessions sessions;

    public EventController(EventService events, SseSessions sessions) {
        this.events = events;
        this.sessions = sessions;
    }

    @GetMapping(path = "/api/clients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ClientResponse>> connectedClients() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(events.connectedClients().stream().map(ClientResponse::from).toList());
    }

    @PostMapping(path = "/api/broadcast", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<BroadcastResult> broadcast(@RequestBody BroadcastRequest request) {
        return events.publishBroadcast(request.message()).thenApply(BroadcastResult::new);
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeTicks(
            @RequestParam(name = "intervalSeconds", defaultValue = "1") int intervalSeconds) throws IOException {
        if (intervalSeconds < 1) throw new IllegalArgumentException("intervalSeconds must be positive");
        return stream(sessions.openTicks(intervalSeconds, events::nextTick));
    }

    @GetMapping(path = "/api/broadcast/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeBroadcast() throws IOException {
        return stream(sessions.openBroadcast());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Void> invalidInput() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(SseSessions.NotRunningException.class)
    public ResponseEntity<Void> sessionsUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private ResponseEntity<SseEmitter> stream(SseEmitter emitter) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no").body(emitter);
    }

    public record ClientResponse(UUID id, String channel, Integer intervalSeconds, Instant connectedAt) {
        static ClientResponse from(Client client) {
            return new ClientResponse(client.id(), client.channel().eventName(), client.intervalSeconds(), client.connectedAt());
        }
    }

    public record BroadcastRequest(String message) {}

    public record BroadcastResult(int sent) {}
}
