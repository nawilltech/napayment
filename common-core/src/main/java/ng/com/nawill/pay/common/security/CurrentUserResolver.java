package ng.com.nawill.pay.common.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal from the validated JWT on the current
 * request. Lives in common-core (not onboarding-auth-rbac) so any module -
 * including payments - can enforce business-scoped row-level filtering
 * without depending on the auth module's internals.
 */
@Component
public class CurrentUserResolver {

    public static final String CLAIM_BUSINESS_ID = "businessId";
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_PERMISSIONS = "permissions";

    public Optional<CurrentUser> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuth.getToken();

        UUID userId = UUID.fromString(jwt.getSubject());
        String businessIdClaim = jwt.getClaimAsString(CLAIM_BUSINESS_ID);
        UUID businessId = businessIdClaim == null ? null : UUID.fromString(businessIdClaim);
        String userType = jwt.getClaimAsString(CLAIM_USER_TYPE);
        Set<String> permissions = jwtAuth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        return Optional.of(new CurrentUser(userId, businessId, userType, permissions));
    }

    public CurrentUser requireCurrentUser() {
        return resolve().orElseThrow(() ->
                new IllegalStateException("No authenticated user in the current security context"));
    }
}
