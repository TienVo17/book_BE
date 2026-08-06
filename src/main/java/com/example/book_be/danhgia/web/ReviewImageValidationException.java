package com.example.book_be.danhgia.web;

/**
 * Anh dinh kem khong hop le, kem MOT ma loi phan biet duoc cho tung nguyen nhan.
 *
 * <p>Handler toan cuc gop moi {@code IllegalArgumentException} vao mot ma
 * {@code VALIDATION_ERROR} duy nhat, nen giao dien khong the noi "anh thu 6" khac gi
 * "anh qua nang" hay "tep khong phai anh". Bon tinh huong nay doi bon cau tra loi khac nhau.
 */
public class ReviewImageValidationException extends RuntimeException {

    public enum Ma {
        /** Da du so anh toi da cho mot danh gia. */
        REVIEW_IMAGE_TOO_MANY,
        /** Anh vuot qua gioi han kich thuoc rieng cua danh gia. */
        REVIEW_IMAGE_TOO_LARGE,
        /** Noi dung that khong phai JPEG/PNG/WebP, bat ke duoi tep hay Content-Type. */
        REVIEW_IMAGE_UNSUPPORTED_TYPE,
        /** Tep rong. */
        REVIEW_IMAGE_EMPTY,
        /** Da dung het han ngach anh tron doi. */
        REVIEW_IMAGE_QUOTA_EXCEEDED
    }

    private final Ma ma;

    public ReviewImageValidationException(Ma ma, String thongDiep) {
        super(thongDiep);
        this.ma = ma;
    }

    public Ma getMa() {
        return ma;
    }
}
