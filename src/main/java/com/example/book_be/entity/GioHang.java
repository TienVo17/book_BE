package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "gio_hang")
public class GioHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_gio_hang")
    private int maGioHang;
    @Column(name = "so_luong")
    private int soLuong;
    @ManyToOne()
    @JoinColumn(name = "ma_sach", nullable = false)
    private Sach sach;
    @ManyToOne()
    @JoinColumn(name = "ma_nguoi_dung", nullable = false)
    private NguoiDung nguoiDung;

    @Override
    public String toString() {
        return "GioHang{" +
                "maGioHang=" + maGioHang +
                ", soLuong=" + soLuong +
                ", sach=" + sach.getMaSach()
                ;
    }
}
