package com.example.book_be.services.cart;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DiaChiGiaoHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.GioHangRepository;
import com.example.book_be.dao.HinhThucThanhToanRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dto.cart.CartItemRequest;
import com.example.book_be.dto.cart.CheckoutOrderRequest;
import com.example.book_be.dto.cart.CheckoutOrderResponse;
import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.DiaChiGiaoHang;
import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.HinhThucThanhToan;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    @Transactional
    public CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request) {
        NguoiDung nguoiDung = getCurrentUser();
        validateCheckoutRequest(request);

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
        donHang.setChiPhiGiaoHang(0);

        Map<Integer, Integer> soLuongTheoSach = gomSoLuongTheoSach(request.getItems());
        double tongTienSanPham = 0;
        for (Map.Entry<Integer, Integer> entry : soLuongTheoSach.entrySet()) {
            Sach db = sachRepository.findById(Long.valueOf(entry.getKey()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách không tồn tại."));
            if (db.getSoLuong() < entry.getValue()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Sách " + db.getTenSach() + " không đủ tồn kho.");
            }
            tongTienSanPham += entry.getValue() * db.getGiaBan();
        }

        donHang.setTongTienSanPham(tongTienSanPham);
        donHang.setTongTien(tongTienSanPham + donHang.getChiPhiGiaoHang() + donHang.getChiPhiThanhToan());
        donHangRepository.save(donHang);

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

        return new CheckoutOrderResponse(
                donHang.getMaDonHang(),
                donHang.getTongTien(),
                normalizePaymentMethodCode(hinhThucThanhToan),
                donHang.getTrangThaiThanhToan(),
                donHang.getHoTen(),
                donHang.getSoDienThoai(),
                donHang.getDiaChiNhanHang()
        );
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
        Map<Integer, Integer> soLuongTheoSach = new HashMap<>();
        for (CartItemRequest item : items) {
            if (item.getMaSach() == null || item.getSoLuong() == null || item.getSoLuong() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin sản phẩm đặt hàng không hợp lệ.");
            }
            soLuongTheoSach.merge(item.getMaSach(), item.getSoLuong(), Integer::sum);
        }
        return soLuongTheoSach;
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
}
