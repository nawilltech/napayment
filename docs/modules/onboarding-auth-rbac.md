# onboarding-auth-rbac

## What it owns

Identity and access: signup/login, password reset, JWT issuance/validation,
the role/permission catalog, the `Business` entity, the seeded platform
SUPERADMIN, and business-scoped API key management (generation, rotation, IP
whitelisting) used for third-party HMAC-signed requests into the `payments`
module's collect/withdraw endpoints.

## Key classes

**Auth** (`onboarding-auth-rbac/src/main/java/ng/com/nawill/pay/onboarding/auth/`)
- `AuthController` / `AuthService` — signup, login, forgot/reset/change password
- `JwtService` — issues HS256 access tokens (`security/JwtService.java`)
- `LoginAttemptService`, `PasswordResetService` — lockout and reset-token flows

**Users / Business**
- `user/User.java`, `user/UserType.java` (`USER` / `SUPERADMIN`)
- `business/Business.java`

**Roles / Permissions** (`rbac/`)
- `Role`, `Permission`, `RolePermission`, `UserRole` entities
- `RoleService` / `RoleController` — business-scoped custom roles
- `PermissionResolutionService` — assembles a user's permission set from `role_permission` at token-issuance time

**API keys** (`apikey/`)
- `ApiKeyCredential`, `ApiKeyIpWhitelist` entities
- `ApiKeyService` / `ApiKeyController` — generate/regenerate/list, IP whitelist CRUD

**Security** (`security/`)
- `JwtService`, `SecurityConfig`
- `ApiKeyAuthenticationFilter` — HMAC auth for `/api/v1/collect` and `/api/v1/withdraw`
- `CachedBodyHttpServletRequest` — lets the filter re-read the body for signature verification after Spring would otherwise consume the stream once

**Bootstrap**
- `bootstrap/SuperAdminSeeder.java` — `ApplicationRunner`, seeds SUPERADMIN at startup

## Endpoints

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | none | Create a user (+ business, if `businessName`/`cacNumber` given); auto-provisions a virtual account and assigns a default role |
| POST | `/api/v1/auth/login` | none | Issue an access token |
| POST | `/api/v1/auth/forgot-password` | none | Start a password reset |
| POST | `/api/v1/auth/reset-password` | none | Complete a password reset |
| POST | `/api/v1/auth/change-password` | none (authenticated) | Change password for the current session |
| POST | `/api/v1/roles` | `roles:manage` | Create a business-scoped custom role |
| GET | `/api/v1/roles` | `roles:manage` | List roles for the caller's business |
| POST | `/api/v1/api-keys` | `apikeys:manage` | Issue the business's API key pair (fails if one is already active) |
| POST | `/api/v1/api-keys/regenerate` | `apikeys:manage` | Rotate the pair — marks the old one `INACTIVE`, issues a new one |
| GET | `/api/v1/api-keys` | `apikeys:manage` | List the business's active key (metadata only, no secret) |
| POST | `/api/v1/api-keys/ip-whitelist` | `apikeys:manage` | Add a CIDR to the active key's whitelist |
| GET | `/api/v1/api-keys/ip-whitelist` | `apikeys:manage` | List whitelisted CIDRs |
| DELETE | `/api/v1/api-keys/ip-whitelist?cidr=` | `apikeys:manage` | Remove a CIDR |

`/api/v1/collect` and `/api/v1/withdraw` live in `payments` (`thirdparty/`) but are authenticated entirely by this module's `ApiKeyAuthenticationFilter`.

## How auth actually works

`JwtService.issueAccessToken` signs an HS256 JWT (`nimbusds`) with claims:
`sub` = user id, plus `CurrentUserResolver.CLAIM_USER_TYPE`,
`CLAIM_PERMISSIONS` (list of permission strings), and `CLAIM_BUSINESS_ID`
when the user has a business. `common-core`'s `CurrentUserResolver` reads
those claims back into a `CurrentUser` per request. `@PreAuthorize("@auth.can('x')")`
on a controller method resolves against that `CurrentUser`'s permission set —
`PermissionResolutionService.resolveFor(userId)` is what assembles it at
login/signup time, by walking `user_role` → `role_permission` → `permission`.
SUPERADMIN never goes through this resolution — `PermissionChecker` in
`common-core` hard-codes an unconditional bypass for `UserType.SUPERADMIN`
instead, so a compromised or empty `permissions` table can't silently grant
superadmin access.

## SUPERADMIN seeding

`SuperAdminSeeder` (an `ApplicationRunner`) checks `userRepository.existsByEmail(email)`
on every startup and is a no-op if it already exists — safe to run on every
boot, not just first-run. Credentials come from `nawill.auth.superadmin.email`
/ `.password` (`AUTH_SUPERADMIN_EMAIL` / `AUTH_SUPERADMIN_PASSWORD`), never a
hardcoded default password. There is deliberately no signup/admin endpoint
that can create a second SUPERADMIN (FR-5) — the only way in is this seeder.

## Business API keys

`ApiKeyService.generate()` issues one `pk_live_<32 hex>` / `sk_live_<48 hex>`
pair per business; a second `generate()` call while one is `ACTIVE` is
rejected (`API_KEY_ALREADY_EXISTS`). `regenerate()` flips the existing row to
`INACTIVE` (kept for audit, not deleted) before issuing a new pair — the old
secret stops authenticating immediately. The secret is stored encrypted
(`common-core`'s `EncryptionService`) and only ever returned once, in the
generate/regenerate response body.

Third-party requests to `/api/v1/collect` and `/api/v1/withdraw` are verified
by `ApiKeyAuthenticationFilter`, which runs before Spring MVC dispatch (so it
writes `ErrorResponse` JSON directly rather than throwing — a
`@RestControllerAdvice` never sees filter-level exceptions). Required
headers: `X-Public-Key`, `X-Timestamp` (unix seconds), `X-Signature`. The
signature is HMAC-SHA256 (`common-core`'s `HmacSigner`) over the canonical
string `"{timestamp}.{rawBody}"`, keyed by the decrypted secret key. The
filter also enforces, in order: a per-IP rate limit, a per-API-key rate
limit, a clock-skew window (`nawill.auth.apikey.max-clock-skew-seconds`)
against `X-Timestamp` to block replay, the signature check itself, then an
optional CIDR whitelist (skipped entirely if the business has whitelisted no
IPs). On success it populates the security context with an
`ApiKeyAuthenticationToken` wrapping a `CurrentUser` that has exactly
`collect:create` and `withdraw:create` — nothing else — so
`@auth.can(...)` works unchanged in the downstream controller without those
two permissions needing to exist in the seeded permission catalog for a real
user/role.

## How to extend it

- **New permission**: add it to the seed data (`V0015__seed_permissions.sql`
  / `V0024__seed_settlement_apikey_paymentlink_permissions.sql` are the
  existing examples — add a new migration, don't edit those), then assign it
  to a role via `role_permission` (either in the same migration for a
  platform-default role, or through `POST /api/v1/roles` for a business's
  own custom role).
- **New role**: `RoleService.createBusinessScopedRole` — business-scoped
  roles are created through the API; platform-default roles (`USER`,
  `BUSINESS_OWNER`, `SUPPLY_ADMIN`) are seeded directly in SQL with
  `business_id = NULL`.
- **New API-key-authenticated endpoint**: anything under the filter's
  matched paths automatically gets a `CurrentUser` with only
  `collect:create`/`withdraw:create` — to expose a new capability this way
  you'd need to extend the hardcoded permission set in
  `ApiKeyAuthenticationFilter`, which is a deliberate, narrow allowlist, not
  a general auth mechanism.

## Gotchas / non-obvious behavior

- The seeded permission catalog is intentionally minimal (7 permissions as
  of `V0015`, a few more added per-feature in later migrations) — it is not
  meant to be exhaustive up front; each new business capability seeds its
  own permission(s) alongside the migration that adds the feature.
- SUPERADMIN is never assigned rows in `role_permission` — its access is a
  code-level bypass, specifically so a data-layer compromise of the
  permissions table cannot grant superadmin elsewhere.
- `regenerate()` never deletes the old `ApiKeyCredential` row — it's kept
  `INACTIVE` for audit trail, which is why `ApiKeyAuthenticationFilter`
  explicitly filters `findByPublicKey(...)` results to `status == ACTIVE`.
- The IP whitelist is opt-in per key: an empty whitelist means *no*
  IP restriction is enforced, not that all IPs are blocked.

## Depends on / depended on by

Depends on `common-core` and `payments` (for `VirtualAccountProvisioningService`,
called from `AuthService.signup` to auto-provision a virtual account on
signup) and `reference-data` transitively — matches the README's stated
build order (`common-core → reference-data → payments → onboarding-auth-rbac → app`).
`app` depends on this module to compose the full application.
