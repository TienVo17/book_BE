package com.example.book_be.giohang.repository;

import com.example.book_be.giohang.domain.GioHangMergeReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface GioHangMergeReceiptRepository extends JpaRepository<GioHangMergeReceipt, Long> {
    Optional<GioHangMergeReceipt> findByNguoiDung_MaNguoiDungAndIdempotencyKey(
            int maNguoiDung,
            String idempotencyKey
    );

    @Query(value = "SELECT ma_gio_hang_merge_receipt "
            + "FROM gio_hang_merge_receipt "
            + "WHERE created_at < :cutoff "
            + "ORDER BY created_at, ma_gio_hang_merge_receipt "
            + "LIMIT :limit", nativeQuery = true)
    List<Long> findExpiredIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GioHangMergeReceipt receipt "
            + "WHERE receipt.maGioHangMergeReceipt IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
