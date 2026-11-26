package demo.sse;

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
        var scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
