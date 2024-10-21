package com.example.book_be.dao;

import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.NguoiDung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "nguoi-dung")
public interface NguoiDungRepository extends JpaRepository<NguoiDung, Long> {
    boolean existsByTenDangNhap(String tenDangNhap);
    boolean existsByEmail(String email);
}
