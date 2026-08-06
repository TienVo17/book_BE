package com.example.book_be.danhgia.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mot request du cho ca khoi danh gia tren trang san pham: danh sach da phan trang, diem
 * trung binh, va phan bo sao.
 *
 * <p>Truoc day {@code findAll} tra ve toan bo danh gia cua mot cuon trong mot mang khong
 * gioi han, va trang san pham khong co cach nao biet phan bo sao ma khong tu dem lai.
 */
@Data
public class DanhGiaTrangResponse {
    private List<DanhGiaCongKhaiResponse> content;
    private int trang;
    private int kichThuoc;
    private int tongSoTrang;
    /**
     * Tong so danh gia HIEN_THI cua cuon sach — KHONG phai so dong khop bo loc dang chon.
     * Cung mot ly do voi {@link #phanBo}.
     */
    private long tongSo;
    private double diemTrungBinh;
    /**
     * Phan bo sao tinh tren TOAN BO danh gia HIEN_THI, khong theo bo loc dang chon.
     * Neu tinh theo bo loc thi thanh phan bo tu xoa het cac cot khac ngay khi nguoi dung
     * bam vao mot cot — bien chinh no thanh thu vo dung.
     */
    private Map<Integer, Long> phanBo;

    public static DanhGiaTrangResponse cua(List<DanhGiaCongKhaiResponse> content, int trang,
                                           int kichThuoc, int tongSoTrang, long tongSo,
                                           double diemTrungBinh, Map<Integer, Long> phanBoTho) {
        DanhGiaTrangResponse r = new DanhGiaTrangResponse();
        r.content = content;
        r.trang = trang;
        r.kichThuoc = kichThuoc;
        r.tongSoTrang = tongSoTrang;
        r.tongSo = tongSo;
        r.diemTrungBinh = diemTrungBinh;
        // Luon du 5 khoa, ke ca khoa co gia tri 0: giao dien ve du 5 thanh, va thieu khoa
        // se thanh thanh bien mat thay vi thanh rong.
        Map<Integer, Long> phanBo = new LinkedHashMap<>();
        for (int sao = 5; sao >= 1; sao--) {
            phanBo.put(sao, phanBoTho.getOrDefault(sao, 0L));
        }
        r.phanBo = phanBo;
        return r;
    }
}
