package ng.com.nawill.pay.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ng.com.nawill.pay.common.security.SecurityPaths;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Every operation's auth is derived from the code that enforces it -
 * {@link SecurityPaths} for public/API-key paths and the handler's
 * {@code @PreAuthorize("@auth.can('...')")} permission - never hand-written,
 * so the docs cannot drift from the real rules. Each operation gets the
 * matching security requirement, an {@code x-auth} extension
 * (PUBLIC, API_KEY, ADMIN, USER) and an "Auth:" line in its description.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String PUBLIC_KEY_SCHEME = "apiKeyPublicKey";
    private static final String TIMESTAMP_SCHEME = "apiKeyTimestamp";
    private static final String SIGNATURE_SCHEME = "apiKeySignature";

    private static final Pattern PERMISSION = Pattern.compile("@auth\\.can\\('([^']+)'\\)");

    /**
     * Permissions held only by platform staff: granted to no business role
     * template (RoleTemplate) nor to the USER/BUSINESS_OWNER seeded roles.
     */
    private static final Set<String> ADMIN_PERMISSIONS =
            Set.of("collection-account:manage");
    private static final String ADMIN_PERMISSION_PREFIX = "platform-";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    enum AuthType { PUBLIC, API_KEY, ADMIN, USER }

    @Bean
    public OpenAPI nawillPayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nawill Pay API")
                        .description("""
                                Nigerian payment collection and processing platform - MVP v0.1.

                                Every operation states its **Auth** (also machine-readable as `x-auth`):
                                - **Public** - no credentials.
                                - **User** - JWT bearer token from `/api/v1/auth/login` for a business or \
                                individual account, holding the listed permission.
                                - **Admin** - JWT bearer token for platform staff (SUPERADMIN, or an ADMIN \
                                assigned the listed permission).
                                - **API key** - server-to-server: `X-Public-Key`, `X-Timestamp` and an HMAC \
                                `X-Signature` of the request.""")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(PUBLIC_KEY_SCHEME, headerScheme("X-Public-Key",
                                "The business's API public key."))
                        .addSecuritySchemes(TIMESTAMP_SCHEME, headerScheme("X-Timestamp",
                                "Request time in epoch seconds; rejected outside the allowed clock skew."))
                        .addSecuritySchemes(SIGNATURE_SCHEME, headerScheme("X-Signature",
                                "HMAC signature of the request made with the API secret key.")));
    }

    @Bean
    public OperationCustomizer authOperationCustomizer() {
        return (operation, handlerMethod) -> {
            Optional<String> permission = requiredPermission(handlerMethod);
            AuthType authType = authType(handlerMethod, permission);
            operation.addExtension("x-auth", authType.name());
            permission.ifPresent(p -> operation.addExtension("x-required-permission", p));
            applySecurity(operation, authType);
            prependAuthLine(operation, authLine(authType, permission));
            return operation;
        };
    }

    private static AuthType authType(HandlerMethod handlerMethod, Optional<String> permission) {
        List<String> paths = paths(handlerMethod);
        if (paths.stream().anyMatch(path -> matchesAny(path, SecurityPaths.API_KEY))) {
            return AuthType.API_KEY;
        }
        if (paths.stream().anyMatch(path -> matchesAny(path, SecurityPaths.PUBLIC))) {
            return AuthType.PUBLIC;
        }
        boolean adminOnly = permission
                .filter(p -> p.startsWith(ADMIN_PERMISSION_PREFIX) || ADMIN_PERMISSIONS.contains(p))
                .isPresent();
        return adminOnly ? AuthType.ADMIN : AuthType.USER;
    }

    private static String authLine(AuthType authType, Optional<String> permission) {
        String required = permission.map(p -> " Requires permission `" + p + "`.").orElse("");
        return switch (authType) {
            case PUBLIC -> "**Auth:** Public - no authentication required.";
            case API_KEY -> "**Auth:** API key - signed with `X-Public-Key`, `X-Timestamp` and `X-Signature` headers "
                    + "(not a JWT)." + required;
            case ADMIN -> "**Auth:** Admin - platform staff JWT bearer token." + required
                    + " SUPERADMIN always passes.";
            case USER -> "**Auth:** User - JWT bearer token." + (required.isEmpty() ? " Any signed-in account." : required);
        };
    }

    private static void applySecurity(Operation operation, AuthType authType) {
        switch (authType) {
            case PUBLIC -> operation.setSecurity(List.of());
            case API_KEY -> operation.setSecurity(List.of(new SecurityRequirement()
                    .addList(PUBLIC_KEY_SCHEME).addList(TIMESTAMP_SCHEME).addList(SIGNATURE_SCHEME)));
            case ADMIN, USER -> operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_SCHEME)));
        }
    }

    private static void prependAuthLine(Operation operation, String authLine) {
        String existing = operation.getDescription();
        operation.setDescription(existing == null || existing.isBlank() ? authLine : authLine + "\n\n" + existing);
    }

    private static Optional<String> requiredPermission(HandlerMethod handlerMethod) {
        PreAuthorize preAuthorize = Optional.ofNullable(handlerMethod.getMethodAnnotation(PreAuthorize.class))
                .orElseGet(() -> AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), PreAuthorize.class));
        if (preAuthorize == null) {
            return Optional.empty();
        }
        Matcher matcher = PERMISSION.matcher(preAuthorize.value());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.of(preAuthorize.value());
    }

    /** Full paths (class-level prefix + method mapping) this handler serves. */
    private static List<String> paths(HandlerMethod handlerMethod) {
        List<String> prefixes = mappingPaths(handlerMethod.getBeanType());
        List<String> suffixes = mappingPaths(handlerMethod.getMethod());
        return prefixes.stream()
                .flatMap(prefix -> suffixes.stream().map(suffix -> prefix + suffix))
                .toList();
    }

    private static List<String> mappingPaths(AnnotatedElement element) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
        if (mapping == null || mapping.path().length == 0) {
            return List.of("");
        }
        return Arrays.asList(mapping.path());
    }

    private static boolean matchesAny(String path, String[] patterns) {
        return Arrays.stream(patterns).anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static SecurityScheme headerScheme(String header, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(header)
                .description(description);
    }
}
