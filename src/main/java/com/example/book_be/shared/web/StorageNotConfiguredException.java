package com.example.book_be.shared.web;

/**
 * Luu tru anh ngoai chua duoc cau hinh.
 *
 * <p>Co kieu rieng thay vi {@code IllegalStateException} vi handler toan cuc map
 * {@code IllegalStateException} sang mot ma co dinh va VUT BO message — nen khong the
 * noi cho nguoi van hanh biet thieu dung bien nao.
 */
public class StorageNotConfiguredException extends RuntimeException {

    public StorageNotConfiguredException(String bienMoiTruong) {
        super("Chưa cấu hình lưu trữ ảnh. Cần đặt biến môi trường " + bienMoiTruong + ".");
    }
}
