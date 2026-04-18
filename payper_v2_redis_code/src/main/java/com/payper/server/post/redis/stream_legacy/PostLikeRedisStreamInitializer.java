package com.payper.server.post.redis.stream_legacy;

import com.payper.server.post.redis.PostLikeRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

// @Component
@RequiredArgsConstructor
@Slf4j
public class PostLikeRedisStreamInitializer { // implements ApplicationRunner {
    private final StringRedisTemplate stringRedisTemplate;

    // 아웃박스 -> 레디스 동작을 해줄 워커가 사용할
    // Stream(메시지 공간)과 Consumer Group(받는 그룹)을
    // 먼저 지정해야 한다!
    // @Override
    public void run(ApplicationArguments args) throws Exception {

        try {
            // 스트림이름, 읽기 시작해야하는 오프셋, 그룹이름
            stringRedisTemplate
                    .opsForStream()
                    .createGroup(
                            PostLikeRedisKeys.STREAM,
                            // 지금 이후부터 들어오는
                            // 메시지부터 소비
                            ReadOffset.latest(),
                            PostLikeRedisKeys.GROUP);
        } catch (Exception e) { // 이미 그룹이 존재할 경우 예외 가능
            log.debug("Error creating group. maybe already exists", e);
        }
    }
}
