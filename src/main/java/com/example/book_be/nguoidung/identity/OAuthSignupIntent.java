package com.example.book_be.nguoidung.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Ho so toi thieu cua mot nguoi dung dang trong qua trinh dang ky bang provider.
 *
 * Callback KHONG tao `nguoi_dung`. Ho so nam o day cho den khi buoc hoan tat tao
 * `nguoi_dung` + quyen USER + `auth_identity` trong dung mot transaction.
 */
@Entity
@Table(name = "oauth_signup_intent")
public class OAuthSignupIntent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_hash", nullable = false, length = 64)
    private String intentHash;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_code_hash", length = 64)
    private String emailCodeHash;

    @Column(name = "email_code_expires_at")
    private Instant emailCodeExpiresAt;

    @Column(name = "email_code_attempts", nullable = false)
    private int emailCodeAttempts;

    @Column(name = "ten_hien_thi", length = 255)
    private String tenHienThi;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIntentHash() { return intentHash; }
    public void setIntentHash(String intentHash) { this.intentHash = intentHash; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getProviderSubject() { return providerSubject; }
    public void setProviderSubject(String providerSubject) { this.providerSubject = providerSubject; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getEmailCodeHash() { return emailCodeHash; }
    public void setEmailCodeHash(String emailCodeHash) { this.emailCodeHash = emailCodeHash; }
    public Instant getEmailCodeExpiresAt() { return emailCodeExpiresAt; }
    public void setEmailCodeExpiresAt(Instant emailCodeExpiresAt) { this.emailCodeExpiresAt = emailCodeExpiresAt; }
    public int getEmailCodeAttempts() { return emailCodeAttempts; }
    public void setEmailCodeAttempts(int emailCodeAttempts) { this.emailCodeAttempts = emailCodeAttempts; }
    public String getTenHienThi() { return tenHienThi; }
    public void setTenHienThi(String tenHienThi) { this.tenHienThi = tenHienThi; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    /** Danh tinh provider da xac minh, dung lai o buoc hoan tat. */
    public ProviderIdentity toProviderIdentity() {
        return new ProviderIdentity(provider, issuer, providerSubject, email, emailVerified, tenHienThi);
    }
}
