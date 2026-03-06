package com.example.book_be.dao;

import com.example.book_be.entity.HinhAnh;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(path = "hinh-anh")
public interface HinhAnhRepository extends JpaRepository<HinhAnh, Long>, JpaSpecificationExecutor<HinhAnh> {

    @Query(value = """
            SELECT *
            FROM hinh_anh
            WHERE (url_hinh LIKE 'data:image/%' OR du_lieu_anh LIKE 'data:image/%')
            ORDER BY ma_hinh_anh
            """, nativeQuery = true)
    List<HinhAnh> findLegacyImages(Pageable pageable);
}
