# common-core

## What it owns

`common-core` is the shared kernel of the modular monolith — the one module every other module (`reference-data`, `payments`, `onboarding-auth-rbac`, `app`) depends on, and which depends on nothing else internally. It exists so that cross-cutting concerns (persistence contract, error handling, idempotency, auth/permission resolution, structured logging, crypto primitives) are implemented exactly once instead of per-module.

Nothing in here is business-domain-specific (no "virtual account" or "settlement" concept lives here) — it's infrastructure and platform-wide contracts that business modules build on.

## Key classes

**Base entity & persistence**
- `entity/BaseEntity.java` — `@MappedSuperclass` every entity extends: UUID PK (`@UuidGenerator`), `EntityStatus` (ACTIVE/INACTIVE, distinct from soft-delete), `createdAt`/`updatedAt`/`createdBy` audit fields, `deletedAt`. Records are never hard-deleted (see Gotchas).
- `entity/EntityStatus.java` — the `ACTIVE`/`INACTIVE` enum.
- `config/JpaAuditingConfig.java` — wires `@CreatedDate`/`@LastModifiedDate`/`@CreatedBy` via Spring Data's `AuditorAware`, sourcing the current user from `CurrentUserResolver`.

**Exceptions**
- `exception/ApiException.java` — base type carrying an `HttpStatus` + `errorCode`; anything meant to become a structured API error extends this.
- `exception/{BadRequestException, ConflictException, ForbiddenException, ResourceNotFoundException, TooManyRequestsException, UnauthorizedException, AccountLockedException}.java` — one per HTTP status, each with a default `errorCode` and a constructor overload to pass a custom one.
- `exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` translating `ApiException`, Spring Security's `AccessDeniedException`/`AuthenticationException`, bean-validation exceptions, and anything uncaught into a consistent `ErrorResponse` JSON body.
- `web/ErrorResponse.java` — the response shape: `timestamp`, `status`, `errorCode`, `message`, `requestId`, `details`.

**Idempotency**
- `idempotency/Idempotent.java` — method-level annotation for controller methods that must return `ResponseEntity<?>`; requires the `Idempotency-Key` header.
- `idempotency/IdempotencyAspect.java` — the `@Around` advice enforcing it: 400 if the header is missing, replays the cached response on a completed duplicate, 409 on a still-in-flight duplicate, persists the eventual response.
- `idempotency/IdempotencyService.java` — DB (source of truth) + Redis (fast-path lock, best-effort) backing store.
- `idempotency/IdempotencyRecord.java` / `IdempotencyRecordRepository.java` — the `idempotency_records` entity/repo (unique constraint on `idempotency_key` from migration `V0014`).
- `idempotency/IdempotencyConstants.java` — header name, Redis key prefix, lock TTL (24h).

**Security & RBAC**
- `security/CurrentUser.java` — the authenticated principal record (`userId`, `businessId`, `userType`, `permissions`), deliberately auth-mechanism-agnostic.
- `security/CurrentUserResolver.java` — resolves `CurrentUser` from whatever's in `SecurityContextHolder` (JWT or API-key auth); `requireBusinessScope()` is the single guard every business-scoped service (settlements, API keys, payment links, dynamic accounts, bank verification, ...) calls instead of reimplementing the check.
- `security/PermissionChecker.java` — the `@auth` SpEL bean for `@PreAuthorize("@auth.can('resource:action'))")`; SUPERADMIN bypass is hard-coded here (see Gotchas).
- `security/ApiKeyAuthenticationToken.java` — the `Authentication` implementation for HMAC-signed third-party requests, carrying a `CurrentUser` directly as its principal (no JWT on this path).

**Crypto**
- `crypto/EncryptionService.java` — AES-256-GCM encrypt/decrypt (for secrets that must be recovered in plaintext later, e.g. an API key's secret — unlike passwords, which are hashed one-way). Key comes from `nawill.security.encryption-key`.
- `crypto/HmacSigner.java` — HMAC-SHA256 sign/verify for third-party API-key request signing (`sign = HMAC-SHA256(secret, timestamp + "." + rawBody)`).

**Rate limiting**
- `ratelimit/RateLimitService.java` — Redis fixed-window counter; fails open (allows the request) if Redis is unavailable, same posture as idempotency's Redis fast-path.

**Logging**
- `logging/RequestIdFilter.java` — first filter in the chain; reads/generates `X-Request-Id`, puts it in MDC, echoes it on the response.
- `logging/UserContextMdcFilter.java` — runs right after Spring Security resolves auth; adds the authenticated `userId` to MDC.
- `logging/LogFields.java` — the MDC key constants (`requestId`, `userId`, `module`).
- `logging/PiiMasker.java` — `maskKeepLast4` / `fullyMask` for BVN/NIN/account numbers/passwords/tokens before they're ever logged.
- `logging/ModuleJsonProvider.java` — Logback JSON provider deriving a `module` field from the logger's package (e.g. `...payments.transaction.TransactionService` → `payments`); wired in `logback-spring.xml`, not Java config.
- `config/LoggingFilterConfig.java` — registers the two MDC filters with explicit ordering (`RequestIdFilter` at `HIGHEST_PRECEDENCE`, `UserContextMdcFilter` just after Spring Security's filter chain).

**Validation**
- `validation/StrongPassword.java` / `StrongPasswordValidator.java` — bean-validation constraint: ≥8 chars, upper+lower+digit+special char.

**Web/misc**
- `web/PageResponse.java` — generic pagination envelope wrapping Spring Data's `Page<T>`.
- `config/OpenApiConfig.java` — the springdoc `OpenAPI` bean (bearer-JWT security scheme).

## How to extend it

- **New error type**: add a class extending `ApiException` (or reuse `BadRequestException`/`ForbiddenException`/etc. with a custom `errorCode` via their two-arg constructor) — `GlobalExceptionHandler` picks up any `ApiException` subtype automatically, no handler registration needed per exception type.
- **New idempotent endpoint**: annotate the controller method `@Idempotent`, ensure it returns `ResponseEntity<?>` (the aspect discards the record instead of caching if it doesn't), and the caller must send `Idempotency-Key`.
- **New permission-gated endpoint**: `@PreAuthorize("@auth.can('resource:action')")` — the permission string itself is just data checked against `CurrentUser.permissions()`; defining/assigning the permission is done in `onboarding-auth-rbac`'s seed data/role tables, not here.
- **New business-scoped service method**: call `currentUserResolver.requireBusinessScope()` at the top rather than checking `businessId != null` manually.
- **Logging a new sensitive field**: run it through `PiiMasker` before it hits any `log.*()` call — there's no automatic redaction, it's opt-in per call site.

## Gotchas / non-obvious behavior

- **No hard deletes.** `BaseEntity` has `@SQLRestriction("deleted_at IS NULL")` — every query against every entity extending it implicitly filters out soft-deleted rows at the Hibernate level, not just at the repository-method level. `markDeleted()` just sets `deletedAt`; nothing ever calls `DELETE`. The one deliberate exception in the whole platform is `IdempotencyService.discard()`, which hard-deletes an in-progress `IdempotencyRecord` after a failed attempt — that table is an ephemeral lock/cache, not an audited business entity, so the soft-delete contract doesn't apply to it.
- **`@Idempotent` requires `ResponseEntity<?>`.** The aspect's return-type handling only knows how to cache a `ResponseEntity` body; any other return type silently skips caching (calls `discard()`) rather than erroring, which can be surprising if you annotate a method returning a plain DTO.
- **Idempotency's Redis lock is not the source of truth.** `IdempotencyService.tryAcquireRedisLock` treats any Redis failure as "not locked" (returns `true`) and logs a warning — the real guard is the DB's unique constraint on `idempotency_key` (`createInProgress` throws `DataIntegrityViolationException` on a genuine race, translated to 409). Redis is purely a fast-path optimization; correctness never depends on it being up.
- **SUPERADMIN bypass is hard-coded, not table-driven.** `PermissionChecker.can()` short-circuits to `true` for any principal with `userType == "SUPERADMIN"`, before ever consulting `CurrentUser.permissions()`. This is deliberate (see the class javadoc, referencing the `SuperAdminAuthority` authority) — the SUPERADMIN role is never assembled from the `permissions`/`role_permission` tables in `onboarding-auth-rbac`, so a compromised permissions table can't silently escalate anyone to superadmin.
- **Two auth mechanisms, one `CurrentUser` shape.** JWT-authenticated requests produce a `JwtAuthenticationToken`; HMAC-signed third-party requests produce an `ApiKeyAuthenticationToken` with a `CurrentUser` as its principal directly. `CurrentUserResolver.resolve()` normalizes both into the same `CurrentUser` record so downstream service code never needs to know which path a request came in on.
- **Rate limiting and idempotency's Redis path both fail open**, by design — losing Redis degrades rate limiting to "unlimited" and idempotency to "DB-constraint-only," rather than blocking traffic. Don't add a rate-limit or idempotency check that assumes Redis being down is fatal.
- **`EncryptionService` is a placeholder for real KMS-backed envelope encryption** (see its class javadoc TODO) — it's a single symmetric key from `nawill.security.encryption-key`, not a stand-in you should assume is production-hardened as-is.

## Depends on / depended on by

Per the README's build order (`common-core` → `reference-data` → `payments` → `onboarding-auth-rbac` → `app`), `common-core` is built first and has no dependency on any other module in this repo. Every other module depends on it. Concretely:
- `reference-data`, `payments`, `onboarding-auth-rbac` all use `BaseEntity`, the exception hierarchy, `CurrentUserResolver`/`PermissionChecker`, and (where relevant) `@Idempotent`, `EncryptionService`, `HmacSigner`, `RateLimitService`.
- `app` is the composition root that pulls everything together; it doesn't add its own domain logic, just main class, profiles, Flyway migrations, and logging config.
