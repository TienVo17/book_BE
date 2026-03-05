package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table(name = "coupon")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_coupon")
    private int maCoupon;

    @Column(name = "ma", unique = true, nullable = false, length = 50)
    private String ma;

    @Column(name = "loai")
    @Enumerated(EnumType.STRING)
    private LoaiGiamGia loai;

    @Column(name = "gia_tri_giam")
    private double giaTriGiam;

    @Column(name = "gia_tri_toi_thieu")
    private double giaTriToiThieu;

    @Column(name = "han_su_dung")
    @Temporal(TemporalType.TIMESTAMP)
    private Date hanSuDung;

    @Column(name = "so_luong_toi_da")
    private int soLuongToiDa;

    @Column(name = "da_su_dung")
    private int daSuDung = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
