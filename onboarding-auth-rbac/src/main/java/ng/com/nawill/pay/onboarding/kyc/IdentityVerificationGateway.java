package ng.com.nawill.pay.onboarding.kyc;

/**
 * Verifies a BVN/NIN against a third-party identity provider (doc 1 §5
 * assumptions) before it's trusted. {@link SandboxIdentityVerificationGateway}
 * is a mock that always approves - swap in a real provider (e.g. NIBSS,
 * Prembly, Smile Identity, YouVerify) behind this same interface later, same
 * posture as {@code PaymentProcessorGateway}/{@code SettlementGateway}.
 */
public interface IdentityVerificationGateway {

    VerificationResult verify(IdentityType type, String number);
}
