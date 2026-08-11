package ng.com.nawill.pay.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code X-Request-Id} from the inbound request, or generates one, and
 * places it in MDC before the request reaches Spring Security or any
 * controller. Registered as the very first filter in the chain (see
 * common-core's FilterConfig) so a request-id is present even for requests
 * that fail authentication. Echoes the id back on the response and clears
 * MDC once the request completes.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(LogFields.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(LogFields.REQUEST_ID, requestId);
            response.setHeader(LogFields.REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
