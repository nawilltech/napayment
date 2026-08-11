package ng.com.nawill.pay.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs immediately after the Spring Security filter chain (see
 * common-core's FilterConfig), once authentication has been resolved, and
 * adds the authenticated userId to MDC so every subsequent log line for this
 * request carries it. Never runs for requests that failed authentication,
 * since Spring Security short-circuits before reaching this filter in that
 * case - requestId (set earlier by RequestIdFilter) still applies.
 */
public class UserContextMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            MDC.put(LogFields.USER_ID, jwtAuth.getToken().getSubject());
        }
        filterChain.doFilter(request, response);
    }
}
