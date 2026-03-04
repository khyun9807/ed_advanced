package com.stock_concurrency_project.facade;

import com.stock_concurrency_project.repository.RedisLockRepository;
import com.stock_concurrency_project.repository.StockRepository;
import com.stock_concurrency_project.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockFacade {
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final RedisLockRepository redisLockRepository;
    private final RedissonClient redissonClient;

    //재시도 로직
    public void decreaseV3(Long stockId, Long quantity) throws InterruptedException {
        while(true) {
            try{
                stockService.decreaseV3(stockId, quantity);

                break;
            }
            catch(Exception e){
                Thread.sleep(50);
            }
        }
    }

    @Transactional
    public void decreaseV4(Long stockId, Long quantity) {
        try{
            stockRepository.getLock(stockId.toString());
            stockService.decreaseV4(stockId, quantity);
        }
        finally {
            stockRepository.releaseLock(stockId.toString());
        }
    }


    public void decreaseV5(Long stockId, Long quantity) throws InterruptedException {
        while(!redisLockRepository.lock(stockId)){
            Thread.sleep(100);
        }

        try{
            stockService.decreaseV5(stockId, quantity);
        }
        finally{
            redisLockRepository.unlock(stockId);
        }
    }

    public void decreaseV6(Long stockId, Long quantity) throws InterruptedException {
        RLock lock = redissonClient.getLock(stockId.toString());

        boolean available=false;
        try{
            //10초 동안 락 획득 기다리기
            //10초 전에 락 획득 가능 메시지 오면 획득 시도 == pub/sub 으로 대기후 재시도
            //락 획득 후 1초 이상되면 락 자동해제
            available = lock.tryLock(10, 1, TimeUnit.SECONDS);
            if(!available){
                log.info("lock 획득 실패");
                return;
            }

            stockService.decreaseV6(stockId, quantity);
        }
        finally {
            if(available&&lock.isHeldByCurrentThread())
                lock.unlock();
        }
    }
}
