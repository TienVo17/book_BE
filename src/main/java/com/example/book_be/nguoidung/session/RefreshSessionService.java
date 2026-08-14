package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.service.TaiKhoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RefreshSessionService {
    private final RefreshTokenSessionRepository repository;
    private final RefreshTokenCodec codec;
    private final Clock clock;
    private final boolean enabled;
    private final NguoiDungRepository userRepository;
    private final JwtService jwtService;

    @Autowired
    public RefreshSessionService(RefreshTokenSessionRepository repository,
                                 RefreshTokenCodec codec,
                                 NguoiDungRepository userRepository,
                                 JwtService jwtService,
                                 @Value("${app.auth.refresh-enabled:false}") boolean enabled) {
        this(repository, codec, Clock.systemUTC(), enabled, userRepository, jwtService);
    }

    RefreshSessionService(RefreshTokenSessionRepository repository, RefreshTokenCodec codec,
                          Clock clock, boolean enabled) {
        this(repository, codec, clock, enabled, null, null);
    }

    RefreshSessionService(RefreshTokenSessionRepository repository, RefreshTokenCodec codec,
                          Clock clock, boolean enabled, NguoiDungRepository userRepository,
                          JwtService jwtService) {
        this.repository = repository;
        this.codec = codec;
        this.clock = clock;
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public SessionGrant issue(NguoiDung user, boolean rememberMe) {
        requireEnabled();
        return issueEnabled(user, rememberMe);
    }

    @Transactional
    public SessionGrant issueIfEnabled(NguoiDung user, boolean rememberMe) {
        return enabled ? issueEnabled(user, rememberMe) : null;
    }

    private SessionGrant issueEnabled(NguoiDung user, boolean rememberMe) {
        Instant now = clock.instant();
        Instant absoluteExpiry = now.plus(30, ChronoUnit.DAYS);
        RefreshTokenCodec.IssuedToken issued = codec.issue();
        RefreshTokenSession session = createSession(
                issued, user, UUID.randomUUID().toString(), rememberMe, now, absoluteExpiry);
        repository.save(session);
        return new SessionGrant(issued.rawToken(), user, rememberMe, absoluteExpiry);
    }

    @Transactional(noRollbackFor = RefreshSessionException.class)
    public TaiKhoanService.AuthenticatedSession rotateAndIssueAccessToken(String rawToken) {
        requireEnabled();
        String selector = codec.selectorOf(rawToken);
        if (selector == null) {
            throw invalidSession();
        }
        int userId = repository.findUserIdBySelector(selector)
                .orElseThrow(this::invalidSession);
        NguoiDung user = userRepository.findByIdForAuthWrite(userId)
                .orElseThrow(this::reauthenticationRequired);
        SessionGrant grant = rotateEnabled(rawToken);
        return new TaiKhoanService.AuthenticatedSession(
                user, grant, jwtService.generateToken(user));
    }

    @Transactional(noRollbackFor = RefreshSessionException.class)
    public SessionGrant rotate(String rawToken) {
        return rotateEnabled(rawToken);
    }

    private SessionGrant rotateEnabled(String rawToken) {
        requireEnabled();
        String selector = codec.selectorOf(rawToken);
        if (selector == null) {
            throw invalidSession();
        }
        RefreshTokenSession current = repository.findBySelectorForUpdate(selector)
                .orElseThrow(this::invalidSession);
        Instant now = clock.instant();

        if (!codec.matches(rawToken, current.getSelector(), current.getSecretHash())) {
            throw invalidSession();
        }
        if (current.getConsumedAt() != null) {
            revokeFamilyWithLocks(current.getFamilyId(), now);
            throw reauthenticationRequired();
        }
        if (current.getRevokedAt() != null || !now.isBefore(current.getAbsoluteExpiresAt())) {
            throw invalidSession();
        }
        if (!Boolean.TRUE.equals(current.getNguoiDung().getDaKichHoat())) {
            revokeFamilyWithLocks(current.getFamilyId(), now);
            throw reauthenticationRequired();
        }

        RefreshTokenCodec.IssuedToken replacementToken = codec.issue();
        RefreshTokenSession replacement = createSession(replacementToken, current.getNguoiDung(),
                current.getFamilyId(), current.isRememberMe(), now, current.getAbsoluteExpiresAt());
        current.setConsumedAt(now);
        current.setReplacedBySelector(replacementToken.selector());
        repository.saveAndFlush(replacement);
        return new SessionGrant(replacementToken.rawToken(), current.getNguoiDung(),
                current.isRememberMe(), current.getAbsoluteExpiresAt());
    }

    @Transactional
    public void revokeCurrent(String rawToken) {
        if (!enabled || rawToken == null) {
            return;
        }
        String selector = codec.selectorOf(rawToken);
        if (selector == null) {
            return;
        }
        repository.findBySelectorForUpdate(selector).ifPresent(session -> {
            if (codec.matches(rawToken, session.getSelector(), session.getSecretHash())) {
                revokeFamilyWithLocks(session.getFamilyId(), clock.instant());
            }
        });
    }

    @Transactional
    public void revokeAllByUser(int maNguoiDung) {
        repository.revokeAllByUser(maNguoiDung, clock.instant());
    }

    private RefreshTokenSession createSession(RefreshTokenCodec.IssuedToken token, NguoiDung user,
                                              String familyId, boolean rememberMe, Instant issuedAt,
                                              Instant absoluteExpiry) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setSelector(token.selector());
        session.setSecretHash(token.secretHash());
        session.setFamilyId(familyId);
        session.setNguoiDung(user);
        session.setRememberMe(rememberMe);
        session.setIssuedAt(issuedAt);
        session.setAbsoluteExpiresAt(absoluteExpiry);
        return session;
    }

    private void revokeFamilyWithLocks(String familyId, Instant revokedAt) {
        var family = repository.findFamilyForUpdate(familyId);
        for (RefreshTokenSession session : family) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(revokedAt);
            }
        }
        repository.saveAllAndFlush(family);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new RefreshSessionException("REFRESH_DISABLED");
        }
    }

    private RefreshSessionException invalidSession() {
        return new RefreshSessionException("SESSION_MISSING_OR_EXPIRED");
    }

    private RefreshSessionException reauthenticationRequired() {
        return new RefreshSessionException("REAUTHENTICATION_REQUIRED");
    }

    public record SessionGrant(String rawToken, NguoiDung user, boolean rememberMe,
                               Instant absoluteExpiresAt) {
    }
}
