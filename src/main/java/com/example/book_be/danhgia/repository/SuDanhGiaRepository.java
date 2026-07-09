package com.example.book_be.danhgia.repository;

import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "su-danh-gia")
public interface SuDanhGiaRepository extends JpaRepository<SuDanhGia, Long>, JpaSpecificationExecutor {
}
