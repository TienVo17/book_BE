package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.domain.NguoiDung;

public interface NguoiDungService {
    NguoiDung getHoSo(String tenDangNhap);
    NguoiDung capNhatHoSo(String tenDangNhap, NguoiDung updates);
}
