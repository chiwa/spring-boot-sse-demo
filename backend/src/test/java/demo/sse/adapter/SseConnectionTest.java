package demo.sse.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import demo.sse.application.EventService.Channel;
import demo.sse.application.EventService.Client;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionTest {
    @Test
    void slowStreamDoesNotBlockOtherStreams() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var slow = new SseEmitter() {
            @Override
            public void send(SseEventBuilder event) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        try (var writer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var slowSession = new SseConnection(client(), slow, writer, () -> {});
                var fastSession = new SseConnection(client(), new SseEmitter(), writer, () -> {});
                var slowWrite = slowSession.enqueue(SseEmitter.event().comment("slow"), true);
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(slowSession.enqueue(SseEmitter.event().comment("skip"), true).join()).isFalse();
                assertThat(fastSession.enqueue(SseEmitter.event().comment("fast"), true).get(2, TimeUnit.SECONDS)).isTrue();
                assertThat(slowWrite.isDone()).isFalse();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void capsPendingBroadcastWritesAndDrainsOnlyTheAcceptedFrames() {
        var tasks = new java.util.ArrayDeque<Runnable>();
        var session = new SseConnection(client(), new SseEmitter(), tasks::add, () -> {});
        var accepted = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
        for (int index = 0; index < 8; index++) {
            accepted.add(session.enqueue(SseEmitter.event().comment("accepted"), false));
        }

        assertThat(session.enqueue(SseEmitter.event().comment("rejected"), false).join()).isFalse();
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(accepted).allMatch(result -> result.join());
    }

    @Test
    void writeFailureUnregistersAndImmediatelyCancelsScheduledWork() throws Exception {
        var emitter = new SseEmitter() {
            @Override
            public void send(SseEventBuilder event) throws IOException {
                throw new IOException("disconnected");
            }
        };
        var timer = new ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        var unregister = new AtomicBoolean();
        try (var writer = Executors.newVirtualThreadPerTaskExecutor()) {
            var schedule = timer.schedule(() -> {}, 1, TimeUnit.HOURS);
            var session = new SseConnection(client(), emitter, writer, () -> unregister.set(true));
            session.setSchedule(schedule);
            assertThat(session.enqueue(SseEmitter.event().comment("failed"), false)
                    .get(2, TimeUnit.SECONDS)).isFalse();
            assertThat(unregister).isTrue();
            assertThat(schedule.isCancelled()).isTrue();
            assertThat(timer.getQueue()).isEmpty();
        } finally {
            timer.shutdownNow();
        }
    }

    private Client client() {
        return new Client(UUID.randomUUID(), Channel.BROADCAST, null, Instant.EPOCH);
    }
}
