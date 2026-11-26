package demo.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.sse.adapter.SseSessions;
import demo.sse.application.EventService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SseSessions sessions;

    @Test
    void validatesIntervalsWithEventSourceAcceptHeader() throws Exception {
        for (String value : new String[]{"0", "-1", "1.5", "2147483648"}) {
            mvc.perform(get("/api/events").param("intervalSeconds", value).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isBadRequest()).andExpect(content().string(""));
        }
    }

    @Test
    void broadcastsOnlyToBroadcastSessionsAndRemovesClosedSessions() throws Exception {
        var timed = mvc.perform(get("/api/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
        var broadcast = mvc.perform(get("/api/broadcast/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            var result = mvc.perform(post("/api/broadcast").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\"}"))
                    .andExpect(request().asyncStarted()).andReturn();
            mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
            assertThat(broadcast.getResponse().getContentAsString()).contains("event:broadcast", "\"message\":\"Hello\"");
            assertThat(timed.getResponse().getContentAsString()).doesNotContain("event:broadcast");

            var clients = mvc.perform(get("/api/clients")).andExpect(status().isOk()).andReturn();
            assertThat(clients.getResponse().getContentAsString()).contains("\"channel\":\"tick\"", "\"channel\":\"broadcast\"");

            broadcast.getRequest().getAsyncContext().complete();
            var second = mvc.perform(post("/api/broadcast").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"Hello again\"}")).andReturn();
            mvc.perform(asyncDispatch(second)).andExpect(jsonPath("$.sent").value(0));
        } finally {
            timed.getRequest().getAsyncContext().complete();
            if (broadcast.getRequest().isAsyncStarted()) broadcast.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void shutdownCompletesOpenStreamsAndCancelsTimers() throws Exception {
        var timer = new ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        try (var writer = Executors.newVirtualThreadPerTaskExecutor()) {
            var clock = Clock.systemUTC();
            var sessions = new SseSessions(timer, writer, clock);
            sessions.start();
            var isolatedMvc = MockMvcBuilders.standaloneSetup(
                    new EventController(new EventService(sessions, clock), sessions)).build();
            var timed = isolatedMvc.perform(get("/api/events")).andReturn();
            var broadcast = isolatedMvc.perform(get("/api/broadcast/events")).andReturn();

            sessions.stop();

            timed.getAsyncResult(2000);
            broadcast.getAsyncResult(2000);
            assertThat(sessions.clients()).isEmpty();
            assertThat(timer.getQueue()).isEmpty();
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void blankIntervalDefaultsToOneSecondAndEmitsATick() throws Exception {
        assertThat(sessions.clients()).isEmpty();
        var timed = mvc.perform(get("/api/events").param("intervalSeconds", "").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        try {
            mvc.perform(get("/api/clients")).andExpect(jsonPath("$[0].intervalSeconds").value(1));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(timed.getResponse().getContentAsString()).contains("event:tick", "\"message\":\"I ate "));
        } finally {
            timed.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void stoppedSessionsReturnA503WithEventSourceAcceptHeader() throws Exception {
        var timer = new ScheduledThreadPoolExecutor(1);
        try {
            var clock = Clock.systemUTC();
            var sessions = new SseSessions(timer, Runnable::run, clock);
            var isolatedMvc = MockMvcBuilders.standaloneSetup(
                    new EventController(new EventService(sessions, clock), sessions)).build();
            for (String endpoint : new String[]{"/api/events", "/api/broadcast/events"}) {
                isolatedMvc.perform(get(endpoint).accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
            }
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void stalledBroadcastTimesOutAndClosesItsSession() throws Exception {
        var timer = new ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        try {
            var clock = Clock.systemUTC();
            // Accept writer tasks without running them to simulate a stalled dispatch.
            var sessions = new SseSessions(timer, task -> {}, clock);
            sessions.start();
            var isolatedMvc = MockMvcBuilders.standaloneSetup(
                    new EventController(new EventService(sessions, clock), sessions)).build();
            var stream = isolatedMvc.perform(get("/api/broadcast/events")).andReturn();
            var send = isolatedMvc.perform(post("/api/broadcast").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"Hello\"}")).andReturn();

            send.getAsyncResult(5000);
            isolatedMvc.perform(asyncDispatch(send)).andExpect(jsonPath("$.sent").value(0));
            stream.getAsyncResult(2000);
            assertThat(sessions.clients()).isEmpty();
            assertThat(timer.getQueue()).isEmpty();
            assertThat(stream.getResponse().getContentAsString()).doesNotContain("event:broadcast");
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void validatesBroadcastMessageLength() throws Exception {
        mvc.perform(post("/api/broadcast").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        for (String message : new String[]{"   ", "a".repeat(501)}) {
            mvc.perform(post("/api/broadcast").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new EventController.BroadcastRequest(message))))
                    .andExpect(status().isBadRequest());
        }
    }
}
