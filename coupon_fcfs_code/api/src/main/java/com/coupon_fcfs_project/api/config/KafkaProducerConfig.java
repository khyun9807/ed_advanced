package com.coupon_fcfs_project.api.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String,Long> producerFactory() {
        //producer 설정
        HashMap<String, Object> config = new HashMap<>();

        //서버의 정보 추가
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        //키 직렬화 정보 추가
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        //밸류 직렬화 정보 추가
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class);

        //해당 설정 정보로 설정된 프로듀서 팩토리 만들기
        return new DefaultKafkaProducerFactory<>(config);
    }

    //카프카 토픽에 데이터를 전송하기 위해 사용할 카프카 템플릿
    @Bean
    public KafkaTemplate<String,Long> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
