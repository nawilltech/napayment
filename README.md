# Nawill Pay — Backend (MVP v0.1)

Java 17 / Spring Boot 3 modular-monolith API for Nawill Pay, a Nigerian payment
collection and processing platform. This is the v0.1 MVP scope only: onboarding +
auth + RBAC, virtual account provisioning, a single (sandbox) payment processor
integration, transaction creation with idempotency, and admin RBAC. See
[`docs/`](docs/) for the full requirements, architecture, security, and
naming-convention specs this build implements.

## Module layout

```
common-core            base entity, exceptions, @Idempotent + interceptor,
                        structured-logging filters, OpenAPI config
reference-data         countries, states (3-level hierarchy), banks
payments                virtual accounts, transactions, idempotency,
                        payment processor stub
onboarding-auth-rbac    signup/login, JWT, roles/permissions, users, business
app                     composition root: main class, Spring profiles,
                        Flyway migrations, logging config, docker-compose
```

Build order: `common-core` → `reference-data` → `payments` →
`onboarding-auth-rbac` → `app`. `app` is the only module with a `main()` — it
component-/entity-/repository-scans `ng.com.nawill.pay.*`, so it needs to see
every other module.

## Prerequisites

- Java 17
- Docker (for local Postgres/Redis, and for the Testcontainers-based
  integration tests)
- No local Maven install required — use the bundled `./mvnw`

## Running locally

1. Start Postgres and Redis:

   ```bash
   docker compose up -d
   ```

   This exposes Postgres on `15432` and Redis on `16379` (not the standard
   `5432`/`6379`) — deliberately non-default, because a machine running
   several local projects often already has something bound to the standard
   ports. If `15432`/`16379` are also taken on your machine, change the port
   mapping in `docker-compose.yml` and the matching `spring.datasource.url` /
   `spring.data.redis.port` in `app/src/main/resources/application-dev.yml`
   together.

2. Run the app against the `dev` profile:

   ```bash
   ./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   The `dev` profile ships with safe non-secret defaults (matching
   `docker-compose.yml`) for the datasource, Redis, JWT signing secret, and
   the seeded SUPERADMIN's credentials, so this works out of the box with no
   environment variables set. Flyway runs automatically on startup.

3. The API is at `http://localhost:8080`. Swagger UI:
   `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at
   `/v3/api-docs`). Health check: `http://localhost:8080/actuator/health`.

4. A platform SUPERADMIN is seeded automatically on first startup (see
   `SuperAdminSeeder`) using `AUTH_SUPERADMIN_EMAIL` / `AUTH_SUPERADMIN_PASSWORD`
   (defaults in `dev`: `superadmin@nawill.com.ng` / `ChangeMe123!` — change
   these for any non-local environment).

### Quick smoke test

```bash
# Sign up (auto-provisions a virtual account, FR-1)
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phoneNo":"08011112222","password":"SecurePass123"}'

# Log in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ada@example.com","password":"SecurePass123"}'

# Create a transaction (requires Idempotency-Key; token from signup/login)
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"virtualAccountId":"<id>","paymentProcessorId":"<id>","transactionType":"CREDIT","amount":5000}'
```

## Running the tests

Unit tests (fast, no Docker required):

```bash
./mvnw test
```

Integration tests (Testcontainers — spins up **real** Postgres + Redis
containers, never H2/embedded; requires Docker to be running):

```bash
./mvnw -pl app -am verify
```

This covers: signup auto-provisions a virtual account; login issues a JWT and
a protected endpoint rejects requests without one; creating a transaction
without an `Idempotency-Key` is rejected; creating one with a key and
replaying the same key returns the identical response with zero duplicate
rows; a USER-role token is rejected (403) from an admin-only endpoint; and a
failed-auth attempt's `requestId` is verified present in the structured log
output. Test classes live in `app/src/test/java/.../app/it/`.

## Git workflow & branch protection

`feature/<ticket>-<slug>` / `fix/<ticket>-<slug>` → PR into `dev` → PR `dev` →
`main` (doc 4 §B.1 naming, with a `dev` integration branch in front of
`main`). `dev` is the repository's default branch, so new PRs target it
unless you explicitly point at `main`.

Both `dev` and `main` are protected on GitHub:

- **No direct pushes** — enforced for admins too; every change goes through a PR.
- **Required status check**: `verify` ([`.github/workflows/ci.yml`](.github/workflows/ci.yml),
  which runs `./mvnw clean verify` — compile, unit tests, and the
  Testcontainers integration suite) must pass, and the branch must be up to
  date with its base, before a PR can merge.
- **No force-pushes, no branch deletion.**
- **0 required approving reviews** — this is currently a solo repo, so
  requiring an external approval would lock out merging your own PRs. Bump
  `required_approving_review_count` on both branches once there's a second
  collaborator:
  ```bash
  gh api -X PUT repos/ushashir/napayment/branches/<branch>/protection/required_pull_request_reviews \
    -f required_approving_review_count=1
  ```

Feature/fix branches are auto-deleted on merge (`delete_branch_on_merge`).

## Environment variables

None of these are required for `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
(the `dev` profile has working defaults) — they're required for `staging`/`prod`,
which intentionally have none.

| Variable | Purpose |
|---|---|
| `NAWILL_DB_URL` | Postgres JDBC URL, e.g. `jdbc:postgresql://host:5432/nawill_pay` |
| `NAWILL_DB_USERNAME` | Postgres username |
| `NAWILL_DB_PASSWORD` | Postgres password |
| `NAWILL_REDIS_HOST` | Redis host |
| `NAWILL_REDIS_PORT` | Redis port |
| `AUTH_JWT_SECRET` | HMAC-SHA256 signing secret for access tokens (≥32 bytes) |
| `AUTH_JWT_EXPIRY_MINUTES` | Access token lifetime in minutes (optional, default `15`) |
| `AUTH_SUPERADMIN_EMAIL` | Seeded platform SUPERADMIN's login email (FR-5) |
| `AUTH_SUPERADMIN_PASSWORD` | Seeded platform SUPERADMIN's password |
| `SERVER_PORT` | HTTP port (optional, default `8080`) |

Spring profile is selected via `-Dspring-boot.run.profiles=<dev|staging|prod>`
or `SPRING_PROFILES_ACTIVE` — no ad-hoc profile names (doc 4 §B.5).

## What's deliberately out of scope (v0.1)

Flagged as `TODO(FR-x)` at the relevant extension point in code: KYC/2FA
(FR-8/FR-8a), Address/reference-data linkage beyond countries/states/banks,
configurable fee tiers (FR-11 — `charge` is currently always `0`), payment
links (FR-14), split settlement to external bank accounts (FR-2 —
transactions credit/debit the virtual account directly), processor webhooks
and reconciliation (FR-7/FR-Recon-1/2), and refresh-token rotation (doc 3
§2.1 — access tokens only for now).
