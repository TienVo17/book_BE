package com.example.book_be.nhantin.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Mot dia chi email da dang ky nhan tin khuyen mai.
 *
 * <p>Email luu o dang da chuan hoa (trim + lower). Rang buoc UNIQUE tren cot nay la thu that
 * su chan trung, chu khong phai buoc kiem tra o tang service: hai request dong thoi cung mot
 * email deu vuot qua duoc buoc kiem tra roi cung ghi.
 */
@Data
@Entity
@Table(name = "dang_ky_nhan_tin")
public class DangKyNhanTin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dang_ky")
    private Long maDangKy;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** Khoa ngau nhien de huy dang ky. Khong doan duoc nen khong ai huy ho nguoi khac. */
    @Column(name = "ma_huy", nullable = false, unique = true, length = 36)
    private String maHuy;

    @Column(name = "ngay_dang_ky", nullable = false)
    private Instant ngayDangKy;

    @Column(name = "da_huy", nullable = false)
    private Boolean daHuy;
}
