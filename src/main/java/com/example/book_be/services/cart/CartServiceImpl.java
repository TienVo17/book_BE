package com.example.book_be.services.cart;

import com.example.book_be.dao.GioHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dto.cart.CartItemRequest;
import com.example.book_be.dto.cart.CartItemResponse;
import com.example.book_be.dto.cart.CartLineAdjustmentResponse;
import com.example.book_be.dto.cart.CartMergeRequest;
import com.example.book_be.dto.cart.CartMergeResponse;
import com.example.book_be.dto.cart.CartSummaryResponse;
import com.example.book_be.entity.GioHang;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartServiceImpl implements CartService {
    private final NguoiDungRepository nguoiDungRepository;
    private final GioHangRepository gioHangRepository;
    private final SachRepository sachRepository;

    public CartServiceImpl(
            NguoiDungRepository nguoiDungRepository,
            GioHangRepository gioHangRepository,
            SachRepository sachRepository
    ) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.gioHangRepository = gioHangRepository;
        this.sachRepository = sachRepository;
    }

    @Override
    public CartSummaryResponse getCurrentUserCart() {
        return buildSummary(getCurrentUserCartLines());
    }

    @Override
    @Transactional
    public CartSummaryResponse addItem(CartItemRequest request) {
        int soLuongThem = normalizePositiveQuantity(request == null ? null : request.getSoLuong(), true);
        Sach sach = getValidBook(request == null ? null : request.getMaSach());
        NguoiDung nguoiDung = getCurrentUser();
        GioHang line = gioHangRepository.findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
        int soLuongMoi = line.getSoLuong() + soLuongThem;
        if (soLuongMoi > sach.getSoLuong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng vượt quá tồn kho hiện tại.");
        }
        line.setSoLuong(soLuongMoi);
        gioHangRepository.save(line);
        return buildSummary(getCurrentUserCartLines());
    }

    @Override
    @Transactional
    public CartSummaryResponse updateItemQuantity(Integer maSach, Integer soLuong) {
        Sach sach = getValidBook(maSach);
        NguoiDung nguoiDung = getCurrentUser();
        int quantity = normalizePositiveQuantity(soLuong, false);
        if (quantity == 0) {
            gioHangRepository.deleteByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach());
            return buildSummary(getCurrentUserCartLines());
        }
        if (quantity > sach.getSoLuong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng vượt quá tồn kho hiện tại.");
        }
        GioHang line = gioHangRepository.findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
        line.setSoLuong(quantity);
        gioHangRepository.save(line);
        return buildSummary(getCurrentUserCartLines());
    }

    @Override
    @Transactional
    public CartSummaryResponse removeItem(Integer maSach) {
        NguoiDung nguoiDung = getCurrentUser();
        gioHangRepository.deleteByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), maSach);
        return buildSummary(getCurrentUserCartLines());
    }

    @Override
    @Transactional
    public CartMergeResponse mergeGuestCart(CartMergeRequest request) {
        NguoiDung nguoiDung = getCurrentUser();
        Map<Integer, Integer> guestItems = groupGuestItems(request);
        CartMergeResponse response = new CartMergeResponse();
        if (guestItems.isEmpty()) {
            applySummary(response, getCurrentUserCartLines());
            return response;
        }

        for (Map.Entry<Integer, Integer> entry : guestItems.entrySet()) {
            Integer maSach = entry.getKey();
            Integer requestedSoLuong = entry.getValue();
            Sach sach = sachRepository.findById(Long.valueOf(maSach)).orElse(null);
            if (sach == null || !isBookActive(sach)) {
                response.getRemovedItems().add(new CartLineAdjustmentResponse(
                        maSach,
                        sach == null ? null : sach.getTenSach(),
                        requestedSoLuong,
                        0,
                        sach == null ? "BOOK_NOT_FOUND" : "BOOK_INACTIVE"
                ));
                continue;
            }
            if (sach.getSoLuong() <= 0) {
                response.getRemovedItems().add(new CartLineAdjustmentResponse(
                        maSach,
                        sach.getTenSach(),
                        requestedSoLuong,
                        0,
                        "OUT_OF_STOCK"
                ));
                continue;
            }

            GioHang line = gioHangRepository.findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                    .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
            int mergedSoLuong = line.getSoLuong() + requestedSoLuong;
            int appliedSoLuong = Math.min(mergedSoLuong, sach.getSoLuong());
            line.setSoLuong(appliedSoLuong);
            gioHangRepository.save(line);
            response.setMergedCount(response.getMergedCount() + 1);
            if (appliedSoLuong != mergedSoLuong) {
                response.getAdjustedItems().add(new CartLineAdjustmentResponse(
                        sach.getMaSach(),
                        sach.getTenSach(),
                        mergedSoLuong,
                        appliedSoLuong,
                        "CAPPED_TO_STOCK"
                ));
            }
        }

        applySummary(response, getCurrentUserCartLines());
        return response;
    }

    @Override
    @Transactional
    public void clearCurrentUserCart() {
        NguoiDung nguoiDung = getCurrentUser();
        gioHangRepository.deleteGioHangByMaNguoiDung(nguoiDung.getMaNguoiDung());
    }

    private NguoiDung getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng không tồn tại.");
        }
        return nguoiDung;
    }

    private Sach getValidBook(Integer maSach) {
        if (maSach == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã sách.");
        }
        Sach sach = sachRepository.findById(Long.valueOf(maSach))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách không tồn tại."));
        if (!isBookActive(sach)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách hiện không khả dụng.");
        }
        if (sach.getSoLuong() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách đã hết hàng.");
        }
        return sach;
    }

    private boolean isBookActive(Sach sach) {
        return sach.getIsActive() == null || sach.getIsActive() == 1;
    }

    private int normalizePositiveQuantity(Integer soLuong, boolean requireAtLeastOne) {
        if (soLuong == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu số lượng.");
        }
        if (requireAtLeastOne && soLuong < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng phải lớn hơn hoặc bằng 1.");
        }
        if (!requireAtLeastOne && soLuong < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng không hợp lệ.");
        }
        return soLuong;
    }

    private GioHang createCartLine(NguoiDung nguoiDung, Sach sach, int soLuong) {
        GioHang gioHang = new GioHang();
        gioHang.setNguoiDung(nguoiDung);
        gioHang.setSach(sach);
        gioHang.setSoLuong(soLuong);
        return gioHang;
    }

    private Map<Integer, Integer> groupGuestItems(CartMergeRequest request) {
        Map<Integer, Integer> grouped = new LinkedHashMap<>();
        if (request == null || request.getItems() == null) {
            return grouped;
        }
        for (CartItemRequest item : request.getItems()) {
            if (item == null || item.getMaSach() == null || item.getSoLuong() == null || item.getSoLuong() <= 0) {
                continue;
            }
            grouped.merge(item.getMaSach(), item.getSoLuong(), Integer::sum);
        }
        return grouped;
    }

    private List<GioHang> getCurrentUserCartLines() {
        NguoiDung nguoiDung = getCurrentUser();
        return gioHangRepository.findByMaNguoiDung(nguoiDung.getMaNguoiDung());
    }

    private CartSummaryResponse buildSummary(List<GioHang> lines) {
        CartSummaryResponse response = new CartSummaryResponse();
        applySummary(response, lines);
        return response;
    }

    private void applySummary(CartSummaryResponse response, List<GioHang> lines) {
        List<CartItemResponse> items = new ArrayList<>();
        int tongSoLuong = 0;
        double tongTien = 0;
        lines.sort(Comparator.comparingInt(GioHang::getMaGioHang));
        for (GioHang line : lines) {
            Sach sach = line.getSach();
            if (sach == null || !isBookActive(sach) || sach.getSoLuong() <= 0) {
                continue;
            }
            int appliedSoLuong = Math.min(line.getSoLuong(), sach.getSoLuong());
            if (appliedSoLuong <= 0) {
                continue;
            }
            items.add(new CartItemResponse(
                    sach.getMaSach(),
                    sach.getTenSach(),
                    sach.getGiaBan(),
                    appliedSoLuong,
                    sach.getSoLuong(),
                    getBookImage(sach),
                    isBookActive(sach)
            ));
            tongSoLuong += appliedSoLuong;
            tongTien += sach.getGiaBan() * appliedSoLuong;
        }
        response.setItems(items);
        response.setTongSoLuong(tongSoLuong);
        response.setTongTien(tongTien);
    }

    private String getBookImage(Sach sach) {
        if (sach.getListHinhAnh() == null || sach.getListHinhAnh().isEmpty()) {
            return "";
        }
        return sach.getListHinhAnh().stream()
                .map(HinhAnh::getUrlHinh)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse("");
    }
}
