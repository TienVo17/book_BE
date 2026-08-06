package com.example.book_be.danhgia.service;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.danhgia.repository.DanhGiaAnTombstoneRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.danhgia.domain.DanhGiaAnTombstone;
import com.example.book_be.danhgia.domain.LyDoKhongDanhGiaDuoc;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.dto.CoTheDanhGiaResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;

/**
 * Moi thao tac doi danh gia deu chay trong transaction va tinh lai du lieu tong hop
 * cua sach truoc khi ket thuc.
 *
 * <p>Truoc day lop nay khong co mot annotation {@code @Transactional} nao. Moi
 * {@code save}/{@code delete} tu mo transaction rieng cua SimpleJpaRepository va commit
 * doc lap, nen khong co ranh gioi nao de gom thao tac danh gia voi buoc tinh lai diem.
 * Cau UPDATE tinh lai cung phai chay sau flush, xem
 * {@code SuDanhGiaRepository.capNhatTongHopChoSach}.
 */
@Service
public class DanhGiaServiceImpl implements DanhGiaService {
    /** Ten rang buoc duy nhat tao trong V11; xem laViPhamTrungDanhGia. */
    private static final String TEN_RANG_BUOC_TRUNG_DANH_GIA = "uk_danhgia_nguoi_sach";

    @Autowired
    NguoiDungRepository nguoiDungRepository;
    @Autowired
    SuDanhGiaRepository suDanhGiaRepository;
    @Autowired
    private SachRepository sachRepository;
    @Autowired
    private DonHangRepository donHangRepository;
    @Autowired
    private DanhGiaAnTombstoneRepository tombstoneRepository;

    /**
     * Che do Chat: chi nguoi co don DA_GIAO chua cuon sach do moi danh gia duoc.
     *
     * <p>Thu tu kiem tra khong tuy tien. Tombstone dung truoc vi no la ket cuoi — noi
     * "chua nhan hang" cho nguoi da bi an bai la sai va con mach nuoc cho ho mua lai de
     * mo khoa. "Da danh gia" dung truoc dieu kien mua vi no la tinh trang cu the hon.
     */
    @Override
    @Transactional(readOnly = true)
    public CoTheDanhGiaResponse kiemTraCoTheDanhGia(int maNguoiDung, int maSach) {
        if (tombstoneRepository.existsByMaNguoiDungAndMaSach(maNguoiDung, maSach)) {
            return CoTheDanhGiaResponse.khong(LyDoKhongDanhGiaDuoc.DA_BI_AN);
        }
        if (suDanhGiaRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(maNguoiDung, maSach)) {
            return CoTheDanhGiaResponse.khong(LyDoKhongDanhGiaDuoc.DA_DANH_GIA);
        }
        Integer maDonHang = donHangRepository.timDonDaGiaoChoSach(maNguoiDung, maSach);
        if (maDonHang != null) {
            return CoTheDanhGiaResponse.duoc(maDonHang);
        }
        // Phan biet "chua mua bao gio" voi "da mua nhung hang chua toi": hai tinh huong
        // nay doi hai hanh dong khac han o phia nguoi dung.
        return donHangRepository.demDonChuaSach(maNguoiDung, maSach) > 0
                ? CoTheDanhGiaResponse.khong(LyDoKhongDanhGiaDuoc.CHUA_NHAN_HANG)
                : CoTheDanhGiaResponse.khong(LyDoKhongDanhGiaDuoc.CHUA_MUA);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach) {
        // Chiem khoa dong sach TRUOC khi cham danhgia — xem khoaSachDeCapNhat.
        Sach sach = sachRepository.khoaSachDeCapNhat(maSach.intValue()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sách."));

        // Kiem tra lai o day chu khong tin ket qua client da doc tu co-the-danh-gia:
        // endpoint kia la tien ich hien thi, day moi la cho chan.
        CoTheDanhGiaResponse quyen = kiemTraCoTheDanhGia(maNguoiDung.intValue(), maSach.intValue());
        if (!quyen.isCoThe()) {
            throw khongDuQuyen(quyen.getLyDo());
        }

        SuDanhGia suDanhGia = new SuDanhGia();
        suDanhGia.setMaDonHang(quyen.getMaDonHang());
        suDanhGia.setNhanXet(nhanXet);
        suDanhGia.setDiemXepHang(diemXepHang);
        suDanhGia.setTimestamp(new Timestamp(System.currentTimeMillis()));
        suDanhGia.datTrangThai(TrangThaiDanhGia.HIEN_THI);
        suDanhGia.setNguoiDung(nguoiDungRepository.findById(maNguoiDung).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.")));
        suDanhGia.setSach(sach);

        SuDanhGia daLuu;
        try {
            daLuu = suDanhGiaRepository.saveAndFlush(suDanhGia);
        } catch (DataIntegrityViolationException viPham) {
            // Chi nhan dung rang buoc trung danh gia. Bat DataIntegrityViolationException
            // chung chung se bao "ban da danh gia roi" cho ca vi pham khoa ngoai hay NOT NULL,
            // tuc la noi sai nguyen nhan cho nguoi dung va cho ca nguoi doc log.
            if (laViPhamTrungDanhGia(viPham)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bạn đã đánh giá cuốn sách này rồi.", viPham);
            }
            throw viPham;
        }
        tinhLaiTongHop(sach.getMaSach());
        return daLuu;
    }

    /** Chi chu so huu duoc sua noi dung danh gia cua chinh minh. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia, Long maNguoiDungYeuCau) {
        // timKemNguoiDung chu khong phai findById: response tra ve doc ten nguoi dung de
        // che, ma luc do entity da roi transaction.
        SuDanhGia db = suDanhGiaRepository.timKemNguoiDung(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        kiemTraChuSoHuu(db, maNguoiDungYeuCau);
        sachRepository.khoaSachDeCapNhat(db.getSach().getMaSach());

        if (danhGia.getDiemXepHang() < 1 || danhGia.getDiemXepHang() > 5
                || danhGia.getNhanXet() == null || danhGia.getNhanXet().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đánh giá không hợp lệ.");
        }

        // Sua noi dung KHONG duoc doi trang thai kiem duyet: neu khong, sua bai tro thanh
        // cach tu bo an ma chang can xoa roi dang lai.
        db.setDiemXepHang(danhGia.getDiemXepHang());
        db.setNhanXet(danhGia.getNhanXet());
        db.setTimestamp(new Timestamp(System.currentTimeMillis()));
        SuDanhGia daLuu = suDanhGiaRepository.save(db);

        tinhLaiTongHop(db.getSach().getMaSach());
        return daLuu;
    }

    /** Chu so huu tu xoa danh gia cua minh; ADMIN xoa duoc de kiem duyet noi dung. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SuDanhGia deleteReview(Long maDanhGia, Long maNguoiDungYeuCau, boolean laQuanTri) {
        SuDanhGia db = suDanhGiaRepository.timKemNguoiDung(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        if (!laQuanTri) {
            kiemTraChuSoHuu(db, maNguoiDungYeuCau);
        }
        int maSach = db.getSach().getMaSach();
        sachRepository.khoaSachDeCapNhat(maSach);
        suDanhGiaRepository.delete(db);

        tinhLaiTongHop(maSach);
        return db;
    }

    /**
     * Admin an hoac hien lai danh gia. Day la hai duong ghi tong hop truoc kia di thang
     * repository tu controller, nen khong co transaction va khong tinh lai diem —
     * dung hai thao tac ma du lieu tong hop duoc dinh nghia tren do.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SuDanhGia doiTrangThai(Long maDanhGia, TrangThaiDanhGia trangThaiMoi) {
        SuDanhGia db = suDanhGiaRepository.timKemNguoiDung(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        sachRepository.khoaSachDeCapNhat(db.getSach().getMaSach());
        db.datTrangThai(trangThaiMoi);
        if (trangThaiMoi == TrangThaiDanhGia.DA_AN) {
            ghiTombstone(db);
        }
        SuDanhGia daLuu = suDanhGiaRepository.save(db);

        tinhLaiTongHop(db.getSach().getMaSach());
        return daLuu;
    }

    /** Duong sua chua khi du lieu tong hop da lech khoi thuc te. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int tinhLaiTongHopTatCa() {
        return suDanhGiaRepository.capNhatTongHopTatCa();
    }

    private void tinhLaiTongHop(int maSach) {
        suDanhGiaRepository.capNhatTongHopChoSach(maSach);
    }

    /**
     * Nhan dien dung rang buoc {@code uk_danhgia_nguoi_sach}. Ten rang buoc nam trong
     * message cua nguyen nhan goc (SQLIntegrityConstraintViolationException cua MySQL),
     * khong nam trong message cua wrapper Spring, nen phai duyet het chuoi cause.
     */
    private boolean laViPhamTrungDanhGia(DataIntegrityViolationException viPham) {
        for (Throwable nguyenNhan = viPham; nguyenNhan != null; nguyenNhan = nguyenNhan.getCause()) {
            String thongDiep = nguyenNhan.getMessage();
            if (thongDiep != null && thongDiep.contains(TEN_RANG_BUOC_TRUNG_DANH_GIA)) {
                return true;
            }
            if (nguyenNhan.getCause() == nguyenNhan) {
                break;
            }
        }
        return false;
    }

    /**
     * Dau vet phai song sot qua buoc chu so huu tu xoa bai — do la ly do no khong nam
     * tren chinh dong danh gia. Idempotent: an roi hien roi an lai khong tao dong thu hai.
     */
    private void ghiTombstone(SuDanhGia danhGia) {
        int maNguoiDung = danhGia.getNguoiDung().getMaNguoiDung();
        int maSach = danhGia.getSach().getMaSach();
        if (tombstoneRepository.existsByMaNguoiDungAndMaSach(maNguoiDung, maSach)) {
            return;
        }
        DanhGiaAnTombstone tombstone = new DanhGiaAnTombstone();
        tombstone.setMaNguoiDung(maNguoiDung);
        tombstone.setMaSach(maSach);
        tombstone.setTaoLuc(new Timestamp(System.currentTimeMillis()));
        tombstoneRepository.save(tombstone);
    }

    /**
     * `DA_DANH_GIA` la 409 vi yeu cau xung dot voi trang thai hien tai; ba ly do con lai
     * la 403 vi nguoi goi khong du dieu kien, du yeu cau hoan toan hop le.
     */
    private ResponseStatusException khongDuQuyen(LyDoKhongDanhGiaDuoc lyDo) {
        return switch (lyDo) {
            case DA_DANH_GIA -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bạn đã đánh giá cuốn sách này rồi.");
            case DA_BI_AN -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Đánh giá của bạn cho cuốn sách này đã bị ẩn, không thể đăng lại.");
            case CHUA_NHAN_HANG -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ đánh giá được sau khi đã nhận hàng.");
            case CHUA_MUA -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn cần mua và nhận cuốn sách này trước khi đánh giá.");
        };
    }

    private void kiemTraChuSoHuu(SuDanhGia danhGia, Long maNguoiDungYeuCau) {
        NguoiDung chuSoHuu = danhGia.getNguoiDung();
        if (chuSoHuu == null || maNguoiDungYeuCau == null
                || chuSoHuu.getMaNguoiDung() != maNguoiDungYeuCau.intValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền với đánh giá này.");
        }
    }
}
