package ng.com.nawill.pay.onboarding.business;

/**
 * {@code verified} means the RC/BN number was independently confirmed
 * against a CAC data source - {@code false} covers both "confirmed not to
 * exist" and "couldn't check" (the lookup source was unreachable). {@code
 * source} distinguishes the two in logs/admin review ({@code
 * PUBLIC_PORTAL} vs {@code LOOKUP_UNAVAILABLE}).
 */
public record CacLookupResult(boolean verified, String matchedName, String source) {
}
