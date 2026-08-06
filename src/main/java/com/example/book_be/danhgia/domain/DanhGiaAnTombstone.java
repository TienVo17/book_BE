package com.example.book_be.danhgia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.sql.Timestamp;

/**
 * Dau vet rang cap (nguoi dung, sach) da tung bi admin an mot lan.
 *
 * <p>Ton tai rieng khoi {@code danhgia} co chu dich. Co {@code tung_bi_an} tren chinh
 * dong danh gia bien mat cung dong do khi chu so huu tu xoa — ma do dung la duong lach:
 * admin an, tac gia xoa bai cua minh (hop le), rang buoc duy nhat (nguoi, sach) duoc giai
 * phong, don DA_GIAO van con, dang lai duoc. Bang nay song sot qua buoc xoa nen vong lap
 * do khong khep lai duoc.
 */
@Data
@Entity
@Table(name = "danhgia_an_tombstone")
public class DanhGiaAnTombstone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_tombstone")
    private long maTombstone;

    @Column(name = "ma_nguoi_dung", nullable = false)
    private int maNguoiDung;

    @Column(name = "ma_sach", nullable = false)
    private int maSach;

    @Column(name = "tao_luc", nullable = false)
    private Timestamp taoLuc;
}
