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
 * Mot luot binh chon "danh gia nay huu ich".
 *
 * <p>Bang da duoc dat xuong tu V11 kem {@code UNIQUE (ma_danh_gia, ma_nguoi_dung)} — do
 * moi la thu chan binh chon trung, khong phai kiem tra o service. Va
 * {@code ON DELETE CASCADE} theo {@code danhgia}: xoa mot danh gia phai keo theo binh
 * chon cua no, neu khong so luot se dem ca nhung dong mo coi.
 *
 * <p>Luu bang khoa vo huong thay vi quan he: dem theo nhom va kiem tra ton tai la toan bo
 * viec cua bang nay, ca hai deu khong can nap entity nao.
 */
@Data
@Entity
@Table(name = "danhgia_huu_ich")
public class DanhGiaHuuIch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_huu_ich")
    private long maHuuIch;

    @Column(name = "ma_danh_gia", nullable = false)
    private long maDanhGia;

    @Column(name = "ma_nguoi_dung", nullable = false)
    private int maNguoiDung;

    @Column(name = "tao_luc", nullable = false)
    private Timestamp taoLuc;
}
