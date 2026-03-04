package com.coupon_fcfs_project.api.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {
    /**
     * 토픽 생성/관리용 AdminClient 설정
     * (도커 컴포즈에서 호스트 접근은 localhost:9092 로 하게 되어 있으니 그대로 사용)
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        return new KafkaAdmin(config);
    }

    /**
     * 앱 시작 시 생성될 토픽 정의
     */
    @Bean
    public NewTopic couponCreateTopic() {
        return TopicBuilder.name("coupon_create")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
