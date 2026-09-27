package ng.com.nawill.pay.common.security;

/**
 * Path-level auth boundaries, shared by {@code SecurityConfig} (which
 * enforces them) and {@code OpenApiConfig} (which documents them), so the
 * API docs can never drift from what is actually enforced.
 */
public final class SecurityPaths {

    /** Served by the API-key/HMAC filter chain, not JWT (doc 3 §2.5). */
    public static final String[] API_KEY = {"/api/v1/collect/**", "/api/v1/withdraw/**"};

    /** Reachable without any credentials. */
    public static final String[] PUBLIC = {
            "/api/v1/auth/signup", "/api/v1/auth/signup/accept-invite",
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
            "/api/v1/pay/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health"
    };

    private SecurityPaths() {
    }
}
