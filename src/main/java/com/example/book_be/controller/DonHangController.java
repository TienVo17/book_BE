package com.example.book_be.controller;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.*;
import com.example.book_be.services.VNPayService;
import com.example.book_be.services.cart.OrderService;
import com.example.book_be.services.email.EmailService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("api/don-hang")
public class DonHangController {


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
    public Page<DonHang> findAll(
                                     @RequestParam("page") Integer page,
                                     HttpServletRequest request) {
        Pageable pageable = PageRequest.of(page, 10);
        NguoiDung nguoiDung = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getName() != null) {
            nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        }
        NguoiDung finalNguoiDung = nguoiDung;
        return donHangRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if(authentication.getAuthorities().contains("ADMIN") || authentication.getAuthorities().contains("USER")){
                predicates.add(builder.equal(root.get("nguoiDung").get("maNguoiDung"), finalNguoiDung.getMaNguoiDung()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    @PostMapping("/them")
    public DonHang add(@RequestBody List<Sach> sachList) {
        DonHang donHang = null;
        try {
            donHang = orderService.saveOrUpdate(sachList);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return donHang;
    }

    @GetMapping("/submitOrder")
    public String submidOrder(@RequestParam("amount") int orderTotal,
                              @RequestParam("orderInfo") String orderInfo,
                              HttpServletRequest request) {
        String baseUrl = "";
        String vnpayUrl = vnPayService.createOrder(orderTotal, orderInfo, baseUrl);
        return vnpayUrl;
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

            }
        }
        return paymentStatus == 1 ? "ordersuccess" : "orderfail";
    }

    @PostMapping("/cap-nhat-trang-thai-giao-hang/{maDonHang}")
    public void submidOrder(@PathVariable Long maDonHang,
                              HttpServletRequest request) {
        DonHang donHang = donHangRepository.findById(maDonHang).orElse(null);
        donHang.setTrangThaiGiaoHang(2);
        donHang.setTrangThaiThanhToan(1);
        donHangRepository.save(donHang);
    }

    public String generateOrderEmailBody(String orderId, String customerName, String orderDate, String diaChi, String tongTien, List<ChiTietDonHang> chiTietDonHangs) {
        String chiTienDonHang = "";
        for (ChiTietDonHang chiTietDonHang : chiTietDonHangs) {
            chiTienDonHang += "<tr>" +
                    "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getMaChiTietDonHang() + "</td>" +
                    "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSach().getTenSach() + "</td>" +
                    "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSoLuong() + "</td>" +
                    "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSach().getGiaBan() + "</td>" +
                    "<td style=\"border: 1px solid #ddd; padding: 8px;\">" + chiTietDonHang.getSoLuong() * chiTietDonHang.getSach().getGiaBan() + "</td>" +
                    "</tr>";
        }
        return "<html>" +
                "<body>" +
                "<h2 style=\"border-bottom: 2px solid #333; padding-bottom: 10px;\">Thông báo Đơn hàng của bạn</h2>" +
                "<p>Chào " + customerName + ",</p>" +
                "<p>Cảm ơn bạn đã đặt hàng tại chúng tôi! Dưới đây là thông tin chi tiết về đơn hàng của bạn:</p>" +
                "<p><b>Mã Đơn Hàng : </b>" + orderId + "</p>" +
                "<p><b>Ngày Đặt Hàng : </b>" + orderDate + "</p>" +
                "<table style=\"width: 100%; border: 1px solid #ddd; border-collapse: collapse;\">" +
                // Phần tiêu đề bảng (thead)
                "<thead style=\"background-color: #f4f4f4;\">" +
                "<tr>" +
                "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Mã chi tiết đơn hàng</th>" +
                "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Tên sách</th>" +
                "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Số lượng</th>" +
                "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Giá bán</th>" +
                "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">Thanh toán</th>" +
                "</tr>" +
                "</thead>" +

                // Phần nội dung bảng (tbody)
                "<tbody>" +
                chiTienDonHang +
                "</tbody>" +
                "</table>" +

                "<p style=\"color:red; border-top: 2px solid red; padding-top: 10px;\"><b>Tổng tiền: " + tongTien + "</b></p>" +
                "<p><b>Địa chỉ nhận hàng: " + diaChi + "</b></p>" +

                "<p style=\"border-top: 1px solid #ddd; padding-top: 10px;\">Đơn hàng của bạn sẽ được xử lý trong vòng 24 giờ. Chúng tôi sẽ thông báo khi hàng hóa được gửi đi.</p>" +
                "<p style=\"border-top: 1px solid #ddd; padding-top: 10px;\">Trân trọng cảm ơn!</p>" +
                "</body>" +
                "</html>";
    }
    @PostMapping("/them-don-hang-moi")
    public DonHang themDonHangMoi(
            @RequestParam String hoTen,
            @RequestParam String soDienThoai,
            @RequestParam String diaChiNhanHang) {
        DonHang donHang = new DonHang();

        // Gán các giá trị cần thiết
        donHang.setHoTen(hoTen);
        donHang.setSoDienThoai(soDienThoai);
        donHang.setDiaChiNhanHang(diaChiNhanHang);
        donHang.setNgayTao(new Date());
        donHang.setTongTien(0);
        donHang.setTrangThaiThanhToan(0);
        donHang.setTrangThaiGiaoHang(0);

        // Gán mã đơn hàng ngẫu nhiên
        Random random = new Random();
        int randomMaDonHang = 1000 + random.nextInt(9000);
        donHang.setMaDonHang(randomMaDonHang);

        // Gán giá trị mặc định cho NguoiDung
        NguoiDung defaultNguoiDung = new NguoiDung();
        defaultNguoiDung.setMaNguoiDung(1); // ID mặc định trong cơ sở dữ liệu
        donHang.setNguoiDung(defaultNguoiDung);

        return donHangRepository.save(donHang);
    }


}
