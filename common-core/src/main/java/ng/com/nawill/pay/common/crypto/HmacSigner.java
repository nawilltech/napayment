package ng.com.nawill.pay.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 request signing for third-party API-key authentication
 * (doc 3 §2.3/§2.5): {@code signature = HMAC-SHA256(secretKey, timestamp + "." + rawBody)}.
 * Confidentiality is TLS's job; this guarantees authenticity/integrity of
 * signed requests. No third-party crypto dependency needed - the JDK's
 * {@code javax.crypto.Mac} is sufficient for HMAC-SHA256.
 */
@Component
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    public String sign(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    public boolean verify(String secret, String canonicalString, String candidateSignature) {
        String expected = sign(secret, canonicalString);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidateSignature.getBytes(StandardCharsets.UTF_8));
    }
}
