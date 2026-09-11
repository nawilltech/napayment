package ng.com.nawill.pay.onboarding.kyc;

public record OwnerIdentityResponse(String bvn, String nin, boolean verified) {
}
