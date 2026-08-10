package com.example.book_be.donhang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailLineItemResponse {
    private Integer maSach;
    private String tenSach;
    private Integer soLuong;
    private Double giaBan;
    private Double thanhTien;
}
