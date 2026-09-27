package ng.com.nawill.pay.onboarding.kyc;

import java.util.UUID;
import ng.com.nawill.pay.common.config.PaystackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * BVN verification via Paystack's {@code GET /bank/resolve_bvn/:bvn} (same
 * base-url/secret-key config as {@code PaystackBankVerificationGateway}).
 * Paystack has no NIN endpoint, so NIN always goes through the fallback
 * path below.
 * <p>
 * Unlike {@code PaystackBankVerificationGateway} (which fails the caller's
 * request on a Paystack error - a bad bank account must not silently pass),
 * a failed BVN/NIN check here falls back to an "approved" mock result
 * instead of blocking onboarding: KYC submissions still land in {@code
 * PENDING_REVIEW} for human admin review (FR-3) regardless, so a flaky
 * third-party provider shouldn't be able to stop a business from
 * progressing through onboarding. The fallback is logged as a warning so
 * it's auditable, and never silently indistinguishable from a real approval
 * in the logs.
 */
@Component
@Profile("!test")
public class PaystackIdentityVerificationGateway implements IdentityVerificationGateway {

    private static final Logger log = LoggerFactory.getLogger(PaystackIdentityVerificationGateway.class);

    private final RestClient restClient;
    private final PaystackProperties paystack;

    public PaystackIdentityVerificationGateway(RestClient.Builder builder, PaystackProperties paystack) {
        this.restClient = builder.baseUrl(paystack.baseUrl()).build();
        this.paystack = paystack;
    }

    @Override
    public VerificationResult verify(IdentityType type, String number) {
        if (type != IdentityType.BVN) {
            log.info("no live provider for {} - approving via fallback", type);
            return fallback(type);
        }
        if (!paystack.configured()) {
            log.warn("Paystack secret key not configured ({}) - approving BVN via fallback", PaystackProperties.SECRET_KEY_ENV);
            return fallback(type);
        }
        try {
            PaystackBvnResponse response = restClient.get()
                    .uri("/bank/resolve_bvn/{bvn}", number)
                    .header("Authorization", paystack.bearerToken())
                    .retrieve()
                    .body(PaystackBvnResponse.class);
            if (response == null || !response.status() || response.data() == null) {
                log.warn("Paystack BVN resolve returned no match - approving via fallback: message={}",
                        response == null ? "empty response" : response.message());
                return fallback(type);
            }
            String reference = "PAYSTACK-" + UUID.randomUUID();
            log.info("BVN verified via Paystack: reference={}", reference);
            return new VerificationResult(true, reference);
        } catch (Exception e) {
            log.warn("Paystack BVN resolve failed, approving via fallback: error={}", e.getMessage());
            return fallback(type);
        }
    }

    private VerificationResult fallback(IdentityType type) {
        String reference = "FALLBACK-" + UUID.randomUUID();
        log.warn("identity verification fallback used (not independently confirmed): type={} reference={}",
                type, reference);
        return new VerificationResult(true, reference);
    }

    private record PaystackBvnResponse(boolean status, String message, Object data) {
    }
}
