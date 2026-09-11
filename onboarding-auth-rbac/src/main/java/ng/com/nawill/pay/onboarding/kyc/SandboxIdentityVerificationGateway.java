package ng.com.nawill.pay.onboarding.kyc;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * TODO(FR-8, doc 1 §5): stand-in for a real BVN/NIN verification provider -
 * synchronously "approves" any well-formed 11-digit number rather than
 * calling out to NIBSS/a KYC vendor. Same scoped-sandbox posture as {@code
 * SandboxPaymentProcessorGateway}/{@code SandboxSettlementGateway}: swap this
 * bean for a real implementation of {@link IdentityVerificationGateway} when
 * a provider is chosen - {@link OwnerIdentityService} never changes.
 */
@Service
public class SandboxIdentityVerificationGateway implements IdentityVerificationGateway {

    private static final Logger log = LoggerFactory.getLogger(SandboxIdentityVerificationGateway.class);

    @Override
    public VerificationResult verify(IdentityType type, String number) {
        String reference = "SANDBOX-" + UUID.randomUUID();
        log.info("sandbox identity verification approved: type={} reference={}", type, reference);
        return new VerificationResult(true, reference);
    }
}
