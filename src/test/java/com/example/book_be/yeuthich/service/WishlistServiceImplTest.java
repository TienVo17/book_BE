package com.example.book_be.yeuthich.service;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.yeuthich.domain.SachYeuThich;
import com.example.book_be.yeuthich.dto.WishlistItemResponse;
import com.example.book_be.yeuthich.repository.SachYeuThichRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock SachYeuThichRepository sachYeuThichRepository;
    @Mock NguoiDungRepository nguoiDungRepository;
    @Mock SachRepository sachRepository;

    private WishlistServiceImpl service;
    private NguoiDung user;
    private Sach book;

    @BeforeEach
    void setUp() {
        service = new WishlistServiceImpl(
                sachYeuThichRepository,
                nguoiDungRepository,
                sachRepository
        );
        user = new NguoiDung();
        user.setMaNguoiDung(7);
        user.setTenDangNhap("wishlist-owner");
        user.setDaKichHoat(true);
        book = new Sach();
        book.setMaSach(3);
        book.setTenSach("Sách yêu thích");
        book.setGiaBan(75000D);
        book.setIsActive(0);
        book.setSoLuong(0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wishlist-owner", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_lap_lai_khong_insert_duplicate_va_tra_snapshot_phang() {
        SachYeuThich row = wishlistRow();
        when(nguoiDungRepository.findIdByTenDangNhap("wishlist-owner"))
                .thenReturn(Optional.of(7));
        when(nguoiDungRepository.findByIdForWishlistWrite(7))
                .thenReturn(Optional.of(user));
        when(sachRepository.findById(3L)).thenReturn(Optional.of(book));
        when(sachYeuThichRepository
                .existsByNguoiDung_MaNguoiDungAndSach_MaSach(7, 3))
                .thenReturn(false, true);
        when(sachYeuThichRepository.findWishlistSnapshot(7)).thenReturn(List.of(row));

        List<WishlistItemResponse> first = service.ensureBookPresent(3);
        List<WishlistItemResponse> replay = service.ensureBookPresent(3);

        assertThat(first).singleElement().satisfies(item -> {
            assertThat(item.maSach()).isEqualTo(3);
            assertThat(item.tenSach()).isEqualTo("Sách yêu thích");
            assertThat(item.giaBan()).isEqualTo(75000D);
            assertThat(item.hinhAnh()).isEmpty();
        });
        assertThat(replay).isEqualTo(first);
        verify(sachYeuThichRepository, times(1)).saveAndFlush(any(SachYeuThich.class));
    }

    @Test
    void delete_lap_lai_luon_ensure_absent_va_tra_snapshot_authoritative() {
        when(nguoiDungRepository.findIdByTenDangNhap("wishlist-owner"))
                .thenReturn(Optional.of(7));
        when(nguoiDungRepository.findByIdForWishlistWrite(7))
                .thenReturn(Optional.of(user));
        when(sachYeuThichRepository.findWishlistSnapshot(7)).thenReturn(List.of());

        assertThat(service.ensureBookAbsent(3)).isEmpty();
        assertThat(service.ensureBookAbsent(3)).isEmpty();

        verify(sachYeuThichRepository, times(2))
                .deleteByNguoiDung_MaNguoiDungAndSach_MaSach(7, 3);
    }

    @Test
    void snapshot_chon_anh_dau_tien_hop_le() {
        HinhAnh empty = new HinhAnh();
        empty.setUrlHinh("  ");
        HinhAnh valid = new HinhAnh();
        valid.setUrlHinh("https://image.example/book.jpg");
        book.setListHinhAnh(List.of(empty, valid));
        when(nguoiDungRepository.findByTenDangNhap("wishlist-owner"))
                .thenReturn(user);
        when(sachYeuThichRepository.findWishlistSnapshot(7))
                .thenReturn(List.of(wishlistRow()));

        assertThat(service.getCurrentUserWishlist())
                .singleElement()
                .extracting(WishlistItemResponse::hinhAnh)
                .isEqualTo("https://image.example/book.jpg");
    }

    @Test
    void sach_khong_ton_tai_tra_404_truoc_insert() {
        when(nguoiDungRepository.findIdByTenDangNhap("wishlist-owner"))
                .thenReturn(Optional.of(7));
        when(nguoiDungRepository.findByIdForWishlistWrite(7))
                .thenReturn(Optional.of(user));
        when(sachRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureBookPresent(999))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(sachYeuThichRepository, never()).saveAndFlush(any());
    }

    @Test
    void ma_sach_loi_bi_tu_choi_truoc_user_lock() {
        assertThatThrownBy(() -> service.ensureBookPresent(0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.ensureBookAbsent(-1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(nguoiDungRepository, never())
                .findByIdForWishlistWrite(anyInt());
    }

    @Test
    void tai_khoan_vo_hieu_hoa_bi_tu_choi_truoc_mutation() {
        user.setDaKichHoat(false);
        when(nguoiDungRepository.findIdByTenDangNhap("wishlist-owner"))
                .thenReturn(Optional.of(7));
        when(nguoiDungRepository.findByIdForWishlistWrite(7))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.ensureBookPresent(3))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(sachRepository, never()).findById(any());
        verify(sachYeuThichRepository, never()).saveAndFlush(any());
    }

    private SachYeuThich wishlistRow() {
        SachYeuThich row = new SachYeuThich();
        row.setMaSachYeuThich(11);
        row.setNguoiDung(user);
        row.setSach(book);
        return row;
    }
}
