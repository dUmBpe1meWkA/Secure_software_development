package mtuci.task2;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Task2Application {
    public static void main(String[] args) {
        SpringApplication.run(Task2Application.class, args);
    }

    @Bean
    public Clock serverClock() {
        return Clock.tick(Clock.systemUTC(), java.time.Duration.ofNanos(1000));
    }
}
