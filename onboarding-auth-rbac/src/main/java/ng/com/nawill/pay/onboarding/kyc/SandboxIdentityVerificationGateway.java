package ng.com.nawill.pay.onboarding.kyc;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test-only stand-in for {@link PaystackIdentityVerificationGateway} -
 * synchronously "approves" any well-formed 11-digit number, no network call,
 * so the integration suite never depends on Paystack's sandbox being
 * reachable. Same reasoning as {@code FakeBankVerificationGateway}. Outside
 * the {@code test} profile, {@link PaystackIdentityVerificationGateway} is
 * the only bean - it falls back to this exact "always approve" behavior
 * itself when the real Paystack call fails, so this class's logic is
 * intentionally duplicated there rather than shared, to keep the two
 * concerns (deterministic test double vs. production resilience fallback)
 * independently readable.
 */
@Service
@Profile("test")
public class SandboxIdentityVerificationGateway implements IdentityVerificationGateway {

    private static final Logger log = LoggerFactory.getLogger(SandboxIdentityVerificationGateway.class);

    @Override
    public VerificationResult verify(IdentityType type, String number) {
        String reference = "SANDBOX-" + UUID.randomUUID();
        log.info("sandbox identity verification approved: type={} reference={}", type, reference);
        return new VerificationResult(true, reference);
    }
}
