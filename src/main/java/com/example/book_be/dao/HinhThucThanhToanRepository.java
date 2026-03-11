package com.example.book_be.dao;

import com.example.book_be.entity.HinhThucThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RepositoryRestResource(path = "hinh-thuc-thanh-toan")
public interface HinhThucThanhToanRepository extends JpaRepository<HinhThucThanhToan, Long> {
    Optional<HinhThucThanhToan> findByMaCodeIgnoreCase(String maCode);
}
