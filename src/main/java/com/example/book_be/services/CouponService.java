package com.example.book_be.services;

import com.example.book_be.entity.Coupon;

import java.util.List;
import java.util.Map;

public interface CouponService {
    Map<String, Object> kiemTra(String ma, double tongTien);
    List<Coupon> findAll();
    Coupon save(Coupon coupon);
    Coupon update(int id, Coupon coupon);
    void delete(int id);
}
