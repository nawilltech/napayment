package ng.com.nawill.pay.onboarding.business;

/**
 * Confirms a business's RC/BN number against a CAC data source when
 * business KYC details are saved. {@link PublicPortalCacLookupGateway} is
 * the only implementation - a best-effort attempt against CAC's free public
 * search, since there is no affordable official API (see its Javadoc for
 * why this is unreliable today). Never blocks the caller: a lookup that
 * can't be confirmed still lets business details save, just marked
 * unverified for the admin KYC review queue (FR-3) to see.
 */
public interface CacLookupGateway {

    CacLookupResult lookup(String rcNumber, String claimedName);
}
