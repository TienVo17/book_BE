package com.example.book_be.dao;

import com.example.book_be.entity.NguoiDung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;


public interface NguoiDungRepository extends JpaRepository<NguoiDung, Long>, JpaSpecificationExecutor {
    boolean existsByTenDangNhap(String tenDangNhap);

    boolean existsByEmail(String email);

    NguoiDung findByTenDangNhap(String tenDangNhap);

    NguoiDung findByEmail(String email);
}
