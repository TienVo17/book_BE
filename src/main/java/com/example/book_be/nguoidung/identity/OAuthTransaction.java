package com.example.book_be.nguoidung.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "oauth_transaction")
public class OAuthTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "flow_kind", nullable = false, length = 16)
    private String flowKind;

    @Column(name = "browser_binding_hash", nullable = false, length = 64)
    private String browserBindingHash;

    @Column(name = "nonce_hash", length = 64)
    private String nonceHash;

    @Column(name = "code_verifier_encrypted", nullable = false, length = 512)
    private byte[] codeVerifierEncrypted;

    @Column(name = "redirect_uri", nullable = false, length = 512)
    private String redirectUri;

    @Column(name = "ma_nguoi_dung_muc_tieu")
    private Integer maNguoiDungMucTieu;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStateHash() { return stateHash; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFlowKind() { return flowKind; }
    public void setFlowKind(String flowKind) { this.flowKind = flowKind; }
    public String getBrowserBindingHash() { return browserBindingHash; }
    public void setBrowserBindingHash(String browserBindingHash) { this.browserBindingHash = browserBindingHash; }
    public String getNonceHash() { return nonceHash; }
    public void setNonceHash(String nonceHash) { this.nonceHash = nonceHash; }
    public byte[] getCodeVerifierEncrypted() { return codeVerifierEncrypted; }
    public void setCodeVerifierEncrypted(byte[] codeVerifierEncrypted) { this.codeVerifierEncrypted = codeVerifierEncrypted; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public Integer getMaNguoiDungMucTieu() { return maNguoiDungMucTieu; }
    public void setMaNguoiDungMucTieu(Integer maNguoiDungMucTieu) { this.maNguoiDungMucTieu = maNguoiDungMucTieu; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
