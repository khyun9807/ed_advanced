package com.payper.server.post.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PostLikeWorkerExecutorConfig {

    // 메인이 아닌 다른 쓰레드에 워커에게 일을 시키기 위함
    //@Bean
    public ThreadPoolTaskExecutor postLikeOutboxWorkerStreamExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1); // 워커 1개(필요하면 늘림)
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(0); // 대기열 없이 바로 실행
        ex.setThreadNamePrefix("post-like-stream-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        return ex;
    }

    @Bean
    public ThreadPoolTaskExecutor postLikeOutboxWorkerPollingExecutor(){
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        //ex.setCorePoolSize(1);
        //ex.setMaxPoolSize(1);
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("post-like-polling-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        return ex;
    }
}
