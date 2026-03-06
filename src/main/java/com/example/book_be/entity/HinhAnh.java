package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "hinh_anh")
public class HinhAnh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hinh_anh")
    private int maHinhAnh;

    @Column(name = "ten_hinh_anh", length = 256)
    private String tenHinhAnh;

    @Column(name = "la_icon")
    private Boolean icon;

    @Column(name = "url_hinh", columnDefinition = "LONGTEXT")
    @Lob
    private String urlHinh;

    @JsonIgnore
    @Column(name = "du_lieu_anh", columnDefinition = "LONGTEXT")
    @Lob
    private String dataImage;

    @Column(name = "cloudinary_public_id", length = 255)
    private String cloudinaryPublicId;

    @JsonIgnore
    @ManyToOne(cascade = {
            CascadeType.MERGE,
    })
    @JoinColumn(name = "ma_sach", nullable = false)
    private Sach sach;
}
