package com.example.book_be.sach.repository;

import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.sach.domain.NhaCungCap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
@RepositoryRestResource(path = "nha-cung-cap")
public interface NhaCungCapRepository extends JpaRepository<NhaCungCap, Long> {
}


