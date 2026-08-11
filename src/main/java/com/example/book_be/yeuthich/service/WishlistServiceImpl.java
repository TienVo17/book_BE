package com.example.book_be.yeuthich.service;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.yeuthich.domain.SachYeuThich;
import com.example.book_be.yeuthich.dto.WishlistItemResponse;
import com.example.book_be.yeuthich.repository.SachYeuThichRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final SachYeuThichRepository sachYeuThichRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final SachRepository sachRepository;

    public WishlistServiceImpl(
            SachYeuThichRepository sachYeuThichRepository,
            NguoiDungRepository nguoiDungRepository,
            SachRepository sachRepository
    ) {
        this.sachYeuThichRepository = sachYeuThichRepository;
        this.nguoiDungRepository = nguoiDungRepository;
        this.sachRepository = sachRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getCurrentUserWishlist() {
        return getSnapshot(getCurrentUser().getMaNguoiDung());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<WishlistItemResponse> ensureBookPresent(Integer maSach) {
        validateBookId(maSach);
        NguoiDung nguoiDung = getCurrentUserForWrite();
        Sach sach = sachRepository.findById(maSach.longValue())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Sách không tồn tại."));

        if (!sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(
                nguoiDung.getMaNguoiDung(), maSach)) {
            SachYeuThich yeuThich = new SachYeuThich();
            yeuThich.setNguoiDung(nguoiDung);
            yeuThich.setSach(sach);
            sachYeuThichRepository.saveAndFlush(yeuThich);
        }

        return getSnapshot(nguoiDung.getMaNguoiDung());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<WishlistItemResponse> ensureBookAbsent(Integer maSach) {
        validateBookId(maSach);
        NguoiDung nguoiDung = getCurrentUserForWrite();
        sachYeuThichRepository.deleteByNguoiDung_MaNguoiDungAndSach_MaSach(
                nguoiDung.getMaNguoiDung(), maSach);
        return getSnapshot(nguoiDung.getMaNguoiDung());
    }

    private NguoiDung getCurrentUser() {
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(getAuthenticatedUsername());
        if (nguoiDung == null || !Boolean.TRUE.equals(nguoiDung.getDaKichHoat())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Người dùng không tồn tại hoặc không còn hoạt động.");
        }
        return nguoiDung;
    }

    private NguoiDung getCurrentUserForWrite() {
        int maNguoiDung = nguoiDungRepository
                .findIdByTenDangNhap(getAuthenticatedUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Người dùng không tồn tại."));
        NguoiDung nguoiDung = nguoiDungRepository
                .findByIdForWishlistWrite(maNguoiDung)
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

    private void validateBookId(Integer maSach) {
        if (maSach == null || maSach <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sách không hợp lệ.");
        }
    }

    private List<WishlistItemResponse> getSnapshot(int maNguoiDung) {
        return sachYeuThichRepository.findWishlistSnapshot(maNguoiDung).stream()
                .map(this::toResponse)
                .toList();
    }

    private WishlistItemResponse toResponse(SachYeuThich yeuThich) {
        Sach sach = yeuThich.getSach();
        return new WishlistItemResponse(
                sach.getMaSach(),
                sach.getTenSach(),
                sach.getGiaBan(),
                getBookImage(sach)
        );
    }

    private String getBookImage(Sach sach) {
        if (sach.getListHinhAnh() == null || sach.getListHinhAnh().isEmpty()) {
            return "";
        }
        return sach.getListHinhAnh().stream()
                .filter(image -> image.getUrlHinh() != null
                        && !image.getUrlHinh().isBlank())
                .min(java.util.Comparator.comparingInt(HinhAnh::getMaHinhAnh))
                .map(HinhAnh::getUrlHinh)
                .orElse("");
    }
}
