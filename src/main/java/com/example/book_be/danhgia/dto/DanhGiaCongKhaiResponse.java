package com.example.book_be.danhgia.dto;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.shared.util.TenHienThiUtil;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * Danh gia nhu nguoi mua nhin thay.
 *
 * <p>Tach khoi {@link DanhGiaQuanTriResponse} co chu dich. Mot DTO phuc vu ca hai khan gia
 * la mot cai bay: che danh tinh cho duong cong khai se dong thoi lam man kiem duyet mu,
 * trong khi viec cua admin chinh la quyet dinh mot con nguoi cu the co bi go bai hay khong.
 *
 * <p>KHONG chua {@code maNguoiDung}: no la dinh danh noi bo, va cong khai no cho phep ghep
 * moi danh gia cua mot nguoi lai voi nhau tren toan bo cua hang.
 */
@Data
public class DanhGiaCongKhaiResponse {
    private long maDanhGia;
    private String nhanXet;
    private float diemXepHang;
    private Timestamp timestamp;
    /**
     * Ten da che, dung {@link com.example.book_be.shared.util.TenHienThiUtil}. Truoc day
     * moi danh gia hien chu "Khách hàng" vi backend khong tra ten nao ca.
     */
    private String tenHienThi;
    /** De giao dien danh dau "danh gia cua ban" ma khong can lo danh tinh ai khac. */
    private boolean laCuaToi;

    public static DanhGiaCongKhaiResponse from(SuDanhGia d, Integer maNguoiDungDangXem) {
        if (d == null) {
            return null;
        }
        DanhGiaCongKhaiResponse r = new DanhGiaCongKhaiResponse();
        r.maDanhGia = d.getMaDanhGia();
        r.nhanXet = d.getNhanXet();
        r.diemXepHang = d.getDiemXepHang();
        r.timestamp = d.getTimestamp();
        NguoiDung nguoiDung = d.getNguoiDung();
        r.tenHienThi = nguoiDung == null
                ? TenHienThiUtil.TEN_AN_DANH
                : TenHienThiUtil.che(nguoiDung.getHoDem(), nguoiDung.getTen());
        r.laCuaToi = maNguoiDungDangXem != null
                && d.getMaNguoiDung() != null
                && d.getMaNguoiDung().equals(maNguoiDungDangXem);
        return r;
    }

    public static List<DanhGiaCongKhaiResponse> fromList(List<SuDanhGia> list, Integer maNguoiDungDangXem) {
        return list == null ? List.of()
                : list.stream().map(d -> from(d, maNguoiDungDangXem)).toList();
    }
}
