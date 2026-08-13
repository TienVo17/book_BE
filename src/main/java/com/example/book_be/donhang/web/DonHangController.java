package com.example.book_be.donhang.web;

import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;
import com.example.book_be.donhang.dto.OrderDetailLineItemResponse;
import com.example.book_be.donhang.dto.OrderDetailResponse;
import com.example.book_be.donhang.dto.OrderListItemResponse;
import com.example.book_be.donhang.dto.VNPayUrlResponse;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.domain.TrangThaiThanhToan;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.thanhtoan.service.VNPayService;
import com.example.book_be.donhang.service.DonHangHuyService;
import com.example.book_be.donhang.service.DonHangTrangThaiService;
import com.example.book_be.donhang.service.OrderService;
import com.example.book_be.shared.dto.ThongBao;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("api/don-hang")
public class DonHangController {

    private static final String PAYMENT_METHOD_VNPAY = "VNPAY";

    /** Idempotency-Key: chieu dai toi da va allow-list ky tu an toan (khong dau/khoang trang/ky tu dieu khien). */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    @Autowired
    private OrderService orderService;

    @Autowired
    private DonHangTrangThaiService donHangTrangThaiService;

    @Autowired
    private DonHangHuyService donHangHuyService;

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @GetMapping("/findAll")
    public Page<OrderListItemResponse> findAll(@RequestParam("page") Integer page, HttpServletRequest request) {
        Pageable pageable = PageRequest.of(page, 10);
        NguoiDung nguoiDung = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getName() != null) {
            nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        }
        NguoiDung finalNguoiDung = nguoiDung;
        boolean isAdmin = isAdmin();
        Page<DonHang> donHangPage = donHangRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Admin thay moi don; user thuong chi thay don cua chinh minh.
            if (!isAdmin && finalNguoiDung != null) {
                predicates.add(builder.equal(root.get("nguoiDung").get("maNguoiDung"), finalNguoiDung.getMaNguoiDung()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        return donHangPage.map(this::toOrderListItemResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> findById(@PathVariable Long id) {
        NguoiDung currentUser = getCurrentUser();
        DonHang donHang = donHangRepository.findDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Đơn hàng không tồn tại."));
        if (!isAdmin()
                && (donHang.getNguoiDung() == null
                    || donHang.getNguoiDung().getMaNguoiDung() != currentUser.getMaNguoiDung())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền truy cập đơn hàng này.");
        }
        return ResponseEntity.ok(toOrderDetailResponse(donHang));
    }

    @PostMapping("/them")
    public ResponseEntity<?> add(@RequestBody CheckoutOrderRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        String idempotencyKey = resolveIdempotencyKey(idempotencyKeyHeader);
        CheckoutOrderResponse donHang = orderService.saveOrUpdate(request, idempotencyKey);
        return ResponseEntity.ok(donHang);
    }

    /**
     * Idempotency-Key la BAT BUOC cho checkout.
     *
     * Khong co key thi mot request bi gui lai — nguoi dung bam hai lan, trinh duyet retry, mang
     * chap chon — se tao them mot don hang that va tru kho lan nua. Client khong the tu sua sai
     * do sau khi da xay ra, nen server phai tu choi ngay tu dau thay vi chap nhan roi tao don trung.
     *
     * Header thieu/rong -> 400. Header vuot chieu dai hoac chua ky tu ngoai allow-list -> 400.
     */
    private String resolveIdempotencyKey(String idempotencyKeyHeader) {
        String trimmed = idempotencyKeyHeader == null ? "" : idempotencyKeyHeader.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thiếu Idempotency-Key cho yêu cầu đặt hàng.");
        }
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH || !IDEMPOTENCY_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key không hợp lệ.");
        }
        return trimmed;
    }

    @GetMapping("/submitOrder")
    public ResponseEntity<?> submidOrder(@RequestParam("maDonHang") Long maDonHang) {
        NguoiDung currentUser = getCurrentUser();
        DonHang donHang = donHangRepository.findById(maDonHang)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng không tồn tại."));
        if (donHang.getNguoiDung() == null
                || donHang.getNguoiDung().getMaNguoiDung() != currentUser.getMaNguoiDung()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền thanh toán đơn hàng này.");
        }
        if (!isVnPay(donHang)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng không dùng phương thức thanh toán VNPAY.");
        }
        if (TrangThaiThanhToan.from(donHang.getTrangThaiThanhToan()) == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng này đã được thanh toán.");
        }
        if (TrangThaiGiaoHang.from(donHang.getTrangThaiGiaoHang()) != TrangThaiGiaoHang.CHO_XU_LY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng không thể tiếp tục thanh toán ở trạng thái hiện tại.");
        }
        int soTienVnd;
        try {
            soTienVnd = VNPayService.toVnd(donHang.getTongTien());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        String paymentUrl = vnPayService.createOrder(soTienVnd, String.valueOf(donHang.getMaDonHang()));
        return ResponseEntity.ok(new VNPayUrlResponse(paymentUrl));
    }

    @GetMapping("/vnpay-payment")
    public String GetMapping(HttpServletRequest request, Model model) {
        int paymentStatus = vnPayService.orderReturn(request);
        if (paymentStatus != 1) {
            return "orderfail";
        }

        String orderInfo = request.getParameter("vnp_OrderInfo");
        String paymentTime = request.getParameter("vnp_PayDate");
        String transactionId = request.getParameter("vnp_TransactionNo");
        String totalPrice = request.getParameter("vnp_Amount");

        model.addAttribute("orderId", orderInfo);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("paymentTime", paymentTime);
        model.addAttribute("transactionId", transactionId);
        Long maDonHang = parsePositiveLong(orderInfo);
        Long soTienVnPay = parsePositiveLong(totalPrice);
        if (maDonHang == null || soTienVnPay == null) {
            return "orderfail";
        }
        // Service tai va khoa authoritative order truoc moi state/payment check.
        return donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(maDonHang, soTienVnPay);
    }

    @PostMapping("/cap-nhat-trang-thai-giao-hang/{maDonHang}")
    public ResponseEntity<?> submidOrder(@PathVariable Long maDonHang, HttpServletRequest request) {
        DonHang donHang = donHangRepository.findById(maDonHang)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Đơn hàng không tồn tại."));
        // Tien 1 buoc qua may trang thai (0->1->2). Quyen ADMIN da duoc chan o SecurityConfiguration.
        String nguoiThucHien = SecurityContextHolder.getContext().getAuthentication().getName();
        DonHang capNhat = donHangTrangThaiService.chuyenTrangThaiTiepTheo(donHang, nguoiThucHien);
        return ResponseEntity.ok(capNhat);
    }

    @PostMapping("/huy/{maDonHang}")
    public ResponseEntity<?> huyDon(@PathVariable Long maDonHang) {
        NguoiDung currentUser = getCurrentUser();
        donHangHuyService.huyDon(maDonHang, currentUser, isAdmin());
        return ResponseEntity.ok(new ThongBao("Hủy đơn hàng thành công"));
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }

    private NguoiDung getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        NguoiDung currentUser = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return currentUser;
    }

    private OrderListItemResponse toOrderListItemResponse(DonHang donHang) {
        String phuongThucThanhToan = null;
        String tenPhuongThucThanhToan = null;
        if (donHang.getHinhThucThanhToan() != null) {
            phuongThucThanhToan = donHang.getHinhThucThanhToan().getMaCode();
            tenPhuongThucThanhToan = donHang.getHinhThucThanhToan().getTenHinhThucGiaoHang();
        }
        return new OrderListItemResponse(
                donHang.getMaDonHang(),
                donHang.getNgayTao(),
                donHang.getDiaChiNhanHang(),
                phuongThucThanhToan,
                tenPhuongThucThanhToan,
                donHang.getTrangThaiThanhToan(),
                donHang.getTrangThaiGiaoHang(),
                donHang.getTongTien()
        );
    }

    private OrderDetailResponse toOrderDetailResponse(DonHang donHang) {
        List<OrderDetailLineItemResponse> lineItems = donHang.getDanhSachChiTietDonHang() == null
                ? List.of()
                : donHang.getDanhSachChiTietDonHang().stream()
                .sorted(Comparator.comparingInt(ChiTietDonHang::getMaChiTietDonHang))
                .map(line -> new OrderDetailLineItemResponse(
                        line.getSach().getMaSach(),
                        line.getSach().getTenSach(),
                        line.getSoLuong(),
                        line.getGiaBan(),
                        line.getGiaBan() * line.getSoLuong()
                ))
                .toList();

        String paymentCode = null;
        String paymentName = null;
        if (donHang.getHinhThucThanhToan() != null) {
            paymentCode = donHang.getHinhThucThanhToan().getMaCode();
            paymentName = donHang.getHinhThucThanhToan().getTenHinhThucGiaoHang();
        }
        String deliveryName = donHang.getHinhThucGiaoHang() == null
                ? null
                : donHang.getHinhThucGiaoHang().getTenHinhThucGiaoHang();
        double discount = Math.max(0, donHang.getTongTienSanPham()
                + donHang.getChiPhiGiaoHang()
                + donHang.getChiPhiThanhToan()
                - donHang.getTongTien());

        return new OrderDetailResponse(
                donHang.getMaDonHang(),
                donHang.getNgayTao(),
                donHang.getHoTen(),
                donHang.getSoDienThoai(),
                donHang.getDiaChiNhanHang(),
                donHang.getTrangThaiThanhToan(),
                donHang.getTrangThaiGiaoHang(),
                paymentCode,
                paymentName,
                deliveryName,
                donHang.getTongTienSanPham(),
                discount,
                donHang.getChiPhiGiaoHang(),
                donHang.getChiPhiThanhToan(),
                donHang.getTongTien(),
                lineItems
        );
    }

    private boolean isVnPay(DonHang donHang) {
        return donHang.getHinhThucThanhToan() != null
                && PAYMENT_METHOD_VNPAY.equalsIgnoreCase(donHang.getHinhThucThanhToan().getMaCode());
    }

    private Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }
}
