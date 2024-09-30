package com.example.book_be.dao;

import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.DonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonHangRepository extends JpaRepository<DonHang, Long> {
}
