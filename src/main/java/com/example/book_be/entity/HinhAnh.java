package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "hinh_anh")
public class HinhAnh {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ma_hinh_anh")
    private int maHinhAnh;
    @Column(name="ten_hinh_anh",length = 256)
    private String tenHinhAnh;
    @Column(name="la_icon")
    private Boolean icon;

    @Column(name="url_hinh",columnDefinition = "LONGTEXT" )
    @Lob
    private String urlHinh;
//    @Column(name="du_lieu_anh",columnDefinition = "LONGTEXT")
//    @Lob
//    private String dataImage;// Dữ liệu ảnh
@JsonIgnore
    @ManyToOne(cascade = {
            CascadeType.MERGE,
    })
    @JoinColumn(name="ma_sach",nullable = false)
    private Sach sach;
}
