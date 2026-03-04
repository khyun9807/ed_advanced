package com.stock_concurrency_project.repository;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisLockRepository {
    //직렬화 : 객체들의 데이터를 연속적인 데이터(스트림)로 변형하여 전송 가능한 형태로 만드는 것
    //역직렬화 : 직렬화된 데이터를 다시 객체의 형태로 만드는 것

    //레디스에 저장될 때는 바이너리로 저장될 수 있음.
    //커스텀 (역)직렬 serializer 설정 가능
    //value를 string뿐만 아니라 Hash, JSON등으로 직렬화 해야할 때 사용
    //템플릿 타입은 자바 코드에서 어떻게 다룰지 컴파일 타임에서 정해지는 것일뿐
    //실제로 직렬화되는 거는 설정한 serializer에 따라 달라진다.
    private final RedisTemplate<String,String> redisTemplate;

    //redis에 문자열로 저장됨. 즉 cli에서도 문자열로 조회가능
    //캐시 키/토큰/락 키/카운터 등 문자열 기반 작업이 대부분일 때 사용
    private final StringRedisTemplate stringRedisTemplate;

    //로직 실행 전 호출
    public boolean lock(Long lockKey) {
        return stringRedisTemplate
                .opsForValue()
                .setIfAbsent(generateKey(lockKey),"lock", Duration.ofSeconds(3));
    }

    //로직 실행 후 호출
    public boolean unlock(Long lockKey) {
        return stringRedisTemplate.delete(generateKey(lockKey));
    }

    private String generateKey(Long lockKey) {
        return lockKey.toString();
    }
}
