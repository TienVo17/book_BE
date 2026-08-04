package com.example.book_be.nguoidung.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Date;

/**
 * Dong singleton danh dau bootstrap admin da duoc su dung hay chua. Khoa ghi tren dong nay
 * la diem tuan tu hoa duy nhat giua nhieu instance cung khoi dong.
 */
@Entity
@Table(name = "admin_bootstrap_state")
public class AdminBootstrapState {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "singleton_id")
    private Integer singletonId;

    @Column(name = "da_su_dung", nullable = false)
    private boolean daSuDung;

    @Column(name = "thoi_diem_su_dung")
    private Date thoiDiemSuDung;

    @Column(name = "ten_dang_nhap_da_tao")
    private String tenDangNhapDaTao;

    protected AdminBootstrapState() {
    }

    public Integer getSingletonId() {
        return singletonId;
    }

    public boolean isDaSuDung() {
        return daSuDung;
    }

    public Date getThoiDiemSuDung() {
        return thoiDiemSuDung;
    }

    public String getTenDangNhapDaTao() {
        return tenDangNhapDaTao;
    }

    public void danhDauDaSuDung(String tenDangNhap) {
        this.daSuDung = true;
        this.thoiDiemSuDung = new Date();
        this.tenDangNhapDaTao = tenDangNhap;
    }
}
