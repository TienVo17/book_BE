package com.example.book_be.controller;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dto.cart.CheckoutOrderRequest;
import com.example.book_be.dto.cart.CheckoutOrderResponse;
import com.example.book_be.dto.cart.OrderListItemResponse;
import com.example.book_be.dto.cart.VNPayUrlResponse;
import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.services.VNPayService;
import com.example.book_be.services.cart.OrderService;
import com.example.book_be.services.email.EmailService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api/don-hang")
public class DonHangController {

    private static final String PAYMENT_METHOD_COD = "COD";

    @Autowired
    private OrderService orderService;

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private ChiTietDonHangRepository chiTietDonHangRepository;

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
        Page<DonHang> donHangPage = donHangRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean hasUserScope = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()) || "USER".equals(authority.getAuthority()));
            if (hasUserScope && finalNguoiDung != null) {
                predicates.add(builder.equal(root.get("nguoiDung").get("maNguoiDung"), finalNguoiDung.getMaNguoiDung()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        return donHangPage.map(this::toOrderListItemResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        NguoiDung currentUser = getCurrentUser();
        DonHang donHang = donHangRepository.findById(id).orElse(null);
        if (donHang == null) {
            return ResponseEntity.notFound().build();
        }
        if (donHang.getNguoiDung() == null
                || donHang.getNguoiDung().getMaNguoiDung() != currentUser.getMaNguoiDung()) {
            return ResponseEntity.status(403).body("Không có quyền truy cập đơn hàng này");
        }
        return ResponseEntity.ok(donHang);
    }

    @PostMapping("/them")
    public ResponseEntity<?> add(@RequestBody CheckoutOrderRequest request) {
        try {
            CheckoutOrderResponse donHang = orderService.saveOrUpdate(request);
            return ResponseEntity.ok(donHang);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Tạo đơn hàng thất bại");
        }
    }

    @GetMapping("/submitOrder")
    public ResponseEntity<?> submidOrder(@RequestParam("maDonHang") Long maDonHang) {
        try {
            NguoiDung currentUser = getCurrentUser();
            DonHang donHang = donHangRepository.findById(maDonHang)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng không tồn tại."));
            if (donHang.getNguoiDung() == null
                    || donHang.getNguoiDung().getMaNguoiDung() != currentUser.getMaNguoiDung()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền thanh toán đơn hàng này.");
            }
            if (isCashOnDelivery(donHang)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng COD không cần tạo liên kết thanh toán.");
            }
            if (donHang.getTrangThaiThanhToan() != null && donHang.getTrangThaiThanhToan() == 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng này đã được thanh toán.");
            }
            String paymentUrl = vnPayService.createOrder((int) Math.round(donHang.getTongTien()), String.valueOf(donHang.getMaDonHang()), "");
            return ResponseEntity.ok(new VNPayUrlResponse(paymentUrl));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Không thể tạo liên kết thanh toán");
        }
    }

    @GetMapping("/vnpay-payment")
    public String GetMapping(HttpServletRequest request, Model model) {
        int paymentStatus = vnPayService.orderReturn(request);

        String orderInfo = request.getParameter("vnp_OrderInfo");
        String paymentTime = request.getParameter("vnp_PayDate");
        String transactionId = request.getParameter("vnp_TransactionNo");
        String totalPrice = request.getParameter("vnp_Amount");

        model.addAttribute("orderId", orderInfo);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("paymentTime", paymentTime);
        model.addAttribute("transactionId", transactionId);
        System.out.println("======" + orderInfo);

        DonHang donHang = donHangRepository.findById(Long.valueOf(orderInfo)).orElse(null);
        if (donHang == null) {
            return "orderfail";
        }
        if (isCashOnDelivery(donHang)) {
            return "orderfail";
        }
        if (donHang.getTrangThaiThanhToan() != null && donHang.getTrangThaiThanhToan() == 1) {
            return "ordersuccess";
        }
        long soTienVnPay = parseVnPayAmount(totalPrice);
        long tongTienDonHang = Math.round(donHang.getTongTien() * 100);
        if (soTienVnPay != tongTienDonHang) {
            return "orderfail";
        }
        List<ChiTietDonHang> chiTietDonHangs = chiTietDonHangRepository.findAll((root, query, builder) -> builder.equal(
                root.get("donHang").get("maDonHang"), donHang.getMaDonHang()
        ));
        if (paymentStatus == 1) {
            donHang.setTrangThaiThanhToan(1);
            donHang.setTrangThaiGiaoHang(1);
            donHangRepository.save(donHang);
            try {
                String noiDung = this.generateOrderEmailBody(String.valueOf(donHang.getMaDonHang()),
                        donHang.getNguoiDung().getHoDem() + " " + donHang.getNguoiDung().getTen(),
                        donHang.getNgayTao().toString(),
                        donHang.getDiaChiNhanHang(),
                        String.valueOf(donHang.getTongTien()),
                        chiTietDonHangs
                );
                emailService.sendEmail("tienvovan917@gmail.com", donHang.getNguoiDung().getEmail(),
                        "Thông báo Đơn hàng của bạn", noiDung);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return paymentStatus == 1 ? "ordersuccess" : "orderfail";
    }

    @PostMapping("/cap-nhat-trang-thai-giao-hang/{maDonHang}")
    public ResponseEntity<?> submidOrder(@PathVariable Long maDonHang, HttpServletRequest request) {
        DonHang donHang = donHangRepository.findById(maDonHang).orElse(null);
        if (donHang == null) {
            return ResponseEntity.badRequest().body("Đơn hàng không tồn tại");
        }
        donHang.setTrangThaiGiaoHang(2);
        donHangRepository.save(donHang);
        return ResponseEntity.ok(donHang);
    }

    public String generateOrderEmailBody(String orderId, String customerName, String orderDate, String diaChi, String tongTien, List<ChiTietDonHang> chiTietDonHangs) {
        String chiTienDonHang = "";
        for (ChiTietDonHang chiTietDonHang : chiTietDonHangs) {
            chiTienDonHang += "<tr>"
                    + "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getMaChiTietDonHang() + "</td>"
                    + "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSach().getTenSach() + "</td>"
                    + "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSoLuong() + "</td>"
                    + "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSach().getGiaBan() + "</td>"
                    + "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSoLuong() * chiTietDonHang.getSach().getGiaBan() + "</td>"
                    + "</tr>";
        }
        return "<html>"
                + "<body>"
                + "<h2 style=\"border-bottom: 2px solid #333; padding-bottom: 10px;\">Thông báo Đơn hàng của bạn</h2>"
                + "<p>Chào " + customerName + ",</p>"
                + "<p>Cảm ơn bạn đã đặt hàng tại chúng tôi! Dưới đây là thông tin chi tiết về đơn hàng của bạn:</p>"
                + "<p><b>Mã Đơn Hàng : </b>" + orderId + "</p>"
                + "<p><b>Ngày Đặt Hàng : </b>" + orderDate + "</p>"
                + "<table style=\"width: 100%; border: 1px solid #ddd; border-collapse: collapse;\">"
                + "<thead style=\"background-color: #f4f4f4;\">"
                + "<tr>"
                + "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Mã chi tiết đơn hàng</th>"
                + "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Tên sách</th>"
                + "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Số lượng</th>"
                + "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Giá bán</th>"
                + "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Thanh toán</th>"
                + "</tr>"
                + "</thead>"
                + "<tbody>"
                + chiTienDonHang
                + "</tbody>"
                + "</table>"
                + "<p style=\"color:red; border-top: 2px solid red; padding-top: 10px;\"><b>Tổng tiền: " + tongTien + "</b></p>"
                + "<p><b>Địa chỉ nhận hàng: " + diaChi + "</b></p>"
                + "<p style=\"border-top: 1px solid #ddd; padding-top: 10px;\">Đơn hàng của bạn sẽ được xử lý trong vòng 24 giờ. Chúng tôi sẽ thông báo khi hàng hóa được gửi đi.</p>"
                + "<p style=\"border-top: 1px solid #ddd; padding-top: 10px;\">Trân trọng cảm ơn!</p>"
                + "</body>"
                + "</html>";
    }

    @PostMapping("/them-don-hang-moi")
    public ResponseEntity<?> themDonHangMoi(@RequestParam String hoTen, @RequestParam String soDienThoai, @RequestParam String diaChiNhanHang) {
        DonHang donHang = new DonHang();

        donHang.setHoTen(hoTen);
        donHang.setSoDienThoai(soDienThoai);
        donHang.setDiaChiNhanHang(diaChiNhanHang);
        donHang.setNgayTao(new Date());
        donHang.setTongTien(0);
        donHang.setTrangThaiThanhToan(0);
        donHang.setTrangThaiGiaoHang(0);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
            if (nguoiDung != null) {
                donHang.setNguoiDung(nguoiDung);
            }
        }

        return ResponseEntity.ok(donHangRepository.save(donHang));
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

    private boolean isCashOnDelivery(DonHang donHang) {
        return donHang.getHinhThucThanhToan() == null
                || PAYMENT_METHOD_COD.equalsIgnoreCase(donHang.getHinhThucThanhToan().getMaCode());
    }

    private long parseVnPayAmount(String totalPrice) {
        try {
            return Long.parseLong(totalPrice);
        } catch (Exception e) {
            return -1;
        }
    }
}
