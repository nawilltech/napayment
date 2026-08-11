package ng.com.nawill.pay.onboarding.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived HS256 access tokens (doc 3 §2.1). TODO(NFR-7/doc 3 §2.1):
 * rotating, server-revocable refresh tokens are out of this session's scope -
 * MVP v0.1 is access-token-only.
 */
@Service
public class JwtService {

    private final byte[] secretBytes;
    private final long expiryMinutes;

    public JwtService(@Value("${nawill.auth.jwt.secret}") String secret,
                       @Value("${nawill.auth.jwt.expiry-minutes:15}") long expiryMinutes) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expiryMinutes = expiryMinutes;
    }

    public String issueAccessToken(UUID userId, UUID businessId, String userType, Set<String> permissions) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim(CurrentUserResolver.CLAIM_USER_TYPE, userType)
                    .claim(CurrentUserResolver.CLAIM_PERMISSIONS, List.copyOf(permissions))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(expiryMinutes * 60)));
            if (businessId != null) {
                claims.claim(CurrentUserResolver.CLAIM_BUSINESS_ID, businessId.toString());
            }

            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            signedJwt.sign(new MACSigner(secretBytes));
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public long expiresInSeconds() {
        return expiryMinutes * 60;
    }

    // Exposed for tests that need to introspect a token without a full resource-server round trip.
    JWTClaimsSet parseClaims(String token) throws ParseException {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }
}
