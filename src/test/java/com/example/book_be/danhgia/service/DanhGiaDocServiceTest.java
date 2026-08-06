package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.dto.DanhGiaTrangResponse;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.DanhGiaHuuIchRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DanhGiaDocServiceTest {

    private final SuDanhGiaRepository danhGiaRepository = mock(SuDanhGiaRepository.class);
    private final DanhGiaHuuIchRepository huuIchRepository = mock(DanhGiaHuuIchRepository.class);
    private final DanhGiaHinhAnhRepository hinhAnhRepository = mock(DanhGiaHinhAnhRepository.class);
    private final DanhGiaDocService service =
            new DanhGiaDocService(danhGiaRepository, huuIchRepository, hinhAnhRepository);

    @Test
    void nap_anh_cho_ca_trang_bang_mot_truy_van_va_gan_dung_review() {
        SuDanhGia danhGiaMot = danhGia(1L);
        SuDanhGia danhGiaHai = danhGia(2L);
        when(danhGiaRepository.findBySach_MaSachAndTrangThai(
                any(Integer.class), any(TrangThaiDanhGia.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(danhGiaMot, danhGiaHai), PageRequest.of(0, 10), 2));
        when(danhGiaRepository.demTheoDiem(7))
                .thenReturn(List.<Object[]>of(new Object[]{5F, 2L}));
        when(huuIchRepository.demTheoDanhGia(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(huuIchRepository.timDaBinhChon(9, List.of(1L, 2L)))
                .thenReturn(List.of(2L));

        DanhGiaHinhAnh anh = new DanhGiaHinhAnh();
        anh.setMaHinhAnh(10L);
        anh.setMaDanhGia(1L);
        anh.setUrlHinh("https://cdn.example/review.jpg");
        anh.setCloudinaryPublicId("web-ban-sach/reviews/private-id");
        when(hinhAnhRepository.findByMaDanhGiaInOrderByThuTuAsc(List.of(1L, 2L)))
                .thenReturn(List.of(anh));

        DanhGiaTrangResponse ketQua = service.docTrang(7, 0, 10, "moi-nhat", null, 9);

        assertThat(ketQua.getContent().get(0).getAnhDinhKem())
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.maHinhAnh()).isEqualTo(10L);
                    assertThat(dto.urlHinh()).isEqualTo("https://cdn.example/review.jpg");
                });
        assertThat(ketQua.getContent().get(1).getAnhDinhKem()).isEmpty();
        assertThat(ketQua.getContent().get(1).isToiDaBinhChon()).isTrue();
        verify(hinhAnhRepository).findByMaDanhGiaInOrderByThuTuAsc(List.of(1L, 2L));
    }

    @Test
    void trang_rong_khong_truy_van_vote_hoac_anh() {
        when(danhGiaRepository.findBySach_MaSachAndTrangThai(
                any(Integer.class), any(TrangThaiDanhGia.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(danhGiaRepository.demTheoDiem(7)).thenReturn(List.of());

        DanhGiaTrangResponse ketQua = service.docTrang(7, 0, 10, "moi-nhat", null, 9);

        assertThat(ketQua.getContent()).isEmpty();
        assertThat(ketQua.getTongSo()).isZero();
        assertThat(ketQua.getDiemTrungBinh()).isZero();
        assertThat(ketQua.getPhanBo())
                .containsExactly(
                        java.util.Map.entry(5, 0L),
                        java.util.Map.entry(4, 0L),
                        java.util.Map.entry(3, 0L),
                        java.util.Map.entry(2, 0L),
                        java.util.Map.entry(1, 0L));
        verifyNoInteractions(huuIchRepository, hinhAnhRepository);
    }

    private SuDanhGia danhGia(long maDanhGia) {
        SuDanhGia danhGia = new SuDanhGia();
        danhGia.setMaDanhGia(maDanhGia);
        return danhGia;
    }
}
