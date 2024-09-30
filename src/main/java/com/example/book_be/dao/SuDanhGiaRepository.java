package com.example.book_be.dao;

import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.SuDanhGia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuDanhGiaRepository extends JpaRepository<SuDanhGia, Long> {
}
