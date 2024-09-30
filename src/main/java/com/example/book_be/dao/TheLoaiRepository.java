package com.example.book_be.dao;

import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.TheLoai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TheLoaiRepository extends JpaRepository<TheLoai, Long> {
}
