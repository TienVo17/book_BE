package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.domain.NguoiDung;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService{
    public NguoiDung findByUsername(String tenDangNhap);
}
