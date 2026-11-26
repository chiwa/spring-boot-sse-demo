package demo.sse.adapter;

import demo.sse.application.EventService.Client;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseConnection {
    private static final Logger LOG = LoggerFactory.getLogger(SseConnection.class);
    private static final ThreadFactory CLOSER = Thread.ofVirtual().name("sse-close-", 0).factory();
    private static final int MAX_PENDING_WRITES = 8;

    private final Client client;
    private final SseEmitter emitter;
    private final Executor writer;
    private final Runnable unregister;
    private final ArrayDeque<Write> pending = new ArrayDeque<>();
    private Write active;
    private ScheduledFuture<?> schedule;
    private boolean draining;
    private boolean closed;

    SseConnection(Client client, SseEmitter emitter, Executor writer, Runnable unregister) {
        this.client = client;
        this.emitter = emitter;
        this.writer = writer;
        this.unregister = unregister;
    }

    Client client() {
        return client;
    }

    synchronized void setSchedule(ScheduledFuture<?> schedule) {
        this.schedule = schedule;
        if (closed) {
            schedule.cancel(false);
        }
    }

    synchronized CompletableFuture<Boolean> enqueue(SseEmitter.SseEventBuilder event, boolean periodic) {
        if (closed || pending.size() >= MAX_PENDING_WRITES || (periodic && draining)) {
            return CompletableFuture.completedFuture(false);
        }
        var write = new Write(event, new CompletableFuture<>());
        pending.addLast(write);
        if (!draining) {
            draining = true;
            try {
                writer.execute(this::drain);
            } catch (RejectedExecutionException stopped) {
                close(true);
            }
        }
        return write.result();
    }

    private void drain() {
        while (true) {
            Write write;
            synchronized (this) {
                write = pending.pollFirst();
                active = write;
                if (write == null) {
                    draining = false;
                    return;
                }
            }
            try {
                emitter.send(write.event());
                write.result().complete(true);
            } catch (IOException disconnected) {
                // The servlet container completes the response after an I/O error.
                close(false);
            } catch (RuntimeException disconnected) {
                close(true);
            }
        }
    }

    void close(boolean completeEmitter) {
        synchronized (this) {
            if (closed) return;
            closed = true;
            unregister.run();
            if (schedule != null) schedule.cancel(false);
            if (active != null) active.result().complete(false);
            pending.forEach(write -> write.result().complete(false));
            pending.clear();
        }
        if (completeEmitter) {
            // The emitter can still be holding its write lock during a socket write.
            CLOSER.newThread(() -> {
                try {
                    emitter.complete();
                } catch (RuntimeException failure) {
                    LOG.warn("Failed to complete SSE session {}", client.id(), failure);
                }
            }).start();
        }
    }

    private record Write(SseEmitter.SseEventBuilder event, CompletableFuture<Boolean> result) {}
}
