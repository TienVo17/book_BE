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

    public static final byte SINGLETON_ID = 1;

    /**
     * Cot la TINYINT trong V10. Entity phai khai bao Byte cho khop, neu khong
     * ddl-auto=validate se tu choi khoi dong ung dung.
     */
    @Id
    @Column(name = "singleton_id")
    private Byte singletonId;

    @Column(name = "da_su_dung", nullable = false)
    private boolean daSuDung;

    @Column(name = "thoi_diem_su_dung")
    private Date thoiDiemSuDung;

    @Column(name = "ten_dang_nhap_da_tao")
    private String tenDangNhapDaTao;

    protected AdminBootstrapState() {
    }

    public Byte getSingletonId() {
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
