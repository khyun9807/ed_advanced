package com.coupon_fcfs_project.api.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
//쿠폰의 소유주를 전송할 프로듀서
public class CouponCreateProducer {
    private final KafkaTemplate<String,Long> kafkaTemplate;

    public void createMessage(Long userId){
        //토픽에 userId 전송
        kafkaTemplate.send("coupon_create",null,userId);
    }
}
