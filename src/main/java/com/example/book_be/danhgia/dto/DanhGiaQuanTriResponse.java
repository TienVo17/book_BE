package com.example.book_be.danhgia.dto;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.nguoidung.domain.NguoiDung;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.sql.Timestamp;

/**
 * Danh gia nhu admin kiem duyet nhin thay.
 *
 * <p>Co danh tinh that, co chu dich: quyet dinh go bai hay khong la quyet dinh ve mot con
 * nguoi cu the, va lam viec do trong bong toi thi te hon. Test PII cua duong cong khai
 * KHONG duoc ap len DTO nay — xem {@link DanhGiaCongKhaiResponse}.
 *
 * <p>{@code trangThai} thay cho {@code isActive} cu. Man quan tri truoc day doc mot truong
 * kieu {@code any} nen gia tri thieu tro thanh {@code undefined} im lang: moi danh gia hien
 * nhan "Da an" bat ke trang thai that, va nut "an" goi {@code !undefined} nen luon gui
 * lenh "hien" — cong cu kiem duyet dao nguoc y nghia ma khong he bao loi.
 */
@Data
public class DanhGiaQuanTriResponse {
    private long maDanhGia;
    private String nhanXet;
    private float diemXepHang;
    private Timestamp timestamp;
    private Integer maNguoiDung;
    private String tenNguoiDung;
    private Integer maSach;
    private String tenSach;
    private TrangThaiDanhGia trangThai;
    /** Da tung bi an it nhat mot lan, ke ca khi hien tai dang hien thi. */
    private boolean tungBiAn;
    private Integer maDonHang;

    public static DanhGiaQuanTriResponse from(SuDanhGia d) {
        if (d == null) {
            return null;
        }
        DanhGiaQuanTriResponse r = new DanhGiaQuanTriResponse();
        r.maDanhGia = d.getMaDanhGia();
        r.nhanXet = d.getNhanXet();
        r.diemXepHang = d.getDiemXepHang();
        r.timestamp = d.getTimestamp();
        r.maNguoiDung = d.getMaNguoiDung();
        NguoiDung nguoiDung = d.getNguoiDung();
        r.tenNguoiDung = nguoiDung == null ? null : nguoiDung.getTenDangNhap();
        r.maSach = d.getSach() == null ? null : d.getSach().getMaSach();
        r.tenSach = d.getSach() == null ? null : d.getSach().getTenSach();
        r.trangThai = d.getTrangThai();
        r.tungBiAn = d.isTungBiAn();
        r.maDonHang = d.getMaDonHang();
        return r;
    }

    public static Page<DanhGiaQuanTriResponse> fromPage(Page<SuDanhGia> page) {
        return page == null ? null : page.map(DanhGiaQuanTriResponse::from);
    }
}
