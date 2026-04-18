package com.payper.server.post.redis.init;

import com.payper.server.post.redis.outbox_to_redis.PostLikeRedisFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class PostLikeRedisSeedInitializer implements ApplicationRunner {

    private final PostLikeRedisFacade postLikeRedisFacade;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 미리 레디스에 넣기
        postLikeRedisFacade.seedHot100FromDb();
    }
}
