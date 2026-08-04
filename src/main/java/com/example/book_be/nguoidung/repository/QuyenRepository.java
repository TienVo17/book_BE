package com.example.book_be.nguoidung.repository;

import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.nguoidung.domain.Quyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

/** exported=false: danh sach quyen he thong khong can cong khai; dung /api/admin/quyen/findAll. */
@RepositoryRestResource(path = "quyen", exported = false)
public interface QuyenRepository extends JpaRepository<Quyen, Long> {
    public Quyen findByTenQuyen(String tenQuyen);
}
