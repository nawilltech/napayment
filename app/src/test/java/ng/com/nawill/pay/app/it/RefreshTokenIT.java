package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import ng.com.nawill.pay.onboarding.auth.RefreshToken;
import ng.com.nawill.pay.onboarding.auth.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * NFR-7: rotating, server-revocable refresh tokens (RefreshTokenService,
 * Postgres-backed - source of truth, not Redis, so a session survives a
 * cache restart and a compromised account leaves a permanent record).
 */
class RefreshTokenIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void refreshRotatesTokenAndTheOldOneNoLongerWorks() {
        Map<String, Object> signup = signupResponse();
        String refreshToken = (String) signup.get("refreshToken");

        ResponseEntity<Map> refreshed = refresh(refreshToken);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefreshToken = (String) refreshed.getBody().get("refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        assertThat(refreshed.getBody().get("accessToken")).isNotNull();

        ResponseEntity<Map> reuseOldToken = refresh(refreshToken);
        assertThat(reuseOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeChain() {
        Map<String, Object> signup = signupResponse();
        String firstToken = (String) signup.get("refreshToken");

        ResponseEntity<Map> rotated = refresh(firstToken);
        String secondToken = (String) rotated.getBody().get("refreshToken");

        // Replaying the already-rotated first token is theft-shaped - the
        // whole chain (including the still-fresh second-generation token)
        // must die, not just the replayed one.
        ResponseEntity<Map> reuse = refresh(firstToken);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> secondTokenNowDead = refresh(secondToken);
        assertThat(secondTokenNowDead.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Permanent record survives the revocation, unlike the old Redis
        // tombstone (self-deleted after a few minutes) - the whole point of
        // moving this to Postgres.
        RefreshToken persisted = refreshTokenRepository.findByTokenHash(sha256Hex(firstToken)).orElseThrow();
        assertThat(persisted.getRevokedAt()).isNotNull();
    }

    @Test
    void logoutRevokesTheToken() {
        Map<String, Object> signup = signupResponse();
        String refreshToken = (String) signup.get("refreshToken");

        ResponseEntity<Map> logout = restTemplate.postForEntity(
                url("/api/v1/auth/logout"), Map.of("refreshToken", refreshToken), Map.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterLogout = refresh(refreshToken);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Map<String, Object> signup = signupResponse();
        String refreshToken = (String) signup.get("refreshToken");

        // Force-expire the row directly rather than waiting out the real
        // 30-day TTL - expiresAt has no application setter (only set once,
        // at issuance), so this reaches into the field the same way a
        // hand-written SQL UPDATE would in a real expiry scenario.
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken)).orElseThrow();
        Field expiresAt = RefreshToken.class.getDeclaredField("expiresAt");
        expiresAt.setAccessible(true);
        expiresAt.set(token, Instant.now().minusSeconds(60));
        refreshTokenRepository.save(token);

        ResponseEntity<Map> afterExpiry = refresh(refreshToken);
        assertThat(afterExpiry.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void garbageTokenIsRejected() {
        ResponseEntity<Map> response = refresh("not-a-real-token");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Map<String, Object> signupResponse() {
        Map<String, Object> body = uniqueSignupPayload("Refresh", "Tester", "SecurePass123!");
        return restTemplate.postForEntity(url("/api/v1/auth/signup"), body, Map.class).getBody();
    }

    private ResponseEntity<Map> refresh(String refreshToken) {
        return restTemplate.exchange(url("/api/v1/auth/refresh"), HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", refreshToken)), Map.class);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
