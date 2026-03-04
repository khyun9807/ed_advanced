package com.coupon_fcfs_project.consumer.repository;

import com.coupon_fcfs_project.consumer.domain.FailedCouponEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedCouponEventRepository extends JpaRepository<FailedCouponEvent, Long> {

}
