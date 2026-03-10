package com.example.book_be.dto.theloai;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TheLoaiAdminResponse extends TheLoaiResponse {
    private boolean coTheXoa;

    public TheLoaiAdminResponse(Integer maTheLoai, String tenTheLoai, String slug, Long soLuongSach, boolean coTheXoa) {
        super(maTheLoai, tenTheLoai, slug, soLuongSach);
        this.coTheXoa = coTheXoa;
    }
}
