package com.example.book_be.nguoidung.identity;

import java.util.Map;

/**
 * Doi authorization code cua Facebook lay danh tinh.
 *
 * Tra ve ho so va ket qua debug token thay vi tra access token: token cua Facebook khong
 * duoc roi khoi buoc nay. Ung dung chi can biet "ai", khong goi tiep Graph API nao khac.
 */
public interface FacebookTokenExchange {
    /** @return `profile` tu Graph /me va `debugToken` tu Graph /debug_token */
    ExchangeResult exchange(String authorizationCode, String codeVerifier, String redirectUri);

    record ExchangeResult(Map<String, Object> profile, Map<String, Object> debugToken) {
    }
}
