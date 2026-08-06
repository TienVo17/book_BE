package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.web.ReviewImageValidationException;
import com.example.book_be.sach.service.CloudinaryService;
import com.example.book_be.sach.service.CloudinaryUploadResult;
import com.example.book_be.shared.security.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Anh dinh kem cua danh gia — be mat rui ro nhat cua ca tinh nang: lan dau tien nguoi
 * dung THUONG duoc day byte len mot dich vu ngoai.
 *
 * <p>Ba lop giu chan, khong lop nao thay duoc lop kia:
 * so anh moi danh gia, han ngach tron doi moi nguoi, va gioi han tan suat.
 */
@Service
public class DanhGiaHinhAnhService {

    /** Toi da moi danh gia. Day KHONG phai han ngach — xoa roi tai lai van duoc. */
    public static final int SO_ANH_TOI_DA = 5;
    /** Rieng cho anh danh gia; chat hon nguong 10MB cua container. */
    public static final long KICH_THUOC_TOI_DA_BYTES = 5L * 1024 * 1024;
    /** Han ngach tron doi. Khong bi buoc xoa lam giam — do la toan bo cong dung cua no. */
    public static final int HAN_NGACH_TRON_DOI = 50;
    private static final int SO_LAN_TAI_TOI_DA = 20;
    public static final int DO_DAI_KHOA_TOI_DA = 100;
    private static final String KHOA_HOP_LE = "^[A-Za-z0-9._-]+$";
    private static final Duration CUA_SO_TAI = Duration.ofMinutes(10);

    private final DanhGiaHinhAnhRepository hinhAnhRepository;
    private final SuDanhGiaRepository suDanhGiaRepository;
    private final CloudinaryService cloudinaryService;
    private final RateLimiter rateLimiter;

    public DanhGiaHinhAnhService(DanhGiaHinhAnhRepository hinhAnhRepository,
                                 SuDanhGiaRepository suDanhGiaRepository,
                                 CloudinaryService cloudinaryService,
                                 RateLimiter rateLimiter) {
        this.hinhAnhRepository = hinhAnhRepository;
        this.suDanhGiaRepository = suDanhGiaRepository;
        this.cloudinaryService = cloudinaryService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Thu tu kiem tra la co y: moi thu re duoc lam TRUOC {@code getBytes()}.
     *
     * <p>{@code CloudinaryService} nap toan bo tep vao heap. Mot tep bi tu choi cung ton
     * y het mot tep duoc nhan neu ta doc no truoc roi moi kiem tra — vai request 50MB
     * dong thoi tren instance nho la OOM, khong phai phan hoi cham.
     */
    @Transactional(rollbackFor = Exception.class)
    public DanhGiaHinhAnh themAnh(long maDanhGia, int maNguoiDung,
                                 String idempotencyKey, MultipartFile tep) throws IOException {
        String khoa = chuanHoaIdempotencyKey(idempotencyKey);
        if (!rateLimiter.choPhep("review-image:" + maNguoiDung, SO_LAN_TAI_TOI_DA, CUA_SO_TAI)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đang tải ảnh quá nhanh, vui lòng thử lại sau.");
        }

        // Khoa review de hai upload dong thoi khong cung doc count=4 roi chen thanh 6 anh.
        // Cung khoa nay cung bien hai request dong thoi co cung idempotency key thanh tuan tu.
        SuDanhGia danhGia = suDanhGiaRepository.khoaDeThemAnh(maDanhGia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
        // Chan o service, khong phai o giao dien: an nut khong ngan mot request thang API.
        if (danhGia.getMaNguoiDung() == null || danhGia.getMaNguoiDung() != maNguoiDung) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền với đánh giá này.");
        }

        if (tep == null || tep.isEmpty()) {
            throw new ReviewImageValidationException(ReviewImageValidationException.Ma.REVIEW_IMAGE_EMPTY,
                    "Tệp ảnh rỗng.");
        }
        if (tep.getSize() > KICH_THUOC_TOI_DA_BYTES) {
            throw new ReviewImageValidationException(ReviewImageValidationException.Ma.REVIEW_IMAGE_TOO_LARGE,
                    "Mỗi ảnh tối đa 5MB.");
        }

        byte[] noiDung = tep.getBytes();
        String noiDungSha256 = sha256(noiDung);
        Optional<DanhGiaHinhAnh> daXuLy = hinhAnhRepository
                .findByMaDanhGiaAndIdempotencyKey(maDanhGia, khoa);
        if (daXuLy.isPresent()) {
            if (!noiDungSha256.equals(daXuLy.get().getNoiDungSha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key đã được dùng cho một tệp khác.");
            }
            return daXuLy.get();
        }

        if (hinhAnhRepository.countByMaDanhGia(maDanhGia) >= SO_ANH_TOI_DA) {
            throw new ReviewImageValidationException(ReviewImageValidationException.Ma.REVIEW_IMAGE_TOO_MANY,
                    "Mỗi đánh giá tối đa " + SO_ANH_TOI_DA + " ảnh.");
        }
        if (hinhAnhRepository.tangHanNgachNeuCon(maNguoiDung, HAN_NGACH_TRON_DOI) == 0) {
            throw new ReviewImageValidationException(ReviewImageValidationException.Ma.REVIEW_IMAGE_QUOTA_EXCEEDED,
                    "Bạn đã dùng hết hạn mức ảnh đánh giá.");
        }

        CloudinaryUploadResult ketQua;
        try {
            ketQua = cloudinaryService.upload(
                    noiDung,
                    tep.getContentType(),
                    tep.getOriginalFilename(),
                    CloudinaryService.REVIEW_IMAGE_FOLDER);
        } catch (IllegalArgumentException noiDungKhongHopLe) {
            // CloudinaryService kiem tra noi dung that; doi sang ma rieng de giao dien
            // noi duoc dung van de thay vi mot "VALIDATION_ERROR" chung chung.
            throw new ReviewImageValidationException(
                    ReviewImageValidationException.Ma.REVIEW_IMAGE_UNSUPPORTED_TYPE,
                    "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.");
        }

        DanhGiaHinhAnh anh = new DanhGiaHinhAnh();
        anh.setMaDanhGia(maDanhGia);
        anh.setUrlHinh(ketQua.secureUrl());
        anh.setCloudinaryPublicId(ketQua.publicId());
        anh.setIdempotencyKey(khoa);
        anh.setNoiDungSha256(noiDungSha256);
        anh.setThuTu((int) hinhAnhRepository.countByMaDanhGia(maDanhGia));
        anh.setTaoLuc(new Timestamp(System.currentTimeMillis()));
        try {
            return hinhAnhRepository.saveAndFlush(anh);
        } catch (RuntimeException loiDatabase) {
            // Upload da thanh cong nhung DB khong ghi duoc: don ngay doi tuong ngoai.
            // Neu don cung loi, giu no lam suppressed de khong danh mat nguyen nhan DB.
            try {
                cloudinaryService.deleteByPublicId(ketQua.publicId());
            } catch (Exception loiDonAnh) {
                loiDatabase.addSuppressed(loiDonAnh);
            }
            throw loiDatabase;
        }
    }

    private String chuanHoaIdempotencyKey(String idempotencyKey) {
        String khoa = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (khoa.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thiếu Idempotency-Key cho yêu cầu tải ảnh.");
        }
        if (khoa.length() > DO_DAI_KHOA_TOI_DA || !khoa.matches(KHOA_HOP_LE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key không hợp lệ.");
        }
        return khoa;
    }

    private String sha256(byte[] noiDung) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(noiDung));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng trong JVM này.", e);
        }
    }

    /** Chu so huu go anh cua minh; ADMIN go anh vi pham. */
    @Transactional(rollbackFor = Exception.class)
    public void xoaAnh(long maHinhAnh, int maNguoiDung, boolean laQuanTri) throws IOException {
        DanhGiaHinhAnh anh = hinhAnhRepository.findById(maHinhAnh)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh."));
        if (!laQuanTri) {
            SuDanhGia danhGia = suDanhGiaRepository.findById(anh.getMaDanhGia())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá."));
            if (danhGia.getMaNguoiDung() == null || danhGia.getMaNguoiDung() != maNguoiDung) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền với ảnh này.");
            }
        }
        // Xoa ben Cloudinary TRUOC khi xoa dong: neu xoa dong truoc va buoc kia loi, ta
        // vua mat luon tay cam duy nhat de tim ra doi tuong con lai.
        cloudinaryService.deleteByPublicId(anh.getCloudinaryPublicId());
        hinhAnhRepository.delete(anh);
    }

    /**
     * Don anh TUONG MINH truoc khi xoa mot danh gia.
     *
     * <p>Cascade cua database khong chay mot dong Java nao: MySQL xoa dong
     * {@code danhgia_hinh_anh} cung voi {@code cloudinary_public_id}, va anh nam lai
     * Cloudinary vinh vien — con ma de don chung thi vua bi chinh cau DELETE do huy.
     * Voi anh bi go vi vi pham, day la van de tuan thu chu khong chi la hoa don.
     */
    @Transactional(rollbackFor = Exception.class)
    public int donAnhCuaDanhGia(long maDanhGia) throws IOException {
        List<DanhGiaHinhAnh> danhSach = hinhAnhRepository.findByMaDanhGiaOrderByThuTuAsc(maDanhGia);
        for (DanhGiaHinhAnh anh : danhSach) {
            cloudinaryService.deleteByPublicId(anh.getCloudinaryPublicId());
        }
        hinhAnhRepository.deleteAll(danhSach);
        return danhSach.size();
    }
}
