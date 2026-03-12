package com.example.book_be.dao;

import com.example.book_be.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByMa(String ma);
    boolean existsByMa(String ma);

    @Modifying
    @Query("""
            update Coupon c
            set c.daSuDung = c.daSuDung + 1
            where c.maCoupon = :maCoupon
              and (c.soLuongToiDa <= 0 or c.daSuDung < c.soLuongToiDa)
            """)
    int tangLuotSuDungNeuConHieuLuc(@Param("maCoupon") int maCoupon);
}
