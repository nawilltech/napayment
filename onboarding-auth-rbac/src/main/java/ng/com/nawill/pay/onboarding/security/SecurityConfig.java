package ng.com.nawill.pay.onboarding.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import ng.com.nawill.pay.common.crypto.EncryptionService;
import ng.com.nawill.pay.common.crypto.HmacSigner;
import ng.com.nawill.pay.common.ratelimit.RateLimitService;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyIpWhitelistRepository;
import ng.com.nawill.pay.onboarding.apikey.ApiKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Endpoint-level gating is intentionally coarse (permitAll vs authenticated)
 * - it is the defence-in-depth backstop from doc 3 §2.2; fine-grained
 * permission-string checks live on service/controller methods via
 * {@code @PreAuthorize("@auth.can(...)")}.
 * <p>
 * Two filter chains: an API-key/HMAC chain (doc 3 §2.5) scoped to the
 * third-party collect/withdraw surface, tried first (lower {@code @Order}
 * value = higher precedence), and the original JWT chain for everything
 * else. This is the standard Spring Security multi-chain idiom for mixing
 * auth mechanisms by path, chosen over extending the single chain so the
 * two mechanisms (and their permitAll lists) stay independently readable.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final String jwtSecret;

    public SecurityConfig(@Value("${nawill.auth.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiKeyFilterChain(HttpSecurity http, ApiKeyRepository apiKeyRepository,
                                                  ApiKeyIpWhitelistRepository ipWhitelistRepository,
                                                  EncryptionService encryptionService, HmacSigner hmacSigner,
                                                  ObjectMapper objectMapper, RateLimitService rateLimitService,
                                                  @Value("${nawill.security.request-signing.max-clock-skew-seconds:300}")
                                                  long maxClockSkewSeconds,
                                                  @Value("${nawill.security.rate-limit.ip.max-requests-per-minute:120}")
                                                  int ipMaxRequestsPerMinute,
                                                  @Value("${nawill.security.rate-limit.api-key.max-requests-per-minute:60}")
                                                  int apiKeyMaxRequestsPerMinute) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyRepository, ipWhitelistRepository, encryptionService, hmacSigner, objectMapper, rateLimitService,
                maxClockSkewSeconds, ipMaxRequestsPerMinute, apiKeyMaxRequestsPerMinute);

        http
                .securityMatcher("/api/v1/collect/**", "/api/v1/withdraw/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/signup/accept-invite",
                                "/api/v1/auth/login", "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/pay/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(CurrentUserResolver.CLAIM_PERMISSIONS);
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
