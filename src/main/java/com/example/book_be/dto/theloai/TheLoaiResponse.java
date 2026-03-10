package com.example.book_be.dto.theloai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TheLoaiResponse {
    private Integer maTheLoai;
    private String tenTheLoai;
    private String slug;
    private Long soLuongSach;
}
