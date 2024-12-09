package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "danhgia")
public class SuDanhGia  {
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


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinColumn(name="ma_nguoi_dung",nullable = false)
    private NguoiDung nguoiDung;

    @Column(name = "ma_nguoi_dung", insertable = false, updatable = false)
    private Integer maNguoiDung;

    @ManyToOne(cascade = {
            CascadeType.DETACH,CascadeType.MERGE,CascadeType.REFRESH,CascadeType.PERSIST
    })

    @JsonIgnore
    @JoinColumn(name="ma_sach",nullable = false)
    private Sach sach;

    @Column(name = "is_active")
    private Integer isActive;

}
