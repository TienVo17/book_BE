package com.example.book_be.sach.dto;

import com.example.book_be.sach.domain.Sach;

/**
 * DTO gon rieng cho goi y khi go tim kiem (typeahead). KHONG tai su dung SachResponse day du:
 * dropdown goi y chi can hien thi anh/ten/gia, khong nen lo mo ta, ton kho, danh gia... cho
 * moi ky tu nguoi dung go.
 */
public record SachGoiYResponse(int maSach, String tenSach, String slug, String urlAnh, double giaBan) {

    public static SachGoiYResponse from(Sach sach, String urlAnh) {
        return new SachGoiYResponse(sach.getMaSach(), sach.getTenSach(), sach.getSlug(), urlAnh, sach.getGiaBan());
    }
}
