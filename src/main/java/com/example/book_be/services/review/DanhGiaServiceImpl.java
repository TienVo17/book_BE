package com.example.book_be.services.review;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dao.SuDanhGiaRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.SuDanhGia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

        suDanhGia.setNguoiDung(nguoiDungRepository.findById(maNguoiDung).orElseThrow(
                () -> new RuntimeException("Không tìm thấy người dùng")));
        suDanhGia.setSach(sachRepository.findById(maSach).orElseThrow(
                () -> new RuntimeException("Không tìm thấy sách")));
        return suDanhGiaRepository.save(suDanhGia);
    }
}
