package com.stock_concurrency_project.service;

import com.stock_concurrency_project.domain.Stock;
import com.stock_concurrency_project.facade.StockFacade;
import com.stock_concurrency_project.repository.StockRepository;
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
class StockServiceTest {

    @Autowired
    StockService stockService;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    StockFacade stockFacade;

    @BeforeEach
    public void setUp() {
        stockRepository.saveAndFlush(Stock.create(1L,100L));
    }

    @AfterEach
    public void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    void decrease() {
        stockService.decrease(1L,1L);

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(99L);
    }

    @Test
    void concurrency100() throws InterruptedException {
        int threadCount=100;

        //비동기 실행 작업을 단순하게 사용할 수 있게 해주는 java api
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        //**
        // *자바에서 **latch(래치)**는 “여러 스레드의 진행을 특정 조건이 만족될 때까지 묶어두는 동기화 장치”야.
        //대표 구현이 java.util.concurrent.CountDownLatch.
        //CountDownLatch가 하는 역할
        //어떤 스레드(들)는 기다림(await) 상태로 멈춰 있음
        //다른 스레드(들)가 작업을 끝낼 때마다 카운트를 하나씩 줄임(countDown)
        //카운트가 0이 되는 순간, 기다리던 스레드들이 한꺼번에 풀려서 진행
        //즉, “N개의 작업이 끝날 때까지 대기” 같은 걸 깔끔하게 구현할 때 쓰는 도구야.
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for(int i=0;i<threadCount;i++){
            executorService.submit(()->{
                try{
                    //stockService.decrease(1L,1L);
                    //stockService.decreaseV1(1L,1L);
                    //stockService.decreaseV2(1L,1L);
                    //stockFacade.decreaseV3(1L,1L);
                    //stockFacade.decreaseV4(1L,1L);
                    //stockFacade.decreaseV5(1L,1L);
                    stockFacade.decreaseV6(1L,1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally{
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();
        Assertions.assertThat(stock.getQuantity()).isEqualTo(0L);
    }
}