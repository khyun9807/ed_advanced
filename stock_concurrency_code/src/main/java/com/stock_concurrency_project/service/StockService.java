package com.stock_concurrency_project.service;

import com.stock_concurrency_project.domain.Stock;
import com.stock_concurrency_project.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long stockId, Long quantity) {
        Stock stock = stockRepository.findById(stockId).orElseThrow();

        //원자적으로 업데이트하는 대신 메모리 상에서 임시 업데이트 후 저장
        //repository.updateQuantity(quantity);
        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //자바에서 지원하는 방법
    //메서드는 쓰레드 하나만 접근 가능
    //@Transactional과 궁합이 안좋다 decreaseV1 끝나고
    //@Transactional을 사용하면 트랜잭션이 끝날 때 업데이트 되는데
    //그 사이(업데이트전)에 다른 쓰레드에서 decreaseV1에 접근하여 사용하기 때문
    //@Transactional
    //하지만 발생하는 문제점 : synchronized는 하나의 프로세스 안에서만 보장됨
    //서버가 두 대 이상이 디비에 접근시 다른 프로세스 두 개 이상이 디비에 접근
    //이는 두 프로세스가 동시에 접근 가능하니 레이스 컨디션
    //실무에서는 서버가 두대 이상인 경우가 대부분이니 synchronized 잘 사용안함
    public synchronized void decreaseV1(Long stockId, Long quantity) {
        Stock stock = stockRepository.findById(stockId).orElseThrow();

        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //데이터베이스 활용 1 pessimistic lock == exclusive lock
    //실제 데이터에 락을 걸기. 락이 걸리면 해제전까지 다른 TX는 데이터를 조회도 못함.
    //데드락 걸릴 수 있다.
    //충돌이 잦은 케이스면 optimistic lock 보다 성능이 좋을 수 있다.
    //락을 통해 업데이트를 하기에 정합성 보장 하지만 성능 떨어진다.
    @Transactional
    public void decreaseV2(Long stockId, Long quantity) {
        Stock stock = stockRepository.findByIdWithPessimisticLock(stockId);

        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //데이터베이스 활용 2 optimistic lock
    //실제 락을 이용하지 않고 데이터의 버전을 이용
    //데이터를 조회하고 업데이트 수행시 내가 읽은 버전이 맞는지 확인
    //맞으면 업데이트 수행  update ..., set version=version+1 where id=? and version=?
    //다르면 앱 레벨에서 다시 읽은 후 작업을 수행해야 하는 로직을 필요시 넣어줘야함.
    //실제 락을 사용하지 않으므로 성능상 이점이 있지만 업데이트 실패시 재시도 코드 작성 번거로움
    //그리고 충돌이 빈번하면 오히려 성능 떨어질 수도. 즉 충돌 빈번이 예상되지 않을 때 사용해라
    @Transactional
    public void decreaseV3(Long stockId, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(stockId);

        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //데이터베이스 활용 3 named lock
    //이름을 가진 metadata lock
    //해제될 때까지 다른 세션이 락을 획득할 수 없음.
    //TX가 끝나도 자동으로 해제되지 않으니 명시적으로 해제 코드 써줘야함. 혹은 선점시간 지나야 풀림.
    //pessimistic lock과 유사하지만 전자는 로우|인덱스|테이블 단위
    //named lock은 별도의 mysql 공간에 락을 건다.
    //즉 다른 데이터소스를 분리해서 사용해야함
    //주로 분산락을 구현할 때 사용된다. 타임아웃을 구현할 수 있다.
    //데이터 삽입시 정합성을 맞출때도 사용된다.
    //실제 실무에서 운용시 구현이 더 복잡해질 수 있다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    //부모의 트랜잭션과 별개로 실행되어야 하기에 전파 방식 변경
    public void decreaseV4(Long stockId, Long quantity) {
        Stock stock = stockRepository.findById(stockId).orElseThrow();

        stock.decrease(quantity);

        //stockRepository.save(stock);
        stockRepository.saveAndFlush(stock);
    }



    //레디스 분산락 활용 2가지 == 분산락이니 db의 namedlock과 유사
    //레디스 분산락 활용 1 lettuce
    //setnx 명령어 활용하여 분산락 구현한다
    //직접 retry 로직을 구현해줘야 한다.
    //spin lock 획득 방식 => 레디스에 부하를 줄 수 있음 => sleep을 통해 재시도 비율 줄여 부하 줄이기
    //구현이 간단하다
    //로직 전 후로 락 획득/해제
    @Transactional
    public void decreaseV5(Long stockId, Long quantity) {
        Stock stock = stockRepository.findById(stockId).orElseThrow();

        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //레디스 분산락 활용 2 redisson
    //추가적인 라이브러리 필요
    //setnx 명령어 + pub/sub(락 해제 알림)
    //별도의 retry 로직을 구현하지 않아도 된다.
    //락 해제에도 락을 한 쓰레드만이 가능하다.
    //반복확인하는 spin lock 방식 대신 메시지로 lock 해제 인지하고 획득 하는 방식
    //즉 메시지를 통해 락 프리를 알림 => 레디스에 부하가 덜 감
    //redisson은 락 관련된 기능들을 제공해주기에 repository에 구현하지 않아도 된다.
    //로직 전 후로 락 획득/해제
    @Transactional
    public void decreaseV6(Long stockId, Long quantity) {
        Stock stock = stockRepository.findById(stockId).orElseThrow();

        stock.decrease(quantity);

        stockRepository.save(stock);
    }

    //재시도가 필요하지 않은 레디스 락 => lettuce
    //재시도가 필요한 레디스 락 => redisson

    //성능은 redis>mysql
    //추가 구축/관리 비용 발생
}
