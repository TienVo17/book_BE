package com.example.book_be.dao;

import com.example.book_be.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByMa(String ma);
    boolean existsByMa(String ma);
}
