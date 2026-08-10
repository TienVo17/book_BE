package com.example.book_be.donhang.service;

import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.giamgia.repository.CouponRepository;
import com.example.book_be.nguoidung.repository.DiaChiGiaoHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.giohang.repository.GioHangRepository;
import com.example.book_be.donhang.repository.HinhThucGiaoHangRepository;
import com.example.book_be.donhang.domain.HinhThucGiaoHang;
import com.example.book_be.thanhtoan.repository.HinhThucThanhToanRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.giamgia.domain.Coupon;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.thanhtoan.domain.HinhThucThanhToan;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String PAYMENT_METHOD_COD = "COD";
    private static final String PAYMENT_METHOD_VNPAY = "VNPAY";

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private ChiTietDonHangRepository chiTietDonHangRepository;

    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private GioHangRepository gioHangRepository;

    @Autowired
    private DiaChiGiaoHangRepository diaChiGiaoHangRepository;

    @Autowired
    private HinhThucThanhToanRepository hinhThucThanhToanRepository;

    @Autowired
    private HinhThucGiaoHangRepository hinhThucGiaoHangRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request) {
        return saveOrUpdate(request, null);
    }

    /**
     * Idempotency: neu idempotencyKey duoc truyen (khong null/blank), thao tac claim/replay chay
     * TRUOC khi tru kho/dung coupon (khong mutation neu la replay hoac 409). Khi 2 request cung
     * key chay dong thoi va ca hai deu khong thay don da ton tai, unique constraint
     * (ma_nguoi_dung, checkout_idempotency_key) chan ban ghi thu 2 luc INSERT; ben thua
     * (DataIntegrityViolationException) rollback TOAN BO transaction cua no (hoan tra kho/coupon
     * da tru trong transaction do) roi doc lai don da commit boi ben thang de replay - khong bao
     * gio de lai kho/coupon bi tru "mo coi".
     */
    @Override
    public CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request, String idempotencyKeyRaw) {
        // Chi dung de xac thuc dang nhap + lay ten_dang_nhap/ma_nguoi_dung (gia tri nguyen thuy, an toan
        // mang qua transaction boundary). KHONG dung truc tiep entity nay de gan vao DonHang: no se
        // detached ngay khi request rieng cua findByTenDangNhap ket thuc, gay "detached entity passed
        // to persist" khi cascade PERSIST. NguoiDung managed phai duoc doc lai BEN TRONG transaction.
        NguoiDung currentUserSnapshot = getCurrentUser();
        validateCheckoutRequest(request);

        String idempotencyKey = (idempotencyKeyRaw == null || idempotencyKeyRaw.isBlank())
                ? null
                : idempotencyKeyRaw.trim();
        String fingerprint = idempotencyKey != null ? computeFingerprint(request) : null;
        String tenDangNhap = currentUserSnapshot.getTenDangNhap();
        int maNguoiDung = currentUserSnapshot.getMaNguoiDung();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            return transactionTemplate.execute(status -> executeCheckout(request, tenDangNhap, idempotencyKey, fingerprint));
        } catch (IdempotencyKeyRaceException race) {
            // Transaction cua ben thua da rollback het (kho/coupon da hoan). Doc lai trong transaction
            // MOI de thay du lieu ben thang vua commit, roi replay hoac 409 neu fingerprint lech.
            return new TransactionTemplate(transactionManager).execute(status -> donHangRepository
                    .findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(maNguoiDung, idempotencyKey)
                    .map(existing -> buildReplayOrConflict(existing, fingerprint))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Xung đột khi tạo đơn hàng, vui lòng thử lại.")));
        }
    }

    private CheckoutOrderResponse executeCheckout(CheckoutOrderRequest request, String tenDangNhap,
                                                    String idempotencyKey, String fingerprint) {
        // Doc lai NguoiDung BEN TRONG transaction nay de no la managed entity: DonHang.nguoiDung
        // cascade PERSIST, mot instance detached (doc ngoai transaction) se lam Hibernate nem
        // "detached entity passed to persist" khi luu don hang moi.
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng đăng nhập.");
        }

        if (idempotencyKey != null) {
            Optional<DonHang> existing = donHangRepository
                    .findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(nguoiDung.getMaNguoiDung(), idempotencyKey);
            if (existing.isPresent()) {
                return buildReplayOrConflict(existing.get(), fingerprint);
            }
        }

        DiaChiGiaoHang diaChiGiaoHang = diaChiGiaoHangRepository.findById(Long.valueOf(request.getMaDiaChiGiaoHang()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không tồn tại."));
        if (diaChiGiaoHang.getNguoiDung() == null
                || diaChiGiaoHang.getNguoiDung().getMaNguoiDung() != nguoiDung.getMaNguoiDung()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền sử dụng địa chỉ giao hàng này.");
        }

        HinhThucThanhToan hinhThucThanhToan = resolvePaymentMethod(request.getPhuongThucThanhToan());

        DonHang donHang = new DonHang();
        donHang.setNgayTao(new Date());
        donHang.setNguoiDung(nguoiDung);
        donHang.setDiaChiMuaHang(nguoiDung.getDiaChiMuaHang());
        donHang.setDiaChiNhanHang(diaChiGiaoHang.getDiaChiDayDu());
        donHang.setHoTen(diaChiGiaoHang.getHoTen());
        donHang.setSoDienThoai(diaChiGiaoHang.getSoDienThoai());
        donHang.setTrangThaiThanhToan(0);
        donHang.setTrangThaiGiaoHang(0);
        donHang.setHinhThucThanhToan(hinhThucThanhToan);
        donHang.setChiPhiThanhToan(hinhThucThanhToan.getChiPhiGiaoHang());

        // Truoc day dong nay ghi cung 0 nen moi don deu mien phi ship, du bang
        // hinh_thuc_giao_hang da co san bieu phi va tongTien ben duoi da cong chiPhiGiaoHang.
        // Ca duong ong deu du, chi thieu dung buoc gan gia tri.
        HinhThucGiaoHang hinhThucGiaoHang = resolveDeliveryMethod(request.getMaHinhThucGiaoHang());
        donHang.setHinhThucGiaoHang(hinhThucGiaoHang);
        donHang.setChiPhiGiaoHang(hinhThucGiaoHang == null ? 0 : hinhThucGiaoHang.getChiPhiGiaoHang());

        // TreeMap: lap theo maSach tang dan de moi transaction khoa row Sach cung thu tu -> tranh deadlock.
        Map<Integer, Integer> soLuongTheoSach = gomSoLuongTheoSach(request.getItems());
        double tongTienSanPham = 0;
        for (Map.Entry<Integer, Integer> entry : soLuongTheoSach.entrySet()) {
            Sach db = sachRepository.findById(Long.valueOf(entry.getKey()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách không tồn tại."));
            // Tru kho nguyen tu: 0 ban ghi = het hang -> throw -> rollback ca transaction (cac sach da tru truoc do hoan tu dong).
            if (sachRepository.truKhoNeuDu(db.getMaSach(), entry.getValue()) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Sách " + db.getTenSach() + " không đủ tồn kho.");
            }
            tongTienSanPham += entry.getValue() * db.getGiaBan();
        }

        Coupon coupon = resolveCoupon(request.getMaCoupon());
        double soTienGiam = tinhSoTienGiam(coupon, tongTienSanPham);
        double tongTien = tongTienSanPham - soTienGiam + donHang.getChiPhiGiaoHang() + donHang.getChiPhiThanhToan();

        donHang.setTongTienSanPham(tongTienSanPham);
        donHang.setTongTien(tongTien);
        if (coupon != null) {
            donHang.setMaCoupon(coupon.getMaCoupon());
        }
        if (idempotencyKey != null) {
            donHang.setCheckoutIdempotencyKey(idempotencyKey);
            donHang.setCheckoutRequestFingerprint(fingerprint);
            donHang.setCheckoutResponseCouponCode(coupon != null ? coupon.getMa() : null);
            donHang.setCheckoutResponsePaymentMethod(normalizePaymentMethodCode(hinhThucThanhToan));
            donHang.setCheckoutResponsePaymentStatus(donHang.getTrangThaiThanhToan());
        }
        try {
            donHangRepository.save(donHang);
        } catch (DataIntegrityViolationException e) {
            // Race: request khac (cung user+key) vua insert truoc va commit/dang commit.
            throw new IdempotencyKeyRaceException();
        }

        for (Map.Entry<Integer, Integer> entry : soLuongTheoSach.entrySet()) {
            Sach db = sachRepository.findById(Long.valueOf(entry.getKey()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách không tồn tại."));
            ChiTietDonHang chiTietDonHang = new ChiTietDonHang();
            chiTietDonHang.setDonHang(donHang);
            chiTietDonHang.setSach(db);
            chiTietDonHang.setSoLuong(entry.getValue());
            chiTietDonHang.setGiaBan(db.getGiaBan());
            chiTietDonHang.setDanhGia(true);
            chiTietDonHangRepository.save(chiTietDonHang);
            gioHangRepository.deleteByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), db.getMaSach());
        }

        if (coupon != null) {
            int soBanGhiCapNhat = couponRepository.tangLuotSuDungNeuConHieuLuc(coupon.getMaCoupon());
            if (soBanGhiCapNhat == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã hết lượt sử dụng.");
            }
        }

        return new CheckoutOrderResponse(
                donHang.getMaDonHang(),
                donHang.getTongTien(),
                donHang.getTongTienSanPham(),
                soTienGiam,
                donHang.getChiPhiGiaoHang(),
                hinhThucGiaoHang == null ? null : hinhThucGiaoHang.getTenHinhThucGiaoHang(),
                coupon != null ? coupon.getMa() : null,
                normalizePaymentMethodCode(hinhThucThanhToan),
                donHang.getTrangThaiThanhToan(),
                donHang.getHoTen(),
                donHang.getSoDienThoai(),
                donHang.getDiaChiNhanHang()
        );
    }

    /** Fingerprint khop -> replay nguyen response da commit (khong mutation). Lech -> 409, khong mutation. */
    private CheckoutOrderResponse buildReplayOrConflict(DonHang existing, String fingerprint) {
        String storedFingerprint = existing.getCheckoutRequestFingerprint();
        if (storedFingerprint == null || !storedFingerprint.equals(fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key đã được dùng cho một yêu cầu đặt hàng khác.");
        }
        return toReplayResponse(existing);
    }

    private CheckoutOrderResponse toReplayResponse(DonHang donHang) {
        double soTienGiam = donHang.getTongTienSanPham() + donHang.getChiPhiGiaoHang()
                + donHang.getChiPhiThanhToan() - donHang.getTongTien();
        return new CheckoutOrderResponse(
                donHang.getMaDonHang(),
                donHang.getTongTien(),
                donHang.getTongTienSanPham(),
                soTienGiam,
                donHang.getChiPhiGiaoHang(),
                donHang.getHinhThucGiaoHang() == null
                        ? null : donHang.getHinhThucGiaoHang().getTenHinhThucGiaoHang(),
                donHang.getCheckoutResponseCouponCode(),
                donHang.getCheckoutResponsePaymentMethod(),
                donHang.getCheckoutResponsePaymentStatus(),
                donHang.getHoTen(),
                donHang.getSoDienThoai(),
                donHang.getDiaChiNhanHang()
        );
    }

    /**
     * Hash on dinh tren request DA NORMALIZE: sach sap xep tang dan theo maSach voi so luong da
     * gop (khong phu thuoc thu tu items trong JSON goc), dia chi, phuong thuc thanh toan, coupon,
     * hinh thuc giao hang.
     *
     * <p>Hinh thuc giao hang PHAI nam trong hash: no doi tong tien. Bo qua no thi khach doi tu
     * "tu lay tai cua hang" sang "giao tan noi" roi dat lai se bi coi la dat trung va nhan ve
     * chinh don cu voi phi cu.
     */
    private String computeFingerprint(CheckoutOrderRequest request) {
        Map<Integer, Integer> normalizedItems = gomSoLuongTheoSach(request.getItems());
        StringBuilder canonical = new StringBuilder("items=");
        normalizedItems.forEach((maSach, soLuong) -> canonical.append(maSach).append(':').append(soLuong).append(';'));
        canonical.append("|address=").append(request.getMaDiaChiGiaoHang());
        canonical.append("|payment=").append(request.getPhuongThucThanhToan() == null
                ? "" : request.getPhuongThucThanhToan().trim().toUpperCase());
        canonical.append("|coupon=").append(request.getMaCoupon() == null || request.getMaCoupon().isBlank()
                ? "" : request.getMaCoupon().trim().toUpperCase());
        canonical.append("|delivery=").append(request.getMaHinhThucGiaoHang() == null
                ? "" : request.getMaHinhThucGiaoHang());
        return sha256Hex(canonical.toString());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String byteHex = Integer.toHexString(0xff & b);
                if (byteHex.length() == 1) {
                    hex.append('0');
                }
                hex.append(byteHex);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng trong JVM này.", e);
        }
    }

    private void validateCheckoutRequest(CheckoutOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách sản phẩm không được để trống.");
        }
        if (request.getMaDiaChiGiaoHang() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn địa chỉ giao hàng.");
        }
        if (request.getPhuongThucThanhToan() == null || request.getPhuongThucThanhToan().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn phương thức thanh toán.");
        }
    }

    private NguoiDung getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng đăng nhập.");
        }
        return nguoiDung;
    }

    private Map<Integer, Integer> gomSoLuongTheoSach(List<CartItemRequest> items) {
        // TreeMap giu thu tu maSach tang dan -> khoa row Sach nhat quan giua cac transaction (chong deadlock).
        Map<Integer, Long> soLuongTongTheoSach = new TreeMap<>();
        for (CartItemRequest item : items) {
            if (item.getMaSach() == null || item.getSoLuong() == null || item.getSoLuong() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin sản phẩm đặt hàng không hợp lệ.");
            }
            long total = soLuongTongTheoSach.getOrDefault(item.getMaSach(), 0L) + item.getSoLuong().longValue();
            if (total > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng sản phẩm đặt hàng vượt giới hạn cho phép.");
            }
            soLuongTongTheoSach.put(item.getMaSach(), total);
        }

        Map<Integer, Integer> soLuongTheoSach = new TreeMap<>();
        soLuongTongTheoSach.forEach((maSach, soLuong) -> soLuongTheoSach.put(maSach, soLuong.intValue()));
        return soLuongTheoSach;
    }

    private Coupon resolveCoupon(String maCoupon) {
        if (maCoupon == null || maCoupon.isBlank()) {
            return null;
        }

        Coupon coupon = couponRepository.findByMa(maCoupon.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không tồn tại."));

        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không còn hiệu lực.");
        }
        if (coupon.getHanSuDung() != null && coupon.getHanSuDung().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã hết hạn.");
        }
        if (coupon.getSoLuongToiDa() > 0 && coupon.getDaSuDung() >= coupon.getSoLuongToiDa()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã hết lượt sử dụng.");
        }

        return coupon;
    }

    /**
     * {@code null} khi client khong gui — giu nguyen hanh vi mien phi ship cu thay vi mac dinh
     * mot hinh thuc co phi, de khong ai bi tinh them tien cho lua chon chua tung nhin thay.
     * Ma khong ton tai thi bao 400 chu khong im lang bo qua: im lang se cho ra mot don co gia
     * khac han cai khach vua thay tren man hinh.
     */
    private HinhThucGiaoHang resolveDeliveryMethod(Integer maHinhThucGiaoHang) {
        if (maHinhThucGiaoHang == null) {
            return null;
        }
        return hinhThucGiaoHangRepository.findById(Long.valueOf(maHinhThucGiaoHang))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Hình thức giao hàng không tồn tại."));
    }

    private double tinhSoTienGiam(Coupon coupon, double tongTienSanPham) {
        if (coupon == null) {
            return 0;
        }
        if (tongTienSanPham < coupon.getGiaTriToiThieu()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng chưa đạt giá trị tối thiểu " + coupon.getGiaTriToiThieu());
        }

        double soTienGiam = coupon.getGiaTriGiam();
        if (coupon.getLoai() != null && coupon.getLoai().name().equals("PERCENT")) {
            soTienGiam = tongTienSanPham * coupon.getGiaTriGiam() / 100.0;
        }
        return Math.min(soTienGiam, tongTienSanPham);
    }

    private HinhThucThanhToan resolvePaymentMethod(String paymentMethodCode) {
        String normalizedCode = paymentMethodCode.trim().toUpperCase();
        if (!PAYMENT_METHOD_COD.equals(normalizedCode) && !PAYMENT_METHOD_VNPAY.equals(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán không hợp lệ.");
        }

        return hinhThucThanhToanRepository.findByMaCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy cấu hình phương thức thanh toán."));
    }

    private String normalizePaymentMethodCode(HinhThucThanhToan hinhThucThanhToan) {
        if (hinhThucThanhToan == null || hinhThucThanhToan.getMaCode() == null || hinhThucThanhToan.getMaCode().isBlank()) {
            return PAYMENT_METHOD_COD;
        }
        return hinhThucThanhToan.getMaCode().trim().toUpperCase();
    }

    /** Signal noi bo: insert va cham unique (ma_nguoi_dung, checkout_idempotency_key). Khong bao gio leak ra ngoai service. */
    private static final class IdempotencyKeyRaceException extends RuntimeException {
        IdempotencyKeyRaceException() {
            super(null, null, false, false);
        }
    }
}
