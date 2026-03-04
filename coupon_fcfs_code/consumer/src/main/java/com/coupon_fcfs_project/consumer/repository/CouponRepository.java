package com.coupon_fcfs_project.consumer.repository;


import com.coupon_fcfs_project.consumer.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
