package demo.sse;

import demo.sse.adapter.SseSessions;
import demo.sse.application.EventService;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SseDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SseDemoApplication.class, args);
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledThreadPoolExecutor eventScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().name("sse-timer-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService eventWriter() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("sse-writer-", 0).factory());
    }

    @Bean
    SseSessions sseSessions(ScheduledThreadPoolExecutor eventScheduler, ExecutorService eventWriter) {
        return new SseSessions(eventScheduler, eventWriter, Clock.systemUTC());
    }

    @Bean
    EventService eventService(SseSessions sessions) {
        return new EventService(sessions, Clock.systemUTC());
    }
}
