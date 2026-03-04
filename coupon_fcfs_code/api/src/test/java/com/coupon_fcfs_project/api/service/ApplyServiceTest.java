package com.coupon_fcfs_project.api.service;

import com.coupon_fcfs_project.api.repository.CouponRedisRepository;
import com.coupon_fcfs_project.api.repository.CouponRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class ApplyServiceTest {

    @Autowired
    ApplyService applyService;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    CouponRedisRepository couponRedisRepository;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
        couponRedisRepository.clear();
        couponRepository.deleteAll();
    }

    @Test
    void applyOnce(){
        applyService.apply(1L);

        long count = couponRepository.count();

        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void applyThousand() throws InterruptedException {
        int threadCount=1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for(int i=0;i<threadCount;i++){
            //long userId=i;
            long userId=1;
            executorService.submit(()->{
                try{
                    //applyService.apply(userId);
                    //applyService.applyV2(userId);
                    //applyService.applyV3(userId);
                    applyService.applyV4(userId);
                }
                finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        Thread.sleep(5_000);

        long count = couponRepository.count();

        //Assertions.assertThat(count).isEqualTo(100);
        Assertions.assertThat(count).isEqualTo(1);
    }
}