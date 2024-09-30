package com.example.book_be.dao;

import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.HinhAnh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HinhAnhRepository extends JpaRepository<HinhAnh, Long> {
}
