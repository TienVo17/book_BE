package com.example.book_be.danhgia.domain;

/**
 * Trang thai kiem duyet cua mot danh gia.
 *
 * <p>Thay cho co Integer mo ho truoc day (1 = hien, moi gia tri khac = an,
 * NULL = khong ai biet). Chi danh gia o {@link #HIEN_THI} moi duoc tinh vao diem trung binh
 * cua sach va moi duoc tra ra duong cong khai.
 */
public enum TrangThaiDanhGia {
    HIEN_THI,
    DA_AN
}
