package com.example.book_be.shared.security;

import com.example.book_be.nguoidung.session.SessionPrincipal;

public class JwtResponse {
    private final String jwt;
    private final String accessToken;
    private final long expiresIn;
    private final SessionPrincipal principal;

    public JwtResponse(String accessToken, long expiresIn, SessionPrincipal principal) {
        this.jwt = accessToken;
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.principal = principal;
    }

    public String getJwt() { return jwt; }
    public String getAccessToken() { return accessToken; }
    public long getExpiresIn() { return expiresIn; }
    public SessionPrincipal getPrincipal() { return principal; }
}
