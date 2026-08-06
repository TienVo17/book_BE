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
 * Anh dinh kem cua mot danh gia.
 *
 * <p>{@code cloudinaryPublicId} la tay cam duy nhat de xoa doi tuong ben Cloudinary. Cascade
 * cua database xoa dong nay ma khong chay mot dong Java nao, nen {@code deleteReview} phai
 * doc va don anh TUONG MINH truoc khi xoa danh gia — neu khong, anh nam lai vinh vien va
 * chinh cau DELETE vua huy mat ma de tim ra chung.
 */
@Data
@Entity
@Table(name = "danhgia_hinh_anh")
public class DanhGiaHinhAnh {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hinh_anh")
    private long maHinhAnh;

    @Column(name = "ma_danh_gia", nullable = false)
    private long maDanhGia;

    @Column(name = "url_hinh", nullable = false, length = 500)
    private String urlHinh;

    @Column(name = "cloudinary_public_id", length = 255)
    private String cloudinaryPublicId;

    /** Khoa do client tao va giu nguyen khi retry cung mot tep. */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** Phan biet replay hop le voi viec tai noi dung khac bang mot khoa da dung. */
    @Column(name = "noi_dung_sha256", nullable = false, length = 64)
    private String noiDungSha256;

    @Column(name = "thu_tu", nullable = false)
    private int thuTu;

    @Column(name = "tao_luc", nullable = false)
    private Timestamp taoLuc;
}
