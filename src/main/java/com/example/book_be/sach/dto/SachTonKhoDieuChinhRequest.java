package com.example.book_be.sach.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class SachTonKhoDieuChinhRequest {

    private final Integer soLuongThayDoi;

    @JsonCreator
    public SachTonKhoDieuChinhRequest(@JsonProperty("soLuongThayDoi") JsonNode soLuongThayDoi) {
        if (soLuongThayDoi == null || soLuongThayDoi.isNull()) {
            this.soLuongThayDoi = null;
            return;
        }
        if (!soLuongThayDoi.isIntegralNumber() || !soLuongThayDoi.canConvertToInt()) {
            throw new IllegalArgumentException("Số lượng thay đổi phải là số nguyên hợp lệ");
        }
        this.soLuongThayDoi = soLuongThayDoi.intValue();
    }

    public Integer getSoLuongThayDoi() {
        return soLuongThayDoi;
    }
}
