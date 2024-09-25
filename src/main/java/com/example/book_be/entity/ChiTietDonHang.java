package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name= "chi_tiet_don_hang")
public class ChiTietDonHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_chi_tiet_don_hang ")
    private long maChiTietDonHang;
    @Column(name = "so_luong ")
    private int soLuong;
    @Column(name = "gia_ban ")
    private double giaBan;
    private boolean danhGia;
    @ManyToOne(cascade = {
            CascadeType.DETACH,CascadeType.MERGE,CascadeType.REFRESH,CascadeType.PERSIST
    })
    @JoinColumn(name="ma_sach",nullable = false)
    private Sach sach;
    @ManyToOne(cascade = {
            CascadeType.DETACH,CascadeType.MERGE,CascadeType.REFRESH,CascadeType.PERSIST
    })
    @JoinColumn(name="ma_don_hang",nullable = false)
    private DonHang donHang;
}
