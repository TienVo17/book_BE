package com.example.book_be.dao;

import com.example.book_be.entity.SachYeuThich;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RepositoryRestResource(path = "sach-yeu-thich")
public interface SachYeuThichRepository extends JpaRepository<SachYeuThich, Long> {

    List<SachYeuThich> findByNguoiDung_MaNguoiDung(int maNguoiDung);

    boolean existsByNguoiDung_MaNguoiDungAndSach_MaSach(int maNguoiDung, int maSach);

    @Transactional
    void deleteByNguoiDung_MaNguoiDungAndSach_MaSach(int maNguoiDung, int maSach);
}
