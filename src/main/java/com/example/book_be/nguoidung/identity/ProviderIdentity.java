package com.example.book_be.nguoidung.identity;

/**
 * Danh tinh toi thieu lay duoc tu mot provider sau khi da xac minh xong token.
 *
 * Khong mang theo access/refresh/id token cua provider: sau buoc xac minh, ung dung chi
 * can biet "ai" chu khong goi tiep API nao cua provider.
 */
public record ProviderIdentity(
        String provider,
        String issuer,
        String subject,
        String email,
        boolean emailVerified,
        String displayName) {

    /**
     * Email chi dung duoc khi provider khang dinh da xac minh. Mot dia chi chua xac minh
     * khong chung minh duoc nguoi dang dang nhap kiem soat hom thu do.
     */
    public String trustedEmail() {
        return emailVerified && email != null && !email.isBlank() ? email : null;
    }
}
