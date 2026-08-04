package com.example.book_be.danhgia.service;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.danhgia.domain.SuDanhGia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;

@Service
public class DanhGiaServiceImpl implements DanhGiaService {
    @Autowired
    NguoiDungRepository nguoiDungRepository;
    @Autowired
    SuDanhGiaRepository suDanhGiaRepository;
    @Autowired
    private SachRepository sachRepository;

    @Override
    public SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach) {
        SuDanhGia suDanhGia = new SuDanhGia();
        suDanhGia.setNhanXet(nhanXet);
        suDanhGia.setDiemXepHang(diemXepHang);
        suDanhGia.setTimestamp(new Timestamp(System.currentTimeMillis()));
        suDanhGia.setIsActive(1);
        suDanhGia.setNguoiDung(nguoiDungRepository.findById(maNguoiDung).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.")));
        suDanhGia.setSach(sachRepository.findById(maSach).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sách.")));
        return suDanhGiaRepository.save(suDanhGia);
    }

    /** Chi chu so huu duoc sua noi dung danh gia cua chinh minh. */
    @Override
    public SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia, Long maNguoiDungYeuCau) {
        SuDanhGia db = suDanhGiaRepository.findById(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        kiemTraChuSoHuu(db, maNguoiDungYeuCau);

        if (danhGia.getDiemXepHang() < 1 || danhGia.getDiemXepHang() > 5
                || danhGia.getNhanXet() == null || danhGia.getNhanXet().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đánh giá không hợp lệ.");
        }

        db.setDiemXepHang(danhGia.getDiemXepHang());
        db.setNhanXet(danhGia.getNhanXet());
        db.setTimestamp(new Timestamp(System.currentTimeMillis()));
        return suDanhGiaRepository.save(db);
    }

    /** Chu so huu tu xoa danh gia cua minh; ADMIN xoa duoc de kiem duyet noi dung. */
    @Override
    public SuDanhGia deleteReview(Long maDanhGia, Long maNguoiDungYeuCau, boolean laQuanTri) {
        SuDanhGia db = suDanhGiaRepository.findById(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        if (!laQuanTri) {
            kiemTraChuSoHuu(db, maNguoiDungYeuCau);
        }
        suDanhGiaRepository.delete(db);
        return db;
    }

    private void kiemTraChuSoHuu(SuDanhGia danhGia, Long maNguoiDungYeuCau) {
        NguoiDung chuSoHuu = danhGia.getNguoiDung();
        if (chuSoHuu == null || maNguoiDungYeuCau == null
                || chuSoHuu.getMaNguoiDung() != maNguoiDungYeuCau.intValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền với đánh giá này.");
        }
    }
}
