package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "danhgia")
public class SuDanhGia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_danh_gia")
    private long maDanhGia;
    @Column(name = "nhan_xet",columnDefinition = "text")
    private String nhanXet;
    @Column(name = "diem_xep_hang")
    private float diemXepHang;
    @Column(name = "timestamp")
    private Timestamp timestamp; // thời gian bình luận

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinColumn(name="ma_nguoi_dung",nullable = false)
    private NguoiDung nguoiDung;
    @ManyToOne(cascade = {
            CascadeType.DETACH,CascadeType.MERGE,CascadeType.REFRESH,CascadeType.PERSIST
    })
    @JoinColumn(name="ma_sach",nullable = false)
    private Sach sach;
}
