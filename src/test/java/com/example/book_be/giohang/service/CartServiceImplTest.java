package com.example.book_be.giohang.service;

import com.example.book_be.giohang.domain.GioHang;
import com.example.book_be.giohang.domain.GioHangMergeReceipt;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.giohang.dto.CartMergeRequest;
import com.example.book_be.giohang.dto.CartMergeResponse;
import com.example.book_be.giohang.repository.GioHangMergeReceiptRepository;
import com.example.book_be.giohang.repository.GioHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.shared.security.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock NguoiDungRepository nguoiDungRepository;
    @Mock GioHangRepository gioHangRepository;
    @Mock GioHangMergeReceiptRepository mergeReceiptRepository;
    @Mock SachRepository sachRepository;

    private CartServiceImpl service;
    private RateLimiter rateLimiter;
    private NguoiDung user;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
        service = new CartServiceImpl(
                nguoiDungRepository,
                gioHangRepository,
                mergeReceiptRepository,
                sachRepository,
                new ObjectMapper(),
                rateLimiter
        );
        user = new NguoiDung();
        user.setMaNguoiDung(7);
        user.setTenDangNhap("cart-owner");
        user.setDaKichHoat(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cart-owner", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retry_cung_key_replay_snapshot_khong_cong_quantity_lan_hai() {
        Sach sach = sach(1, 20);
        GioHang line = line(sach, 3);
        AtomicReference<GioHangMergeReceipt> storedReceipt = new AtomicReference<>();
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));
        when(sachRepository.findByIdForCartWrite(1)).thenReturn(Optional.of(sach));
        when(gioHangRepository.findByMaNguoiDungAndMaSach(7, 1))
                .thenReturn(Optional.of(line));
        when(gioHangRepository.findByMaNguoiDung(7)).thenReturn(List.of(line));
        when(mergeReceiptRepository.findByNguoiDung_MaNguoiDungAndIdempotencyKey(7, "stable-key"))
                .thenAnswer(ignored -> Optional.ofNullable(storedReceipt.get()));
        when(mergeReceiptRepository.save(any(GioHangMergeReceipt.class)))
                .thenAnswer(invocation -> {
                    GioHangMergeReceipt receipt = invocation.getArgument(0);
                    storedReceipt.set(receipt);
                    return receipt;
                });

        CartMergeRequest request = new CartMergeRequest(List.of(new CartItemRequest(1, 2)));
        CartMergeResponse first = service.mergeGuestCart(request, "stable-key");
        CartMergeResponse retry = service.mergeGuestCart(request, "stable-key");

        assertThat(first.getItems()).singleElement().extracting("soLuong").isEqualTo(5);
        assertThat(retry).isEqualTo(first);
        assertThat(line.getSoLuong()).isEqualTo(5);
        verify(gioHangRepository, times(1)).save(line);
        verify(mergeReceiptRepository, times(1)).save(any(GioHangMergeReceipt.class));
    }

    @Test
    void cung_key_payload_khac_tra_409_truoc_cart_mutation() {
        GioHangMergeReceipt receipt = new GioHangMergeReceipt();
        receipt.setRequestFingerprint("0".repeat(64));
        receipt.setResponseJson("{}");
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));
        when(mergeReceiptRepository.findByNguoiDung_MaNguoiDungAndIdempotencyKey(7, "used-key"))
                .thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.mergeGuestCart(
                new CartMergeRequest(List.of(new CartItemRequest(1, 3))), "used-key"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

        verify(sachRepository, never()).findByIdForCartWrite(anyInt());
        verify(gioHangRepository, never()).save(any());
    }

    @Test
    void payload_loi_hoac_overflow_bi_tu_choi_khong_mutation() {
        List<CartMergeRequest> invalidRequests = new java.util.ArrayList<>();
        invalidRequests.add(null);
        invalidRequests.add(new CartMergeRequest(null));
        invalidRequests.add(new CartMergeRequest(List.of(new CartItemRequest(null, 1))));
        invalidRequests.add(new CartMergeRequest(List.of(new CartItemRequest(1, 0))));
        invalidRequests.add(new CartMergeRequest(List.of(
                new CartItemRequest(1, Integer.MAX_VALUE),
                new CartItemRequest(1, 1))));

        for (CartMergeRequest request : invalidRequests) {
            assertThatThrownBy(() -> service.mergeGuestCart(request, "valid-key"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode())
                                    .isEqualTo(HttpStatus.BAD_REQUEST));
        }

        verify(nguoiDungRepository, never()).findByTenDangNhapForCartWrite(any());
        verify(gioHangRepository, never()).save(any());
        verify(mergeReceiptRepository, never()).save(any());
    }

    @Test
    void payload_vuot_gioi_han_line_bi_tu_choi_truoc_user_lock() {
        List<CartItemRequest> items = IntStream.rangeClosed(1, 101)
                .mapToObj(maSach -> new CartItemRequest(maSach, 1))
                .toList();

        assertThatThrownBy(() -> service.mergeGuestCart(
                new CartMergeRequest(items), "too-many-lines-key"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(nguoiDungRepository, never()).findByTenDangNhapForCartWrite(any());
        verify(sachRepository, never()).findByIdForCartWrite(anyInt());
        verify(gioHangRepository, never()).save(any());
        verify(mergeReceiptRepository, never()).save(any());
    }

    @Test
    void key_thieu_hoac_khong_hop_le_bi_tu_choi_truoc_user_lock() {
        CartMergeRequest request = new CartMergeRequest(List.of(new CartItemRequest(1, 1)));

        assertThatThrownBy(() -> service.mergeGuestCart(request, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.mergeGuestCart(request, "   "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.mergeGuestCart(request, "bad key !"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.mergeGuestCart(request, "a".repeat(101)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(nguoiDungRepository, never()).findByTenDangNhapForCartWrite(any());
    }

    @Test
    void qua_nhieu_merge_moi_bi_tu_choi_nhung_retry_receipt_van_duoc_replay() {
        GioHangMergeReceipt receipt = new GioHangMergeReceipt();
        receipt.setRequestFingerprint("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        receipt.setResponseJson("{\"items\":[],\"tongSoLuong\":0,\"tongTien\":0.0,\"mergedCount\":0,\"adjustedItems\":[],\"removedItems\":[]}");
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));
        when(mergeReceiptRepository.findByNguoiDung_MaNguoiDungAndIdempotencyKey(
                org.mockito.ArgumentMatchers.eq(7), any()))
                .thenAnswer(invocation -> "replay-key".equals(invocation.getArgument(1))
                        ? Optional.of(receipt)
                        : Optional.empty());

        CartMergeRequest emptyCart = new CartMergeRequest(List.of());
        for (int request = 0; request < 30; request++) {
            assertThat(rateLimiter.choPhep(
                    "cart-merge:7", 30, java.time.Duration.ofMinutes(10)))
                    .isTrue();
        }

        assertThatThrownBy(() -> service.mergeGuestCart(emptyCart, "new-key"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        CartMergeResponse replay = service.mergeGuestCart(emptyCart, "replay-key");

        assertThat(replay.getItems()).isEmpty();
        verify(mergeReceiptRepository, never()).save(any());
    }

    @Test
    void duplicate_guest_lines_duoc_gom_theo_ma_sach_truoc_merge() {
        Sach sach = sach(1, 20);
        GioHang line = line(sach, 1);
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));
        when(sachRepository.findByIdForCartWrite(1)).thenReturn(Optional.of(sach));
        when(gioHangRepository.findByMaNguoiDungAndMaSach(7, 1))
                .thenReturn(Optional.of(line));
        when(gioHangRepository.findByMaNguoiDung(7)).thenReturn(List.of(line));

        CartMergeResponse response = service.mergeGuestCart(
                new CartMergeRequest(List.of(
                        new CartItemRequest(1, 2),
                        new CartItemRequest(1, 3))),
                "duplicate-lines-key"
        );

        assertThat(response.getMergedCount()).isOne();
        assertThat(line.getSoLuong()).isEqualTo(6);
        verify(gioHangRepository, times(1)).save(line);
    }

    @Test
    void add_dung_long_de_khong_overflow_int() {
        Sach sach = sach(1, Integer.MAX_VALUE);
        GioHang line = line(sach, Integer.MAX_VALUE);
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));
        when(sachRepository.findByIdForCartWrite(1)).thenReturn(Optional.of(sach));
        when(gioHangRepository.findByMaNguoiDungAndMaSach(7, 1))
                .thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.addItem(new CartItemRequest(1, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(line.getSoLuong()).isEqualTo(Integer.MAX_VALUE);
        verify(gioHangRepository, never()).save(any());
    }

    @Test
    void cart_write_tu_choi_tai_khoan_bi_vo_hieu_hoa_sau_xac_thuc() {
        user.setDaKichHoat(false);
        when(nguoiDungRepository.findByTenDangNhapForCartWrite("cart-owner"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.addItem(new CartItemRequest(1, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(sachRepository, never()).findByIdForCartWrite(anyInt());
        verify(gioHangRepository, never()).save(any());
    }

    private Sach sach(int maSach, int soLuong) {
        Sach sach = new Sach();
        sach.setMaSach(maSach);
        sach.setTenSach("Cart fixture");
        sach.setGiaBan(10000D);
        sach.setSoLuong(soLuong);
        sach.setIsActive(1);
        return sach;
    }

    private GioHang line(Sach sach, int soLuong) {
        GioHang line = new GioHang();
        line.setMaGioHang(11);
        line.setNguoiDung(user);
        line.setSach(sach);
        line.setSoLuong(soLuong);
        return line;
    }
}
