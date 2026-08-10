package com.example.book_be.donhang.web;

import com.example.book_be.donhang.dto.HinhThucGiaoHangResponse;
import com.example.book_be.donhang.repository.HinhThucGiaoHangRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Danh sach hinh thuc giao hang cho man hinh thanh toan.
 *
 * <p>Cong khai: khach chua dang nhap van xem duoc phi van chuyen khi cong dong hang, va day
 * chi la bang tham chieu, khong chua du lieu ca nhan nao.
 */
@RestController
@RequestMapping("/api/hinh-thuc-giao-hang")
public class HinhThucGiaoHangController {

    private final HinhThucGiaoHangRepository repository;

    public HinhThucGiaoHangController(HinhThucGiaoHangRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<HinhThucGiaoHangResponse>> findAll() {
        // Sap xep theo phi tang dan: lua chon re nhat len dau, va thu tu on dinh giua cac lan goi
        // de man hinh khong doi cho cac muc moi lan tai lai.
        return ResponseEntity.ok(repository.findAll().stream()
                .sorted(Comparator.comparingDouble(nguon -> nguon.getChiPhiGiaoHang()))
                .map(HinhThucGiaoHangResponse::from)
                .toList());
    }
}
