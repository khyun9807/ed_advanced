package com.payper.server.post.redis_fallback;

import com.payper.server.global.exception.ApiException;
import com.payper.server.post.redis_v3.PostLikeRedisSeedV3;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class PostLikeCircuitBreakerConfig {
    //private final PostLikeRedisSeedV3 postLikeRedisSeedV3;

    @Bean
    public CircuitBreaker postLikeCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // 슬라이딩 윈도우: 최근 10회 호출 기준
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                // 실패율 50% 이상이면 OPEN
                .failureRateThreshold(50)
                // OPEN 상태 30초 유지 후 HALF_OPEN 전환
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // HALF_OPEN에서 3회 시험 호출
                .permittedNumberOfCallsInHalfOpenState(3)
                // CLOSED 진입 시 최소 5회 호출 후 실패율 계산 시작
                .minimumNumberOfCalls(5)
                // Redis 관련 예외만 실패로 기록
                .recordExceptions(
                        RedisConnectionFailureException.class,
                        RedisSystemException.class,
                        QueryTimeoutException.class)
                // 비즈니스 예외는 무시 (circuit breaker 상태에 영향 X)
                .ignoreExceptions(ApiException.class)
                .build();

        CircuitBreaker cb = CircuitBreaker.of("postLikeRedis", config);

        // 상태 전환 로그 + Redis 복구 시 시딩
        cb.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("[CircuitBreaker] 상태 변경: {}", event.getStateTransition());

                    // HALF_OPEN → CLOSED 복귀 시 DB → Redis 시딩
                    if (event.getStateTransition()
                            == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                        log.info("[CircuitBreaker] Redis 복구 감지 → Hot100 시딩 시작");
                        //postLikeRedisSeedV3.seedRanking();
                    }
                });

        return cb;
    }
}
