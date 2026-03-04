package com.coupon_fcfs_project.consumer.consumer;

import com.coupon_fcfs_project.consumer.domain.Coupon;
import com.coupon_fcfs_project.consumer.domain.FailedCouponEvent;
import com.coupon_fcfs_project.consumer.repository.CouponRepository;
import com.coupon_fcfs_project.consumer.repository.FailedCouponEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponCreatedConsumer {
    private final CouponRepository couponRepository;
    private final FailedCouponEventRepository failedCouponEventRepository;

    @KafkaListener(
            topics = "coupon_create",
            groupId = "group_1"
    )
    public void listener(Long userId) {
        //System.out.println(userId);
        try{
            couponRepository.save(Coupon.create(userId));

        }catch (Exception e){
            log.error("failed to create coupon userID={}", userId, e);
            failedCouponEventRepository.save(FailedCouponEvent.create(userId));
        }
    }
}
