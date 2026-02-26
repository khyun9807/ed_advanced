package com.community_code.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching // Spring Boot의 캐싱 설정을 활성화
public class RedisCacheConfig {
    @Bean
    public CacheManager postsCacheManager(
            RedisConnectionFactory redisConnectionFactory
    ) {
        // ✅ 타입정보 포함(JSON) 직렬화기 (Page 같은 타입에 유리)
        GenericJacksonJsonRedisSerializer valueSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        // 가장 간단: 내부망/신뢰 가능한 Redis라는 전제에서 많이 씀
                        .enableUnsafeDefaultTyping()
                        .build();

        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration
                .defaultCacheConfig()
// Redis에 Key를 저장할 때 String으로 직렬화(변환)해서 저장
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                RedisSerializer.string()
                        )
                )
// Redis에 Value를 저장할 때 Json으로 직렬화(변환)해서 저장
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                valueSerializer
                        )
                )
// 데이터의 만료기간(TTL) 설정 중요!!!!!
                .entryTtl(Duration.ofMinutes(1L));
        return RedisCacheManager
                .RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}