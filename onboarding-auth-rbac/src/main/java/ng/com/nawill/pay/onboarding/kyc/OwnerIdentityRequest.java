package ng.com.nawill.pay.onboarding.kyc;

/**
 * At least one of bvn/nin required, both 11 digits (CBN standard) - mirrors
 * the frontend's {@code ownerIdentitySchema} zod rule. Validated in {@link
 * OwnerIdentityService#save}, not here: this is a {@code @RequestBody}
 * deserialization target, and throwing from a record's compact constructor
 * during Jackson binding surfaces as an opaque "malformed JSON" 400 instead
 * of our normal {@code ApiException} error envelope.
 */
public record OwnerIdentityRequest(String bvn, String nin) {
}
