package ng.com.nawill.pay.onboarding.business;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Best-effort attempt against CAC's free public company search
 * ({@code search.cac.gov.ng}) before falling back to an unverified result.
 * <p>
 * <b>This is not a verified integration.</b> Unlike BVN (Paystack, a
 * documented paid API), CAC has no affordable official API for RC/BN
 * lookups - the free public search is a human-facing web portal with no
 * documented JSON endpoint, and direct HTTP requests to it were observed
 * returning 403/connection-refused during development (bot/WAF
 * protection - unsurprising for a government portal). This class attempts
 * the request anyway on every call and treats literally any outcome other
 * than a clean 2xx match as "unavailable," which is the practical result
 * today. It's wired up as the correct seam so real confirmation starts
 * working automatically if CAC's posture ever changes, and so swapping in a
 * paid provider (Dojah/Mono CAC Lookup) later is a one-file change -
 * {@link BusinessKycService} never changes either way.
 */
@Component
@Profile("!test")
public class PublicPortalCacLookupGateway implements CacLookupGateway {

    private static final Logger log = LoggerFactory.getLogger(PublicPortalCacLookupGateway.class);
    private static final String SOURCE_PUBLIC_PORTAL = "PUBLIC_PORTAL";
    private static final String SOURCE_UNAVAILABLE = "LOOKUP_UNAVAILABLE";

    private final RestClient restClient;

    public PublicPortalCacLookupGateway() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl("https://search.cac.gov.ng")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public CacLookupResult lookup(String rcNumber, String claimedName) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search").queryParam("q", rcNumber).build())
                    .retrieve()
                    .body(String.class);
            // No documented response shape to parse against (see class Javadoc) -
            // a non-throwing 2xx here isn't itself proof of a match, so this
            // conservatively still falls back rather than guessing at HTML.
            log.info("CAC public portal responded but has no parseable contract yet - falling back: rcNumber={}",
                    rcNumber);
            return unavailable(body != null);
        } catch (Exception e) {
            log.info("CAC public portal unreachable, falling back to unverified: rcNumber={} error={}",
                    rcNumber, e.getMessage());
            return unavailable(false);
        }
    }

    private CacLookupResult unavailable(boolean portalReachable) {
        return new CacLookupResult(false, null, portalReachable ? SOURCE_PUBLIC_PORTAL : SOURCE_UNAVAILABLE);
    }
}
