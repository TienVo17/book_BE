package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Phan giai danh tinh provider thanh dung mot `NguoiDung`, va lien ket danh tinh moi vao
 * mot tai khoan da xac thuc.
 *
 * Khoa la (provider, issuer, subject). Email khong bao gio duoc dung lam khoa, va trung
 * email khong bao gio tu dong lien ket: xem `resolve`.
 */
@Service
public class AuthIdentityService {
    private final AuthIdentityRepository identityRepository;
    private final NguoiDungRepository userRepository;

    public AuthIdentityService(AuthIdentityRepository identityRepository,
                               NguoiDungRepository userRepository) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Resolution resolve(ProviderIdentity claims) {
        Optional<AuthIdentity> existing = identityRepository
                .findByProviderAndIssuerAndProviderSubject(
                        claims.provider(), claims.issuer(), claims.subject());
        if (existing.isPresent()) {
            return new Resolution(existing.get().getNguoiDung(), false, claims.trustedEmail());
        }

        String trustedEmail = claims.trustedEmail();
        if (trustedEmail == null) {
            // Email chua xac minh khong the dung de doi chieu tai khoan san co: nguoi lap
            // mot tai khoan provider voi dia chi bat ky se doi duoc ket qua doi chieu.
            return new Resolution(null, false, null);
        }

        boolean collides = userRepository.findByEmail(trustedEmail) != null;
        return new Resolution(null, collides, trustedEmail);
    }

    /**
     * Lien ket mot danh tinh vao tai khoan da xac thuc. Caller phai tu chung minh nguoi dung
     * dang thao tac that su so huu tai khoan do (mat khau hoac re-auth gan day).
     */
    @Transactional
    public AuthIdentity link(NguoiDung user, ProviderIdentity claims) {
        Optional<AuthIdentity> existing = identityRepository
                .findByProviderAndIssuerAndProviderSubject(
                        claims.provider(), claims.issuer(), claims.subject());
        if (existing.isPresent()) {
            AuthIdentity identity = existing.get();
            if (identity.getNguoiDung().getMaNguoiDung() != user.getMaNguoiDung()) {
                // Mot danh tinh provider chi thuoc ve dung mot tai khoan. Chuyen chu am
                // tham se lam nguoi chu cu mat duong dang nhap ma khong he hay biet.
                throw new AuthIdentityException("IDENTITY_ALREADY_LINKED");
            }
            return identity;
        }

        Instant now = Instant.now();
        AuthIdentity identity = new AuthIdentity();
        identity.setNguoiDung(user);
        identity.setProvider(claims.provider());
        identity.setIssuer(claims.issuer());
        identity.setProviderSubject(claims.subject());
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        return identityRepository.save(identity);
    }

    /**
     * @param linkedUser tai khoan da lien ket, hoac null neu chua co
     * @param collidesWithExistingAccount co tai khoan mat khau dung dia chi email da xac minh nay
     * @param trustedEmail email chi khi provider khang dinh da xac minh
     */
    public record Resolution(NguoiDung linkedUser, boolean collidesWithExistingAccount,
                             String trustedEmail) {
        public boolean requiresSignup() {
            return linkedUser == null && !collidesWithExistingAccount;
        }
    }
}
