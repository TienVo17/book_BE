package com.example.book_be.sach.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SachTonKhoDieuChinhRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chap_nhan_so_nguyen_trong_mien_int() throws Exception {
        SachTonKhoDieuChinhRequest request = objectMapper.readValue(
                "{\"soLuongThayDoi\":-5}", SachTonKhoDieuChinhRequest.class);

        assertThat(request.getSoLuongThayDoi()).isEqualTo(-5);
    }

    @Test
    void giu_null_de_service_tra_400_on_dinh() throws Exception {
        SachTonKhoDieuChinhRequest missing = objectMapper.readValue(
                "{}", SachTonKhoDieuChinhRequest.class);
        SachTonKhoDieuChinhRequest explicitNull = objectMapper.readValue(
                "{\"soLuongThayDoi\":null}", SachTonKhoDieuChinhRequest.class);

        assertThat(missing.getSoLuongThayDoi()).isNull();
        assertThat(explicitNull.getSoLuongThayDoi()).isNull();
    }

    @Test
    void tu_choi_so_thap_phan_scientific_fraction_text_boolean_va_ngoai_int() {
        for (String value : new String[]{"1.5", "1e-1", "\"5\"", "true", "2147483648", "-2147483649"}) {
            assertThatThrownBy(() -> objectMapper.readValue(
                    "{\"soLuongThayDoi\":" + value + "}", SachTonKhoDieuChinhRequest.class))
                    .as("value %s must be rejected", value)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }
}
