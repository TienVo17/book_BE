package com.example.book_be.donhang.repository;

import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.donhang.domain.HinhThucGiaoHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "hinh-thuc-giao-hang")
public interface HinhThucGiaoHangRepository extends JpaRepository<HinhThucGiaoHang, Long> {
}
