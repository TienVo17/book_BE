package com.example.book_be.dao;

import com.example.book_be.entity.ChiTietDonHang;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

/**
 * Repository for ChiTietDonHang (order line items).
 * Includes queries for top best-selling books aggregation.
 */
@RepositoryRestResource(path = "chi-tiet-don-hang")
public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang, Long>, JpaSpecificationExecutor {

    // Returns Object[] rows: [maSach, tenSach, tongBan] ordered by sales volume desc
    @Query("SELECT ct.sach.maSach, ct.sach.tenSach, SUM(ct.soLuong) as tongBan FROM ChiTietDonHang ct GROUP BY ct.sach.maSach, ct.sach.tenSach ORDER BY tongBan DESC")
    List<Object[]> findTopBanChay(Pageable pageable);
}
