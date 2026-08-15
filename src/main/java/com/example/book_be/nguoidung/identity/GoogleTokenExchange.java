package com.example.book_be.nguoidung.identity;

import java.util.Map;

/**
 * Doi authorization code lay claim danh tinh.
 *
 * Tra ve claim da giai ma thay vi tra token: access/refresh token cua Google khong duoc roi
 * khoi buoc nay. Ung dung chi can biet "ai", khong goi tiep API nao cua Google, nen giu lai
 * token chi tao them mot thu de bi danh cap.
 */
public interface GoogleTokenExchange {
    Map<String, Object> exchange(String authorizationCode, String codeVerifier, String redirectUri);
}
