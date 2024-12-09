package com.example.book_be.bo;

import lombok.Data;

@Data
public class UserBo extends BaseBo {

    private int maNguoiDung;

    private String hoDem;

    private String ten;

    private String tenDangNhap;

    private String matKhau;

    private char gioiTinh;

    private String email;

    private String soDienThoai;

    private String diaChiMuaHang;

    private String diaChiGiaoHang;

    private Boolean daKichHoat = false;

    private String maKichHoat;
}
