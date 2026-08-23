package ng.com.nawill.pay.common.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The {@link org.springframework.security.core.Authentication} produced by
 * {@code ApiKeyAuthenticationFilter} once an HMAC-signed request has been
 * verified (doc 3 §2.5). Its principal is a {@link CurrentUser} directly -
 * no JWT exists on this path - so {@link CurrentUserResolver} can hand a
 * uniform {@link CurrentUser} to callers regardless of which auth mechanism
 * produced it.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser currentUser;

    public ApiKeyAuthenticationToken(CurrentUser currentUser) {
        super(List.of());
        this.currentUser = currentUser;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return currentUser;
    }
}
