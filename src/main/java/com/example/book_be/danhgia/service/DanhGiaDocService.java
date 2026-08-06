package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.dto.DanhGiaCongKhaiResponse;
import com.example.book_be.danhgia.dto.DanhGiaTrangResponse;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Duong doc cong khai cua phan danh gia.
 *
 * <p>Tach khoi {@link DanhGiaService} vi hai lop tra loi hai cau hoi khac han: lop kia
 * quan ly vong doi va bat bien khi ghi, lop nay chi doc va gop du lieu cho mot man hinh.
 */
@Service
public class DanhGiaDocService {

    /** Chan tren cho kich thuoc trang: client khong duoc tu quyet dinh tai cua may chu. */
    private static final int KICH_THUOC_TOI_DA = 50;
    private static final int KICH_THUOC_MAC_DINH = 10;

    private final SuDanhGiaRepository suDanhGiaRepository;

    public DanhGiaDocService(SuDanhGiaRepository suDanhGiaRepository) {
        this.suDanhGiaRepository = suDanhGiaRepository;
    }

    @Transactional(readOnly = true)
    public DanhGiaTrangResponse docTrang(int maSach, Integer trang, Integer kichThuoc,
                                         String sapXep, Integer locSao,
                                         Integer maNguoiDungDangXem) {
        Pageable pageable = PageRequest.of(
                trang == null || trang < 0 ? 0 : trang,
                chuanHoaKichThuoc(kichThuoc),
                sapXepTheo(sapXep));

        Page<SuDanhGia> ketQua = locSao == null
                ? suDanhGiaRepository.findBySach_MaSachAndTrangThai(
                        maSach, TrangThaiDanhGia.HIEN_THI, pageable)
                : suDanhGiaRepository.findBySach_MaSachAndTrangThaiAndDiemXepHang(
                        maSach, TrangThaiDanhGia.HIEN_THI, locSao, pageable);

        // Phan bo va cac so tong doc tu MOT nguon duy nhat va khong dinh gi toi bo loc:
        // neu tinh lai theo bo loc, bam vao cot "5 sao" se lam bon cot con lai bien mat.
        Map<Integer, Long> phanBo = new HashMap<>();
        long tongSo = 0;
        double tongDiem = 0;
        for (Object[] dong : suDanhGiaRepository.demTheoDiem(maSach)) {
            int sao = Math.round(((Number) dong[0]).floatValue());
            long soLuong = ((Number) dong[1]).longValue();
            phanBo.merge(sao, soLuong, Long::sum);
            tongSo += soLuong;
            tongDiem += (double) sao * soLuong;
        }

        return DanhGiaTrangResponse.cua(
                DanhGiaCongKhaiResponse.fromList(ketQua.getContent(), maNguoiDungDangXem),
                ketQua.getNumber(),
                ketQua.getSize(),
                ketQua.getTotalPages(),
                tongSo,
                tongSo == 0 ? 0 : tongDiem / tongSo,
                phanBo);
    }

    private int chuanHoaKichThuoc(Integer kichThuoc) {
        if (kichThuoc == null || kichThuoc < 1) {
            return KICH_THUOC_MAC_DINH;
        }
        return Math.min(kichThuoc, KICH_THUOC_TOI_DA);
    }

    /**
     * {@code huu-ich} duoc nhan nhung chua co du lieu that: so luot binh chon huu ich chi
     * ton tai tu phase sau. Cho toi luc do no tuong duong {@code moi-nhat} — nhan tu bay gio
     * de giao dien va URL da chia se khong phai doi khi du lieu san sang.
     */
    private Sort sapXepTheo(String sapXep) {
        String khoa = sapXep == null ? "moi-nhat" : sapXep;
        Sort chinh = switch (khoa) {
            case "cu-nhat" -> Sort.by(Sort.Direction.ASC, "timestamp");
            case "diem-cao" -> Sort.by(Sort.Direction.DESC, "diemXepHang")
                    .and(Sort.by(Sort.Direction.DESC, "timestamp"));
            case "diem-thap" -> Sort.by(Sort.Direction.ASC, "diemXepHang")
                    .and(Sort.by(Sort.Direction.DESC, "timestamp"));
            default -> Sort.by(Sort.Direction.DESC, "timestamp");
        };
        // Chot thu tu bang khoa chinh trong MOI kieu sap xep. Hai danh gia cung timestamp
        // (hoac cung diem) lam thu tu khong on dinh giua hai request, va khi do mot dong
        // co the xuat hien o ca hai trang lien tiep hoac khong o trang nao.
        return chinh.and(Sort.by(Sort.Direction.DESC, "maDanhGia"));
    }
}
