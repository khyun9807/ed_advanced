package com.stock_concurrency_project.repository;

import com.stock_concurrency_project.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    //JPA에서 제공하는 어노테이션은 락을 쉽게 구현해준다
    //mysql에서는 select ... for update라는 쿼리가 실제로 날라간다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id=:id")
    Stock findByIdWithPessimisticLock(Long id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id=:id")
    Stock findByIdWithOptimisticLock(Long id);

    @Query(value = "select get_lock(:key,3)",nativeQuery = true)
    void getLock(@Param("key") String key);

    @Query(value = "select release_lock(:key)",nativeQuery = true)
    void releaseLock(@Param("key") String key);
}
