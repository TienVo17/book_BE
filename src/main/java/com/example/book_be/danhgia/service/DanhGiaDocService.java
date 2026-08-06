package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.dto.DanhGiaCongKhaiResponse;
import com.example.book_be.danhgia.dto.DanhGiaHinhAnhCongKhaiResponse;
import com.example.book_be.danhgia.dto.DanhGiaTrangResponse;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.DanhGiaHuuIchRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** Kieu sap xep duy nhat khong bieu dien duoc bang {@link Sort} — no can mot JOIN. */
    private static final String SAP_XEP_HUU_ICH = "huu-ich";

    private final SuDanhGiaRepository suDanhGiaRepository;
    private final DanhGiaHuuIchRepository danhGiaHuuIchRepository;
    private final DanhGiaHinhAnhRepository danhGiaHinhAnhRepository;

    public DanhGiaDocService(SuDanhGiaRepository suDanhGiaRepository,
                             DanhGiaHuuIchRepository danhGiaHuuIchRepository,
                             DanhGiaHinhAnhRepository danhGiaHinhAnhRepository) {
        this.suDanhGiaRepository = suDanhGiaRepository;
        this.danhGiaHuuIchRepository = danhGiaHuuIchRepository;
        this.danhGiaHinhAnhRepository = danhGiaHinhAnhRepository;
    }

    @Transactional(readOnly = true)
    public DanhGiaTrangResponse docTrang(int maSach, Integer trang, Integer kichThuoc,
                                         String sapXep, Integer locSao,
                                         Integer maNguoiDungDangXem) {
        int soTrang = trang == null || trang < 0 ? 0 : trang;
        int cuaSo = chuanHoaKichThuoc(kichThuoc);

        Page<SuDanhGia> ketQua;
        if (SAP_XEP_HUU_ICH.equals(sapXep)) {
            // Thu tu do truy van quyet dinh (ORDER BY COUNT), nen Pageable o day khong
            // mang Sort — dua Sort vao se sinh ORDER BY thu hai de len tren.
            ketQua = suDanhGiaRepository.timTheoLuotHuuIch(maSach,
                    locSao == null ? null : (float) locSao, PageRequest.of(soTrang, cuaSo));
        } else {
            Pageable pageable = PageRequest.of(soTrang, cuaSo, sapXepTheo(sapXep));
            ketQua = locSao == null
                    ? suDanhGiaRepository.findBySach_MaSachAndTrangThai(
                            maSach, TrangThaiDanhGia.HIEN_THI, pageable)
                    : suDanhGiaRepository.findBySach_MaSachAndTrangThaiAndDiemXepHang(
                            maSach, TrangThaiDanhGia.HIEN_THI, locSao, pageable);
        }

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
                gopHuuIch(DanhGiaCongKhaiResponse.fromList(ketQua.getContent(), maNguoiDungDangXem),
                        maNguoiDungDangXem),
                ketQua.getNumber(),
                ketQua.getSize(),
                ketQua.getTotalPages(),
                tongSo,
                tongSo == 0 ? 0 : tongDiem / tongSo,
                phanBo);
    }

    /**
     * Bo sung so luot huu ich bang HAI truy van cho ca trang, khong phai hai truy van moi
     * dong. Day la ly do khong can cot dem san: chi phi la hang so theo so trang, va
     * khong sinh ra bat bien hai nguon su that nao phai canh.
     */
    private List<DanhGiaCongKhaiResponse> gopHuuIch(List<DanhGiaCongKhaiResponse> trang,
                                                    Integer maNguoiDungDangXem) {
        if (trang.isEmpty()) {
            return trang;
        }
        List<Long> danhSachMa = trang.stream().map(DanhGiaCongKhaiResponse::getMaDanhGia).toList();

        Map<Long, Long> soLuot = new HashMap<>();
        for (Object[] dong : danhGiaHuuIchRepository.demTheoDanhGia(danhSachMa)) {
            soLuot.put(((Number) dong[0]).longValue(), ((Number) dong[1]).longValue());
        }
        Set<Long> daBinhChon = maNguoiDungDangXem == null
                ? Set.of()
                : Set.copyOf(danhGiaHuuIchRepository.timDaBinhChon(maNguoiDungDangXem, danhSachMa));

        // Anh cung nap cho ca trang bang mot truy van, cung mot ly do.
        Map<Long, List<DanhGiaHinhAnhCongKhaiResponse>> anhTheoDanhGia = new HashMap<>();
        for (var anh : danhGiaHinhAnhRepository.findByMaDanhGiaInOrderByThuTuAsc(danhSachMa)) {
            anhTheoDanhGia.computeIfAbsent(anh.getMaDanhGia(), k -> new ArrayList<>())
                    .add(DanhGiaHinhAnhCongKhaiResponse.from(anh));
        }

        for (DanhGiaCongKhaiResponse dong : trang) {
            dong.setSoLuotHuuIch(soLuot.getOrDefault(dong.getMaDanhGia(), 0L));
            dong.setToiDaBinhChon(daBinhChon.contains(dong.getMaDanhGia()));
            dong.setAnhDinhKem(anhTheoDanhGia.getOrDefault(dong.getMaDanhGia(), List.of()));
        }
        return trang;
    }

    private int chuanHoaKichThuoc(Integer kichThuoc) {
        if (kichThuoc == null || kichThuoc < 1) {
            return KICH_THUOC_MAC_DINH;
        }
        return Math.min(kichThuoc, KICH_THUOC_TOI_DA);
    }

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
