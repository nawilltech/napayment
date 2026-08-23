package ng.com.nawill.pay.onboarding.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ng.com.nawill.pay.common.crypto.EncryptionService;
import ng.com.nawill.pay.common.crypto.HmacSigner;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.logging.LogFields;
import ng.com.nawill.pay.common.ratelimit.RateLimitService;
import ng.com.nawill.pay.common.security.ApiKeyAuthenticationToken;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.web.ErrorResponse;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyCredential;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyIpWhitelist;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyIpWhitelistRepository;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyRepository;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HMAC-SHA256 authentication for third-party requests to /api/v1/collect and
 * /api/v1/withdraw (doc 3 §2.5, FR-9): {@code X-Public-Key} identifies the
 * business, {@code X-Timestamp} + {@code X-Signature} prove possession of
 * the secret key and guard against replay. On success, populates the
 * security context with an {@link ApiKeyAuthenticationToken} so
 * {@code @auth.can(...)} works unchanged downstream.
 * <p>
 * Runs before Spring MVC dispatch, so failures are written directly here
 * (matching {@code GlobalExceptionHandler}'s {@code ErrorResponse} shape)
 * rather than thrown - exceptions thrown from a filter never reach
 * {@code @RestControllerAdvice}, which only wraps the DispatcherServlet.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_PUBLIC_KEY = "X-Public-Key";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Signature";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyIpWhitelistRepository ipWhitelistRepository;
    private final EncryptionService encryptionService;
    private final HmacSigner hmacSigner;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final long maxClockSkewSeconds;
    private final int ipMaxRequestsPerMinute;
    private final int apiKeyMaxRequestsPerMinute;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository,
                                       ApiKeyIpWhitelistRepository ipWhitelistRepository,
                                       EncryptionService encryptionService, HmacSigner hmacSigner,
                                       ObjectMapper objectMapper, RateLimitService rateLimitService,
                                       long maxClockSkewSeconds, int ipMaxRequestsPerMinute,
                                       int apiKeyMaxRequestsPerMinute) {
        this.apiKeyRepository = apiKeyRepository;
        this.ipWhitelistRepository = ipWhitelistRepository;
        this.encryptionService = encryptionService;
        this.hmacSigner = hmacSigner;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.ipMaxRequestsPerMinute = ipMaxRequestsPerMinute;
        this.apiKeyMaxRequestsPerMinute = apiKeyMaxRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimitService.tryConsume("ip:" + request.getRemoteAddr(), ipMaxRequestsPerMinute, Duration.ofMinutes(1))) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests from this IP");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String publicKey = request.getHeader(HEADER_PUBLIC_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (publicKey == null || timestamp == null || signature == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "MISSING_SIGNATURE_HEADERS",
                    "X-Public-Key, X-Timestamp and X-Signature headers are all required");
            return;
        }

        Optional<ApiKeyCredential> maybeKey = apiKeyRepository.findByPublicKey(publicKey)
                .filter(key -> key.getStatus() == EntityStatus.ACTIVE);
        if (maybeKey.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Unknown or inactive API key");
            return;
        }
        ApiKeyCredential apiKey = maybeKey.get();

        if (!rateLimitService.tryConsume("apikey:" + apiKey.getId(), apiKeyMaxRequestsPerMinute, Duration.ofMinutes(1))) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests for this API key");
            return;
        }

        if (!withinClockSkew(timestamp)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "STALE_TIMESTAMP",
                    "Request timestamp is outside the allowed clock-skew window");
            return;
        }

        String rawBody = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);
        String canonicalString = timestamp + "." + rawBody;
        String secretKey = encryptionService.decrypt(apiKey.getSecretKeyEncrypted());
        if (!hmacSigner.verify(secretKey, canonicalString, signature)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request signature verification failed");
            return;
        }

        List<String> whitelist = ipWhitelistRepository.findByApiKeyId(apiKey.getId()).stream()
                .map(ApiKeyIpWhitelist::getCidr).toList();
        if (!whitelist.isEmpty() && whitelist.stream().noneMatch(cidr -> matchesCidr(request.getRemoteAddr(), cidr))) {
            writeError(response, HttpStatus.FORBIDDEN, "IP_NOT_WHITELISTED",
                    "Source IP is not in this API key's whitelist");
            return;
        }

        CurrentUser currentUser = new CurrentUser(null, apiKey.getBusinessId(), "API_CLIENT",
                Set.of("collect:create", "withdraw:create"));
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(currentUser));

        filterChain.doFilter(cachedRequest, response);
    }

    private boolean withinClockSkew(String timestampHeader) {
        try {
            long requestEpochSeconds = Long.parseLong(timestampHeader);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - requestEpochSeconds) <= maxClockSkewSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean matchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return ip.equals(cidr);
            }
            String[] parts = cidr.split("/");
            byte[] cidrBytes = InetAddress.getByName(parts[0]).getAddress();
            byte[] remoteBytes = InetAddress.getByName(ip).getAddress();
            if (cidrBytes.length != remoteBytes.length) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1]);
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != remoteBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                if ((cidrBytes[fullBytes] & mask) != (remoteBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String errorCode, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(status.value(), errorCode, message, MDC.get(LogFields.REQUEST_ID));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
