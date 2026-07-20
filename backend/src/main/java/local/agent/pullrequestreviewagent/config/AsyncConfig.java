package local.agent.pullrequestreviewagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Runs each review off the request thread so the controller can return a streaming
     * {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter} immediately
     * and push progress events as the review, including model tool calls, plays out.
     */
    @Bean
    public Executor reviewExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
