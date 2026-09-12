package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * NFR-7: rotating, server-revocable refresh tokens (RefreshTokenService,
 * Redis-backed - no DB fallback, same posture as password-reset codes).
 */
class RefreshTokenIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

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
    void expiredTokenIsRejected() {
        Map<String, Object> signup = signupResponse();
        String refreshToken = (String) signup.get("refreshToken");

        // Force-expire the Redis key directly rather than waiting out the
        // real 30-day TTL.
        String hash = sha256Hex(refreshToken);
        Boolean expired = redisTemplate.expire("auth:refresh-token:" + hash, 1, TimeUnit.MILLISECONDS);
        assertThat(expired).isTrue();
        await(Duration.ofMillis(50));

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

    private static void await(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String sha256Hex(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
