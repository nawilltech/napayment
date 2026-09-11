package ng.com.nawill.pay.onboarding.business;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only stand-in for {@link PublicPortalCacLookupGateway} - deterministic,
 * no network call, so the integration suite never depends on a government
 * portal being reachable. Same reasoning as {@code FakeBankVerificationGateway}.
 */
@Component
@Profile("test")
public class FakeCacLookupGateway implements CacLookupGateway {

    @Override
    public CacLookupResult lookup(String rcNumber, String claimedName) {
        return new CacLookupResult(true, claimedName, "TEST");
    }
}
