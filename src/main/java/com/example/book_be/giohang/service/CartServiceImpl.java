package com.example.book_be.giohang.service;

import com.example.book_be.giohang.domain.GioHang;
import com.example.book_be.giohang.domain.GioHangMergeReceipt;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.giohang.dto.CartItemResponse;
import com.example.book_be.giohang.dto.CartLineAdjustmentResponse;
import com.example.book_be.giohang.dto.CartMergeRequest;
import com.example.book_be.giohang.dto.CartMergeResponse;
import com.example.book_be.giohang.dto.CartSummaryResponse;
import com.example.book_be.giohang.repository.GioHangMergeReceiptRepository;
import com.example.book_be.giohang.repository.GioHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.shared.security.RateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class CartServiceImpl implements CartService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_MERGE_ITEMS = 100;
    private static final int MAX_NEW_MERGES_PER_WINDOW = 30;
    private static final Duration MERGE_RATE_WINDOW = Duration.ofMinutes(10);
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final NguoiDungRepository nguoiDungRepository;
    private final GioHangRepository gioHangRepository;
    private final GioHangMergeReceiptRepository mergeReceiptRepository;
    private final SachRepository sachRepository;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    public CartServiceImpl(
            NguoiDungRepository nguoiDungRepository,
            GioHangRepository gioHangRepository,
            GioHangMergeReceiptRepository mergeReceiptRepository,
            SachRepository sachRepository,
            ObjectMapper objectMapper,
            RateLimiter rateLimiter
    ) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.gioHangRepository = gioHangRepository;
        this.mergeReceiptRepository = mergeReceiptRepository;
        this.sachRepository = sachRepository;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public CartSummaryResponse getCurrentUserCart() {
        NguoiDung nguoiDung = getCurrentUser();
        return buildSummary(getCurrentUserCartLines(nguoiDung));
    }

    @Override
    @Transactional
    public CartSummaryResponse addItem(CartItemRequest request) {
        Integer maSach = request == null ? null : request.getMaSach();
        int soLuongThem = normalizePositiveQuantity(
                request == null ? null : request.getSoLuong(), true);
        NguoiDung nguoiDung = getCurrentUserForWrite();
        Sach sach = getValidBook(maSach);
        GioHang line = gioHangRepository
                .findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
        long soLuongMoi = (long) line.getSoLuong() + soLuongThem;
        if (soLuongMoi > sach.getSoLuong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng vượt quá tồn kho hiện tại.");
        }
        line.setSoLuong((int) soLuongMoi);
        gioHangRepository.save(line);
        return buildSummary(getCurrentUserCartLines(nguoiDung));
    }

    @Override
    @Transactional
    public CartSummaryResponse updateItemQuantity(Integer maSach, Integer soLuong) {
        int quantity = normalizePositiveQuantity(soLuong, false);
        NguoiDung nguoiDung = getCurrentUserForWrite();
        if (quantity == 0) {
            validateBookId(maSach);
            gioHangRepository.deleteByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), maSach);
            return buildSummary(getCurrentUserCartLines(nguoiDung));
        }
        Sach sach = getValidBook(maSach);
        if (quantity > sach.getSoLuong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng vượt quá tồn kho hiện tại.");
        }
        GioHang line = gioHangRepository
                .findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
        line.setSoLuong(quantity);
        gioHangRepository.save(line);
        return buildSummary(getCurrentUserCartLines(nguoiDung));
    }

    @Override
    @Transactional
    public CartSummaryResponse removeItem(Integer maSach) {
        validateBookId(maSach);
        NguoiDung nguoiDung = getCurrentUserForWrite();
        gioHangRepository.deleteByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), maSach);
        return buildSummary(getCurrentUserCartLines(nguoiDung));
    }

    @Override
    @Transactional
    public CartMergeResponse mergeGuestCart(CartMergeRequest request, String idempotencyKeyRaw) {
        Map<Integer, Integer> guestItems = groupGuestItems(request);
        String idempotencyKey = normalizeIdempotencyKey(idempotencyKeyRaw);
        String fingerprint = computeFingerprint(guestItems);
        NguoiDung nguoiDung = getCurrentUserForWrite();

        GioHangMergeReceipt existing = mergeReceiptRepository
                .findByNguoiDung_MaNguoiDungAndIdempotencyKey(
                        nguoiDung.getMaNguoiDung(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key đã được dùng cho một giỏ hàng khác.");
            }
            return readStoredResponse(existing.getResponseJson());
        }

        if (!rateLimiter.choPhep(
                "cart-merge:" + nguoiDung.getMaNguoiDung(),
                MAX_NEW_MERGES_PER_WINDOW,
                MERGE_RATE_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã merge giỏ hàng quá nhiều lần. Vui lòng thử lại sau.");
        }

        CartMergeResponse response = applyGuestItems(nguoiDung, guestItems);
        GioHangMergeReceipt receipt = new GioHangMergeReceipt();
        receipt.setNguoiDung(nguoiDung);
        receipt.setIdempotencyKey(idempotencyKey);
        receipt.setRequestFingerprint(fingerprint);
        receipt.setResponseJson(writeStoredResponse(response));
        receipt.setCreatedAt(LocalDateTime.now());
        mergeReceiptRepository.save(receipt);
        return response;
    }

    @Override
    @Transactional
    public void clearCurrentUserCart() {
        NguoiDung nguoiDung = getCurrentUserForWrite();
        gioHangRepository.deleteGioHangByMaNguoiDung(nguoiDung.getMaNguoiDung());
    }

    private CartMergeResponse applyGuestItems(
            NguoiDung nguoiDung,
            Map<Integer, Integer> guestItems
    ) {
        CartMergeResponse response = new CartMergeResponse();
        for (Map.Entry<Integer, Integer> entry : guestItems.entrySet()) {
            Integer maSach = entry.getKey();
            Integer requestedSoLuong = entry.getValue();
            Sach sach = sachRepository.findByIdForCartWrite(maSach).orElse(null);
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

            GioHang line = gioHangRepository
                    .findByMaNguoiDungAndMaSach(nguoiDung.getMaNguoiDung(), sach.getMaSach())
                    .orElseGet(() -> createCartLine(nguoiDung, sach, 0));
            long mergedSoLuong = (long) line.getSoLuong() + requestedSoLuong;
            if (mergedSoLuong > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tổng số lượng sách trong giỏ vượt giới hạn cho phép.");
            }
            int appliedSoLuong = Math.min((int) mergedSoLuong, sach.getSoLuong());
            line.setSoLuong(appliedSoLuong);
            gioHangRepository.save(line);
            response.setMergedCount(response.getMergedCount() + 1);
            if (appliedSoLuong != mergedSoLuong) {
                response.getAdjustedItems().add(new CartLineAdjustmentResponse(
                        sach.getMaSach(),
                        sach.getTenSach(),
                        (int) mergedSoLuong,
                        appliedSoLuong,
                        "CAPPED_TO_STOCK"
                ));
            }
        }

        applySummary(response, getCurrentUserCartLines(nguoiDung));
        return response;
    }

    private NguoiDung getCurrentUser() {
        String username = getAuthenticatedUsername();
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(username);
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Người dùng không tồn tại.");
        }
        return nguoiDung;
    }

    private NguoiDung getCurrentUserForWrite() {
        NguoiDung nguoiDung = nguoiDungRepository
                .findByTenDangNhapForCartWrite(getAuthenticatedUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Người dùng không tồn tại."));
        if (!Boolean.TRUE.equals(nguoiDung.getDaKichHoat())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Tài khoản không còn hoạt động.");
        }
        return nguoiDung;
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        return authentication.getName();
    }

    private Sach getValidBook(Integer maSach) {
        validateBookId(maSach);
        Sach sach = sachRepository.findByIdForCartWrite(maSach)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Sách không tồn tại."));
        if (!isBookActive(sach)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sách hiện không khả dụng.");
        }
        if (sach.getSoLuong() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách đã hết hàng.");
        }
        return sach;
    }

    private void validateBookId(Integer maSach) {
        if (maSach == null || maSach <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sách không hợp lệ.");
        }
    }

    private boolean isBookActive(Sach sach) {
        return sach.getIsActive() == null || sach.getIsActive() == 1;
    }

    private int normalizePositiveQuantity(Integer soLuong, boolean requireAtLeastOne) {
        if (soLuong == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu số lượng.");
        }
        if (requireAtLeastOne && soLuong < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng phải lớn hơn hoặc bằng 1.");
        }
        if (!requireAtLeastOne && soLuong < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng không hợp lệ.");
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
        Map<Integer, Long> totals = new TreeMap<>();
        if (request == null || request.getItems() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Danh sách sản phẩm merge không được để trống.");
        }
        if (request.getItems().size() > MAX_MERGE_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giỏ hàng merge không được vượt quá 100 dòng.");
        }
        for (CartItemRequest item : request.getItems()) {
            if (item == null || item.getMaSach() == null || item.getMaSach() <= 0
                    || item.getSoLuong() == null || item.getSoLuong() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mỗi sản phẩm merge phải có mã sách và số lượng dương.");
            }
            long total = totals.getOrDefault(item.getMaSach(), 0L) + item.getSoLuong();
            if (total > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tổng số lượng sách merge vượt giới hạn cho phép.");
            }
            totals.put(item.getMaSach(), total);
        }

        Map<Integer, Integer> grouped = new TreeMap<>();
        totals.forEach((maSach, soLuong) -> grouped.put(maSach, soLuong.intValue()));
        return grouped;
    }

    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thiếu Idempotency-Key cho thao tác merge giỏ hàng.");
        }
        String key = raw.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key không hợp lệ.");
        }
        return key;
    }

    private String computeFingerprint(Map<Integer, Integer> guestItems) {
        StringBuilder canonical = new StringBuilder();
        guestItems.forEach((maSach, soLuong) -> canonical
                .append(maSach).append(':').append(soLuong).append(';'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String writeStoredResponse(CartMergeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu kết quả merge giỏ hàng.", exception);
        }
    }

    private CartMergeResponse readStoredResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, CartMergeResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể đọc kết quả merge giỏ hàng.", exception);
        }
    }

    private List<GioHang> getCurrentUserCartLines(NguoiDung nguoiDung) {
        return gioHangRepository.findByMaNguoiDung(nguoiDung.getMaNguoiDung());
    }

    private CartSummaryResponse buildSummary(List<GioHang> lines) {
        CartSummaryResponse response = new CartSummaryResponse();
        applySummary(response, lines);
        return response;
    }

    private void applySummary(CartSummaryResponse response, List<GioHang> lines) {
        List<CartItemResponse> items = new ArrayList<>();
        long tongSoLuong = 0;
        double tongTien = 0;
        List<GioHang> sortedLines = new ArrayList<>(lines);
        sortedLines.sort(Comparator.comparingInt(GioHang::getMaGioHang));
        for (GioHang line : sortedLines) {
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
            tongSoLuong = Math.min(Integer.MAX_VALUE, tongSoLuong + appliedSoLuong);
            tongTien += sach.getGiaBan() * appliedSoLuong;
        }
        response.setItems(items);
        response.setTongSoLuong((int) tongSoLuong);
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
