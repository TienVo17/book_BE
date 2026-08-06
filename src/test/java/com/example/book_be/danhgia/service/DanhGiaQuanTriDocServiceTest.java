package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.dto.DanhGiaQuanTriResponse;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DanhGiaQuanTriDocServiceTest {

    private final SuDanhGiaRepository danhGiaRepository = mock(SuDanhGiaRepository.class);
    private final DanhGiaHinhAnhRepository hinhAnhRepository = mock(DanhGiaHinhAnhRepository.class);
    private final DanhGiaQuanTriDocService service =
            new DanhGiaQuanTriDocService(danhGiaRepository, hinhAnhRepository);

    @Test
    void napAnhChoCaTrangBangMotTruyVanVaKhongLoPublicId() {
        SuDanhGia danhGiaMot = danhGia(1L);
        SuDanhGia danhGiaHai = danhGia(2L);
        when(danhGiaRepository.timTrangChoQuanTri(any()))
                .thenReturn(new PageImpl<>(List.of(danhGiaMot, danhGiaHai), PageRequest.of(0, 10), 2));

        DanhGiaHinhAnh anh = new DanhGiaHinhAnh();
        anh.setMaHinhAnh(10L);
        anh.setMaDanhGia(1L);
        anh.setUrlHinh("https://cdn.example/review.jpg");
        anh.setCloudinaryPublicId("web-ban-sach/reviews/private-id");
        when(hinhAnhRepository.findByMaDanhGiaInOrderByThuTuAsc(List.of(1L, 2L)))
                .thenReturn(List.of(anh));

        Page<DanhGiaQuanTriResponse> ketQua = service.docTrang(0);

        assertThat(ketQua.getContent().get(0).getAnhDinhKem())
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.maHinhAnh()).isEqualTo(10L);
                    assertThat(dto.urlHinh()).isEqualTo("https://cdn.example/review.jpg");
                });
        assertThat(ketQua.getContent().get(1).getAnhDinhKem()).isEmpty();
        verify(hinhAnhRepository).findByMaDanhGiaInOrderByThuTuAsc(List.of(1L, 2L));
    }

    @Test
    void trangRongKhongTruyVanAnh() {
        when(danhGiaRepository.timTrangChoQuanTri(any())).thenReturn(Page.empty());

        assertThat(service.docTrang(-1)).isEmpty();

        verify(danhGiaRepository).timTrangChoQuanTri(PageRequest.of(0, 10));
        verifyNoInteractions(hinhAnhRepository);
    }

    private SuDanhGia danhGia(long maDanhGia) {
        SuDanhGia danhGia = new SuDanhGia();
        danhGia.setMaDanhGia(maDanhGia);
        return danhGia;
    }
}
