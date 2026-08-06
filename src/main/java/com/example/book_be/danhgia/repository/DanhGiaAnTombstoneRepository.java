package com.example.book_be.danhgia.repository;

import com.example.book_be.danhgia.domain.DanhGiaAnTombstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/** Khong expose qua Spring Data REST: day la du lieu kiem duyet noi bo. */
@RepositoryRestResource(exported = false)
public interface DanhGiaAnTombstoneRepository extends JpaRepository<DanhGiaAnTombstone, Long> {

    boolean existsByMaNguoiDungAndMaSach(int maNguoiDung, int maSach);
}
