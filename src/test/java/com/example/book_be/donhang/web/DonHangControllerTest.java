package com.example.book_be.donhang.web;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.HinhThucGiaoHang;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.service.DonHangHuyService;
import com.example.book_be.donhang.service.DonHangTrangThaiService;
import com.example.book_be.donhang.service.OrderService;
import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.service.UserService;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.shared.email.EmailService;
import com.example.book_be.shared.web.ApiErrorWriter;
import com.example.book_be.shared.web.ApiExceptionHandler;
import com.example.book_be.thanhtoan.domain.HinhThucThanhToan;
import com.example.book_be.thanhtoan.service.VNPayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DonHangController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ApiErrorWriter.class})
class DonHangControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    OrderService orderService;
    @MockBean
    DonHangTrangThaiService donHangTrangThaiService;
    @MockBean
    DonHangHuyService donHangHuyService;
    @MockBean
    VNPayService vnPayService;
    @MockBean
    EmailService emailService;
    @MockBean
    DonHangRepository donHangRepository;
    @MockBean
    ChiTietDonHangRepository chiTietDonHangRepository;
    @MockBean
    NguoiDungRepository nguoiDungRepository;
    @MockBean
    JwtService jwtService;
    @MockBean
    UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void owner_nhan_dto_chi_tiet_day_du_va_khong_lo_du_lieu_noi_bo() throws Exception {
        NguoiDung owner = user(7, "owner");
        DonHang order = order(owner);
        when(nguoiDungRepository.findByTenDangNhap("owner")).thenReturn(owner);
        when(donHangRepository.findDetailById(91L)).thenReturn(Optional.of(order));
        authenticate("owner", "USER");

        mvc.perform(get("/api/don-hang/91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maDonHang").value(91))
                .andExpect(jsonPath("$.hoTen").value("Người nhận"))
                .andExpect(jsonPath("$.phuongThucThanhToan").value("COD"))
                .andExpect(jsonPath("$.tenHinhThucGiaoHang").value("Giao hàng tận nơi"))
                .andExpect(jsonPath("$.tongTienSanPham").value(200000))
                .andExpect(jsonPath("$.soTienGiam").value(10000))
                .andExpect(jsonPath("$.chiPhiGiaoHang").value(10000))
                .andExpect(jsonPath("$.tongTien").value(200000))
                .andExpect(jsonPath("$.danhSachChiTietDonHang[0].maSach").value(3))
                .andExpect(jsonPath("$.danhSachChiTietDonHang[0].tenSach").value("Sách kiểm thử"))
                .andExpect(jsonPath("$.danhSachChiTietDonHang[0].soLuong").value(2))
                .andExpect(jsonPath("$.danhSachChiTietDonHang[0].giaBan").value(100000))
                .andExpect(jsonPath("$.danhSachChiTietDonHang[0].thanhTien").value(200000))
                .andExpect(jsonPath("$.nguoiDung").doesNotExist())
                .andExpect(jsonPath("$.checkoutIdempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void user_khac_khong_duoc_xem_don() throws Exception {
        NguoiDung owner = user(7, "owner");
        NguoiDung other = user(8, "other");
        when(nguoiDungRepository.findByTenDangNhap("other")).thenReturn(other);
        when(donHangRepository.findDetailById(91L)).thenReturn(Optional.of(order(owner)));
        authenticate("other", "USER");

        mvc.perform(get("/api/don-hang/91"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền truy cập đơn hàng này."));
    }

    @Test
    void admin_duoc_xem_don_cua_user_khac() throws Exception {
        NguoiDung admin = user(1, "admin");
        when(nguoiDungRepository.findByTenDangNhap("admin")).thenReturn(admin);
        when(donHangRepository.findDetailById(91L)).thenReturn(Optional.of(order(user(7, "owner"))));
        authenticate("admin", "ADMIN");

        mvc.perform(get("/api/don-hang/91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maDonHang").value(91));
    }

    @Test
    void don_khong_ton_tai_tra_404() throws Exception {
        NguoiDung owner = user(7, "owner");
        when(nguoiDungRepository.findByTenDangNhap("owner")).thenReturn(owner);
        when(donHangRepository.findDetailById(999L)).thenReturn(Optional.empty());
        authenticate("owner", "USER");

        mvc.perform(get("/api/don-hang/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Đơn hàng không tồn tại."));
    }

    @Test
    void owner_tao_lai_link_vnpay_cho_don_dang_cho_thanh_toan() throws Exception {
        NguoiDung owner = user(7, "owner");
        DonHang order = order(owner);
        order.getHinhThucThanhToan().setMaCode("VNPAY");
        when(nguoiDungRepository.findByTenDangNhap("owner")).thenReturn(owner);
        when(donHangRepository.findById(91L)).thenReturn(Optional.of(order));
        when(vnPayService.createOrder(200000, "91")).thenReturn("https://sandbox.vnpay.vn/pay/91");
        authenticate("owner", "USER");

        mvc.perform(get("/api/don-hang/submitOrder").param("maDonHang", "91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").value("https://sandbox.vnpay.vn/pay/91"));

        verify(vnPayService).createOrder(200000, "91");
    }

    @Test
    void user_khac_khong_duoc_tao_link_vnpay() throws Exception {
        NguoiDung owner = user(7, "owner");
        NguoiDung other = user(8, "other");
        DonHang order = order(owner);
        order.getHinhThucThanhToan().setMaCode("VNPAY");
        when(nguoiDungRepository.findByTenDangNhap("other")).thenReturn(other);
        when(donHangRepository.findById(91L)).thenReturn(Optional.of(order));
        authenticate("other", "USER");

        mvc.perform(get("/api/don-hang/submitOrder").param("maDonHang", "91"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền thanh toán đơn hàng này."));

        verify(vnPayService, never()).createOrder(anyInt(), anyString());
    }

    @Test
    void khong_tao_link_vnpay_cho_don_cod() throws Exception {
        assertPaymentLinkRejected(order(user(7, "owner")), "Đơn hàng không dùng phương thức thanh toán VNPAY.");
    }

    @Test
    void khong_tao_link_vnpay_cho_don_da_thanh_toan() throws Exception {
        DonHang order = vnpayOrder(user(7, "owner"));
        order.setTrangThaiThanhToan(1);
        assertPaymentLinkRejected(order, "Đơn hàng này đã được thanh toán.");
    }

    @Test
    void khong_tao_link_vnpay_cho_don_khong_con_cho_xu_ly() throws Exception {
        for (int deliveryStatus : List.of(1, 2, 3)) {
            DonHang order = vnpayOrder(user(7, "owner"));
            order.setTrangThaiGiaoHang(deliveryStatus);
            assertPaymentLinkRejected(order, "Đơn hàng không thể tiếp tục thanh toán ở trạng thái hiện tại.");
        }
    }

    @Test
    void tao_link_vnpay_va_callback_dung_cung_cach_lam_tron_tien() throws Exception {
        NguoiDung owner = user(7, "owner");
        DonHang order = vnpayOrder(owner);
        order.setTongTien(84999.15);
        when(nguoiDungRepository.findByTenDangNhap("owner")).thenReturn(owner);
        when(donHangRepository.findById(91L)).thenReturn(Optional.of(order));
        when(vnPayService.createOrder(84999, "91")).thenReturn("https://sandbox.vnpay.vn/pay/91");
        when(vnPayService.orderReturn(any())).thenReturn(1);
        when(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(91L, 8499900L))
                .thenReturn("ordersuccess");
        authenticate("owner", "USER");

        mvc.perform(get("/api/don-hang/submitOrder").param("maDonHang", "91"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/don-hang/vnpay-payment")
                        .param("vnp_OrderInfo", "91")
                        .param("vnp_Amount", "8499900"))
                .andExpect(status().isOk())
                .andExpect(content().string("ordersuccess"));

        verify(vnPayService).createOrder(84999, "91");
        verify(donHangTrangThaiService).xuLyThanhToanVnPayThanhCong(91L, 8499900L);
    }

    @Test
    void khong_tao_link_vnpay_khi_tong_tien_vuot_mien_int() throws Exception {
        DonHang order = vnpayOrder(user(7, "owner"));
        order.setTongTien((double) Integer.MAX_VALUE + 1);
        assertPaymentLinkRejected(order, "Tổng tiền đơn hàng không hợp lệ.");
    }

    @Test
    void vnpay_callback_khong_hop_le_hoac_malformed_tra_orderfail_khong_truy_van_don() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(0);

        mvc.perform(get("/api/don-hang/vnpay-payment"))
                .andExpect(status().isOk())
                .andExpect(content().string("orderfail"));

        verify(donHangRepository, never()).findById(any(Long.class));
        verify(donHangTrangThaiService, never())
                .xuLyThanhToanVnPayThanhCong(any(Long.class), any(Long.class));
    }

    @Test
    void vnpay_callback_order_info_malformed_tra_orderfail() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(1);

        for (String orderInfo : List.of("abc", "9223372036854775808", "0", "-1")) {
            mvc.perform(get("/api/don-hang/vnpay-payment")
                            .param("vnp_OrderInfo", orderInfo)
                            .param("vnp_Amount", "20000000"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("orderfail"));
        }

        verify(donHangTrangThaiService, never())
                .xuLyThanhToanVnPayThanhCong(any(Long.class), any(Long.class));
    }

    @Test
    void vnpay_callback_thanh_cong_chi_chuyen_trang_thai_khong_gui_email_trung() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(1);
        when(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(91L, 20000000L))
                .thenReturn("ordersuccess");

        performSuccessfulVnPayCallback()
                .andExpect(status().isOk())
                .andExpect(content().string("ordersuccess"));

        verify(donHangTrangThaiService).xuLyThanhToanVnPayThanhCong(91L, 20000000L);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void vnpay_callback_thanh_cong_sau_khi_huy_chi_ghi_nhan_thanh_toan() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(1);
        when(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(91L, 20000000L))
                .thenReturn("ordercancelledpaid");

        performSuccessfulVnPayCallback()
                .andExpect(status().isOk())
                .andExpect(content().string("ordercancelledpaid"));

        verify(donHangTrangThaiService).xuLyThanhToanVnPayThanhCong(91L, 20000000L);
    }

    @Test
    void vnpay_callback_link_cu_khong_lui_trang_thai_dang_giao_hoac_da_giao() throws Exception {
        for (int deliveryStatus : List.of(1, 2)) {
            when(vnPayService.orderReturn(any())).thenReturn(1);
            when(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(91L, 20000000L))
                    .thenReturn("ordersuccess");

            performSuccessfulVnPayCallback()
                    .andExpect(status().isOk())
                    .andExpect(content().string("ordersuccess"));
        }

        verify(donHangTrangThaiService, org.mockito.Mockito.times(2))
                .xuLyThanhToanVnPayThanhCong(91L, 20000000L);
    }

    @Test
    void vnpay_callback_lap_cho_don_da_huy_da_thanh_toan_van_vao_khoa_authoritative() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(1);
        when(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(91L, 20000000L))
                .thenReturn("ordercancelledpaid");

        performSuccessfulVnPayCallback()
                .andExpect(status().isOk())
                .andExpect(content().string("ordercancelledpaid"));

        verify(donHangTrangThaiService).xuLyThanhToanVnPayThanhCong(91L, 20000000L);
    }

    @Test
    void vnpay_callback_that_bai_cho_don_da_huy_khong_mutation() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(0);

        performSuccessfulVnPayCallback()
                .andExpect(status().isOk())
                .andExpect(content().string("orderfail"));

        verify(donHangTrangThaiService, never())
                .xuLyThanhToanVnPayThanhCong(any(Long.class), any(Long.class));
    }

    @Test
    void vnpay_callback_that_bai_khong_duoc_bao_thanh_cong_cho_don_da_thanh_toan() throws Exception {
        when(vnPayService.orderReturn(any())).thenReturn(0);

        performSuccessfulVnPayCallback()
                .andExpect(status().isOk())
                .andExpect(content().string("orderfail"));

        verify(donHangTrangThaiService, never())
                .xuLyThanhToanVnPayThanhCong(any(Long.class), any(Long.class));
    }

    private org.springframework.test.web.servlet.ResultActions performSuccessfulVnPayCallback() throws Exception {
        return mvc.perform(get("/api/don-hang/vnpay-payment")
                .param("vnp_OrderInfo", "91")
                .param("vnp_Amount", "20000000")
                .param("vnp_PayDate", "20260810120000")
                .param("vnp_TransactionNo", "txn-91"));
    }

    private void assertPaymentLinkRejected(DonHang order, String expectedMessage) throws Exception {
        NguoiDung owner = order.getNguoiDung();
        when(nguoiDungRepository.findByTenDangNhap("owner")).thenReturn(owner);
        when(donHangRepository.findById(91L)).thenReturn(Optional.of(order));
        authenticate("owner", "USER");

        mvc.perform(get("/api/don-hang/submitOrder").param("maDonHang", "91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage));

        verify(vnPayService, never()).createOrder(anyInt(), anyString());
    }

    private DonHang vnpayOrder(NguoiDung owner) {
        DonHang order = order(owner);
        order.getHinhThucThanhToan().setMaCode("VNPAY");
        return order;
    }

    private void authenticate(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "",
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }

    private NguoiDung user(int id, String username) {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(id);
        user.setTenDangNhap(username);
        return user;
    }

    private DonHang order(NguoiDung owner) {
        Sach book = new Sach();
        book.setMaSach(3);
        book.setTenSach("Sách kiểm thử");
        book.setGiaBan(125000);

        ChiTietDonHang line = new ChiTietDonHang();
        line.setMaChiTietDonHang(12);
        line.setSach(book);
        line.setSoLuong(2);
        line.setGiaBan(100000);

        HinhThucThanhToan payment = new HinhThucThanhToan();
        payment.setMaCode("COD");
        payment.setTenHinhThucGiaoHang("Thanh toán khi nhận hàng");

        HinhThucGiaoHang delivery = new HinhThucGiaoHang();
        delivery.setTenHinhThucGiaoHang("Giao hàng tận nơi");

        DonHang order = new DonHang();
        order.setMaDonHang(91);
        order.setNgayTao(new Date(1760000000000L));
        order.setNguoiDung(owner);
        order.setHoTen("Người nhận");
        order.setSoDienThoai("0900000000");
        order.setDiaChiNhanHang("Địa chỉ kiểm thử");
        order.setHinhThucThanhToan(payment);
        order.setHinhThucGiaoHang(delivery);
        order.setTrangThaiThanhToan(0);
        order.setTrangThaiGiaoHang(0);
        order.setTongTienSanPham(200000);
        order.setChiPhiGiaoHang(10000);
        order.setChiPhiThanhToan(0);
        order.setTongTien(200000);
        order.setDanhSachChiTietDonHang(List.of(line));
        line.setDonHang(order);
        return order;
    }
}
