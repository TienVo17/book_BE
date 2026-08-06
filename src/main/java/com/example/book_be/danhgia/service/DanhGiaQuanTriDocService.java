package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.dto.DanhGiaHinhAnhCongKhaiResponse;
import com.example.book_be.danhgia.dto.DanhGiaQuanTriResponse;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Nap mot trang danh gia kem anh cho man kiem duyet, khong tao N+1. */
@Service
public class DanhGiaQuanTriDocService {

    private final SuDanhGiaRepository suDanhGiaRepository;
    private final DanhGiaHinhAnhRepository danhGiaHinhAnhRepository;

    public DanhGiaQuanTriDocService(SuDanhGiaRepository suDanhGiaRepository,
                                    DanhGiaHinhAnhRepository danhGiaHinhAnhRepository) {
        this.suDanhGiaRepository = suDanhGiaRepository;
        this.danhGiaHinhAnhRepository = danhGiaHinhAnhRepository;
    }

    @Transactional(readOnly = true)
    public Page<DanhGiaQuanTriResponse> docTrang(Integer trang) {
        int soTrang = trang == null || trang < 0 ? 0 : trang;
        Page<DanhGiaQuanTriResponse> ketQua = DanhGiaQuanTriResponse.fromPage(
                suDanhGiaRepository.timTrangChoQuanTri(PageRequest.of(soTrang, 10)));
        if (ketQua.isEmpty()) {
            return ketQua;
        }

        List<Long> danhSachMa = ketQua.stream()
                .map(DanhGiaQuanTriResponse::getMaDanhGia)
                .toList();
        Map<Long, List<DanhGiaHinhAnhCongKhaiResponse>> anhTheoDanhGia = new HashMap<>();
        for (var anh : danhGiaHinhAnhRepository.findByMaDanhGiaInOrderByThuTuAsc(danhSachMa)) {
            anhTheoDanhGia.computeIfAbsent(anh.getMaDanhGia(), key -> new ArrayList<>())
                    .add(DanhGiaHinhAnhCongKhaiResponse.from(anh));
        }
        for (DanhGiaQuanTriResponse danhGia : ketQua) {
            danhGia.setAnhDinhKem(
                    anhTheoDanhGia.getOrDefault(danhGia.getMaDanhGia(), List.of()));
        }
        return ketQua;
    }
}
