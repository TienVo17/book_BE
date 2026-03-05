package com.example.book_be.services;

import com.example.book_be.entity.NguoiDung;

public interface NguoiDungService {
    NguoiDung getHoSo(String tenDangNhap);
    NguoiDung capNhatHoSo(String tenDangNhap, NguoiDung updates);
}
