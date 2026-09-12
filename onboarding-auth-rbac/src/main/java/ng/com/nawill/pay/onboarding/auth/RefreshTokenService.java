package ng.com.nawill.pay.onboarding.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.UnauthorizedException;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NFR-7: rotating, server-revocable refresh tokens. Postgres is the source
 * of truth, not Redis - a 30-day session token needs to survive a Redis
 * restart/eviction the way a 15-minute lockout counter doesn't, and a
 * compromised-account investigation needs a permanent record of every token
 * issued/revoked for a user, not a tombstone that self-deletes in minutes.
 * <p>
 * Single-use - every {@link #rotate} call revokes the presented token and
 * issues a fresh one (OWASP's recommended refresh-token rotation). A
 * rotated-out row is marked revoked, never deleted, so presenting it again
 * is recognizable as reuse (vs. never having existed) - the row itself
 * carries the {@code replacedByTokenId} chain, giving a permanent audit
 * trail with no separate bookkeeping needed (unlike the Redis version's
 * per-user SET).
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService securityAuditService;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                SecurityAuditService securityAuditService,
                                @Value("${nawill.auth.jwt.refresh-expiry-days:30}") long refreshExpiryDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityAuditService = securityAuditService;
        this.refreshTtl = Duration.ofDays(refreshExpiryDays);
    }

    public String issue(UUID userId) {
        String rawToken = randomToken();
        RefreshToken token = new RefreshToken(userId, hash(rawToken), Instant.now().plus(refreshTtl));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    public RotationResult rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid refresh token"));

        if (current.getRevokedAt() != null) {
            log.warn("reused refresh token detected, revoking all active tokens: userId={} tokenId={}",
                    current.getUserId(), current.getId());
            revokeAllActive(current.getUserId());
            securityAuditService.record(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED, AuditOutcome.FAILURE,
                    current.getUserId(), null, "Reused an already-rotated refresh token; all active tokens revoked");
            throw new UnauthorizedException("REFRESH_TOKEN_REUSED", "This refresh token has already been used");
        }
        if (current.isExpired()) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        String newRawToken = randomToken();
        RefreshToken next = new RefreshToken(current.getUserId(), hash(newRawToken), Instant.now().plus(refreshTtl));
        next = refreshTokenRepository.save(next);
        current.revoke(next.getId());
        refreshTokenRepository.save(current);

        log.info("refresh token rotated: userId={} newTokenId={}", current.getUserId(), next.getId());
        securityAuditService.record(AuditEventType.REFRESH_TOKEN_ROTATED, AuditOutcome.SUCCESS,
                current.getUserId(), null, null);
        return new RotationResult(current.getUserId(), newRawToken);
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(null);
                refreshTokenRepository.save(token);
                log.info("refresh token revoked (logout): userId={} tokenId={}", token.getUserId(), token.getId());
                securityAuditService.record(AuditEventType.REFRESH_TOKEN_REVOKED, AuditOutcome.SUCCESS,
                        token.getUserId(), null, "Logout");
            }
        });
    }

    private void revokeAllActive(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(null));
        refreshTokenRepository.saveAll(active);
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(UUID userId, String rawToken) {
    }
}
