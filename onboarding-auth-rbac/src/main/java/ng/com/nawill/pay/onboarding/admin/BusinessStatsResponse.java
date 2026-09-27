package ng.com.nawill.pay.onboarding.admin;

import java.util.Map;
import ng.com.nawill.pay.onboarding.business.KycStatus;

/** Headline counts for the admin console's overview. */
public record BusinessStatsResponse(long total, Map<KycStatus, Long> byKycStatus) {
}
