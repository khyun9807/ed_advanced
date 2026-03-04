package com.coupon_fcfs_project.api.repository;

import com.coupon_fcfs_project.api.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
