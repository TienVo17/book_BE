package com.example.book_be.dao;

import com.example.book_be.entity.TheLoai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TheLoaiRepository extends JpaRepository<TheLoai, Integer> {

    @Query("SELECT t.maTheLoai, t.tenTheLoai, t.slug, COUNT(s) FROM TheLoai t LEFT JOIN t.listSach s GROUP BY t.maTheLoai, t.tenTheLoai, t.slug ORDER BY t.tenTheLoai")
    List<Object[]> findAllWithBookCount();

    @Query("SELECT t.maTheLoai, t.tenTheLoai, t.slug, COUNT(s) FROM TheLoai t LEFT JOIN t.listSach s GROUP BY t.maTheLoai, t.tenTheLoai, t.slug HAVING COUNT(s) > 0 ORDER BY t.tenTheLoai")
    List<Object[]> findAllPublicWithBookCount();

    Optional<TheLoai> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndMaTheLoaiNot(String slug, Integer maTheLoai);

    boolean existsByTenTheLoaiIgnoreCase(String tenTheLoai);

    boolean existsByTenTheLoaiIgnoreCaseAndMaTheLoaiNot(String tenTheLoai, Integer maTheLoai);

    @Query("SELECT COUNT(s) FROM TheLoai t LEFT JOIN t.listSach s WHERE t.maTheLoai = :maTheLoai")
    long countSachByMaTheLoai(@Param("maTheLoai") Integer maTheLoai);

    @Query("SELECT COUNT(s) FROM TheLoai t JOIN t.listSach s WHERE t.maTheLoai = :maTheLoai AND COALESCE(s.isActive, 1) = 1")
    long countActiveSachByMaTheLoai(@Param("maTheLoai") Integer maTheLoai);
}
