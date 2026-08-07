package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SachRepository van duoc Spring Data REST export (path "sach") de {@code GET /sach/{id}} tiep
 * tuc doc duoc cong khai. Nhung truoc ban va, MOI query method rieng cua no (vi du
 * {@code findAllByIsActive}, {@code truKhoNeuDu}) lai phoi ra duoi {@code /sach/search/*} ma
 * KHONG di qua bo loc isActive/quyen han cua tang controller:
 *
 * <ul>
 *   <li>{@code GET /sach/search/findAllByIsActive?isActive=0} tra ve TOAN BO sach admin da an;</li>
 *   <li>{@code GET /sach/search/truKhoNeuDu?maSach=1&soLuong=0} goi thang cau
 *       {@code @Modifying UPDATE} doi ton kho va tra HTTP 500 (khong ai bat exception).</li>
 * </ul>
 *
 * <p>Sau khi moi query method mang {@code @RestResource(exported = false)}, ca hai duong nay
 * phai dong lai, va {@code GET /sach/{id}} van phai tra 200 (ReviewExposureIT da khang dinh
 * dieu nay o goc do khac; test o day khang dinh lai de gan lien voi thay doi cua SachRepository).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SachRepositorySearchExposureIT {

    @Autowired TestRestTemplate rest;

    /**
     * Spring Data REST tra ve {@code org.springframework.data.rest.webmvc.ResourceNotFoundException}
     * khi tai nguyen search khong con query method nao duoc export — ApiExceptionHandler da co
     * san handler cho chinh exception nay va ghi ro no ap dung cho ca truong hop tuong tu
     * ({@code /sach/{id}/listHinhAnh} tu HinhAnhRepository, da la exported = false tu truoc).
     * Sau ban va, {@code /sach/search} phai di theo dung duong do: 404, khong con quang cao ten
     * bat ky query method nao trong body.
     */
    @Test
    void duong_tim_kiem_repository_khong_con_phoi_bat_ky_query_method_nao() {
        ResponseEntity<String> response = rest.getForEntity("/sach/search", String.class);
        String body = response.getBody() == null ? "" : response.getBody();

        assertThat(response.getStatusCode().value())
                .as("tai nguyen search khong con query method nao phai tra 404, giong /sach/{id}/listHinhAnh")
                .isEqualTo(404);
        assertThat(body)
                .as("body khong duoc quang cao ten bat ky query method nao")
                .doesNotContain("findAllByIsActive")
                .doesNotContain("findBySlug")
                .doesNotContain("existsBySlug")
                .doesNotContain("findBanChay")
                .doesNotContain("findByIsActiveOrderByMaSachDesc")
                .doesNotContain("findLienQuan")
                .doesNotContain("findGoiY")
                .doesNotContain("truKhoNeuDu")
                .doesNotContain("hoanKho")
                .doesNotContain("tangTonKhoNeuKhongVuotQua")
                .doesNotContain("giamTonKhoNeuDu")
                .doesNotContain("findSoLuongByMaSach")
                .doesNotContain("khoaSachDeCapNhat");
    }

    @Test
    void sach_da_an_khong_con_liet_ke_duoc_qua_repository_search() {
        ResponseEntity<String> response = rest.getForEntity(
                "/sach/search/findAllByIsActive?isActive=0", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("/sach/search/findAllByIsActive khong con lo sach da an ra ngoai")
                .isFalse();
    }

    @Test
    void tru_kho_khong_con_goi_duoc_tu_ben_ngoai_qua_repository_search() {
        ResponseEntity<String> response = rest.getForEntity(
                "/sach/search/truKhoNeuDu?maSach=1&soLuong=0", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("khong con duong nao tu ben ngoai goi duoc cau UPDATE ton kho")
                .isFalse();
        assertThat(response.getStatusCode().value())
                .as("dong dung cach phai tra loi ro rang (vi du 404), khong phai 500 loi khong ai bat")
                .isNotEqualTo(500);
    }

    /** Chong hoi quy: dong query method KHONG duoc keo theo dong luon tai nguyen sach. */
    @Test
    void tai_nguyen_sach_theo_id_van_doc_duoc_cong_khai_sau_khi_dong_query_method() {
        ResponseEntity<String> response = rest.getForEntity("/sach/1", String.class);

        assertThat(response.getStatusCode().value())
                .as("tai nguyen sach van phai doc duoc cong khai")
                .isEqualTo(200);
    }
}
