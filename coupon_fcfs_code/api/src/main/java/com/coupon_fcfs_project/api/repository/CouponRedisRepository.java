package com.coupon_fcfs_project.api.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    public Long increment(){
        return stringRedisTemplate.opsForValue().increment("coupon_count");
    }

    public void clear(){
        stringRedisTemplate.delete("coupon_count");
        stringRedisTemplate.delete("coupon_applied_user");
    }

    public Long addUser(Long userId){
        return stringRedisTemplate.opsForSet().add("coupon_applied_user",userId.toString());
    }
}
