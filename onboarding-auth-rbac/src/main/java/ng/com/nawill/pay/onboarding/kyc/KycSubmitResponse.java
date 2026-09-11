package ng.com.nawill.pay.onboarding.kyc;

import java.time.Instant;
import ng.com.nawill.pay.onboarding.business.KycStatus;

public record KycSubmitResponse(KycStatus status, Instant submittedAt) {
}
