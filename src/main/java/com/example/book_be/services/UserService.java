package com.example.book_be.services;

import com.example.book_be.entity.NguoiDung;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService{
    public NguoiDung findByUsername(String tenDangNhap);
}
