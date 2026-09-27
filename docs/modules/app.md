# app

## What it owns

The composition root. `app` is the only module with a `main()`
(`NawillPayApplication`), and it component-/entity-/repository-scans
`ng.com.nawill.pay.*` — every other module — so it's the one place that
needs to know all of them exist (`app/src/main/java/ng/com/nawill/pay/app/NawillPayApplication.java`).
It also owns the Spring profile configs, the entire Flyway migration
history (regardless of which module a table logically belongs to), and
structured-logging config. There's no business logic here — just wiring.

## Key files

- `app/src/main/java/ng/com/nawill/pay/app/NawillPayApplication.java` —
  `@SpringBootApplication(scanBasePackages = "ng.com.nawill.pay")` +
  `@EntityScan`/`@EnableJpaRepositories` on the same base package.
- `app/src/main/resources/application.yml` — base config for every
  profile: JPA/Flyway/datasource/Redis wiring, JWT/superadmin/encryption-key/Paystack
  config (all via required `${VAR}` placeholders with no default, except
  Paystack's `secret-key` which defaults to empty — see Gotchas), server
  port, actuator (`health`,`info` only), springdoc path.
- `application-dev.yml` / `application-staging.yml` / `application-prod.yml`
  — profile overlays, see below.
- `logback-spring.xml` — JSON structured logging via
  `logstash-logback-encoder`; every line carries timestamp, level,
  `requestId`/`userId` (MDC, set by common-core's `RequestIdFilter`/
  `UserContextMdcFilter`), and `module` (via common-core's
  `ModuleJsonProvider`, derived from the logger's package).
- `.env.properties` — not app config, a `spring-dotenv` control file (see
  Gotchas).

## Spring profiles

- **`dev`**: safe, non-secret defaults for everything (`application-dev.yml`)
  — Postgres/Redis pointed at `docker-compose.yml`'s ports (`15432`/`16379`),
  a hardcoded dev JWT secret and encryption key, `superadmin@nawill.com.ng`
  / `ChangeMe123!`, `show-sql: true`, `DEBUG` logging. Works with zero
  environment variables set.
- **`staging`** / **`prod`**: no defaults at all — every value in the base
  `application.yml`'s `${VAR}` placeholders must come from a real
  environment variable, since staging/prod override nothing except
  `show-sql: false`, health-details visibility, and `INFO` logging level.
  `prod`'s comment is explicit: real credentials, real money movement,
  injected via a secrets manager at deploy time, never committed.
- Selected via `-Dspring-boot.run.profiles=<dev|staging|prod>` or
  `SPRING_PROFILES_ACTIVE` (README's "Environment variables" section).

## Flyway migrations

`app` owns `app/src/main/resources/db/migration/` as the single migration
path for the whole schema — `reference-data`'s and `payments`' tables are
created here too, not split by module. Current head: **V0030**.

```
V0001 create_countries_table          V0016 create_bank_accounts_table
V0002 create_states_table             V0017 create_settlement_accounts_table
V0003 create_banks_table              V0018 add_auto_settle_to_virtual_accounts
V0004 create_users_table              V0019 create_collection_accounts_table
V0005 create_business_table           V0020 create_settlements_table
V0006 add_users_business_fk           V0021 create_api_keys_table
V0007 create_roles_table              V0022 create_api_key_ip_whitelist_table
V0008 create_permissions_table        V0023 create_payment_links_table
V0009 create_role_permission_table    V0024 seed_settlement_apikey_paymentlink_permissions
V0010 create_user_role_table          V0025 create_dynamic_virtual_accounts_table
V0011 create_payment_processors_table V0026 add_capital_to_states
V0012 create_virtual_accounts_table   V0027 seed_countries
V0013 create_transactions_table       V0028 seed_nigeria_states
V0014 create_idempotency_records_table V0029 seed_nigeria_lgas
V0015 seed_permissions                V0030 seed_nigeria_banks
```

V0027-V0030 are generated, not hand-written — see
[`docs/modules/reference-data.md`](reference-data.md) for
`scripts/generate-seed-migrations.py`.

## Testing conventions

`AbstractIntegrationTest` (`app/src/test/java/.../app/it/AbstractIntegrationTest.java`)
starts one Postgres + one Redis Testcontainer in a **static initializer**
rather than via `@Testcontainers`/`@Container` — that JUnit5 extension was
observed to start a fresh container pair (and fresh Spring context) per
`*IT` class instead of sharing one across the run; manual start guarantees
exactly one pair for the whole suite, and Ryuk cleans them up at JVM exit.
Real Postgres + Redis always — never H2/embedded (doc 3 §5.2).

It also carries `@ActiveProfiles("test")`. That's what activates
`FakeBankVerificationGateway` over the live `PaystackBankVerificationGateway`
in the `payments` module (`@Profile("test")` vs `@Profile("!test")`), so
the integration suite never depends on Paystack's sandbox being reachable
— the first and so far only external HTTP dependency in the codebase. See
[`docs/modules/payments.md`](payments.md).

12 `*IT.java` classes live in `app/src/test/java/.../app/it/`, each
extending `AbstractIntegrationTest` and driving the app over real HTTP via
`TestRestTemplate` (with Apache HttpClient5 swapped in — the JDK's default
request factory throws on a 401 to a POST with a body).

Per README: `./mvnw test` for unit tests (no Docker), `./mvnw -pl app -am verify`
for the full Testcontainers integration suite (Docker required) — that's
also the CI-required `verify` check.

## Running locally

Fully covered in the root [`README.md`](../../README.md#running-locally) —
don't duplicate it here. Two things worth knowing that supplement it:

- `.env` at the repo root auto-loads via `spring-dotenv` (see Gotchas
  below) — no manual `export`/`source` needed for local dev.
- `scripts/wait-for-local-services.sh` blocks until a natively-run (non-Docker)
  Postgres/Redis are actually accepting connections, for anyone who
  installed those directly instead of via `docker-compose.yml`.

## How to extend it

- **New migration**: next free `Vnnnn`, `V{nnnn}__{snake_case_description}.sql`
  in `app/src/main/resources/db/migration/`. Flyway history is immutable
  once applied — never edit an existing `Vnnnn` file, only add new ones.
- **New profile-specific config**: add the key to base `application.yml`
  as a required `${VAR}` (no default) if it must be explicit in
  staging/prod, then only override it in `application-dev.yml` if dev
  needs a safe non-secret default — that's the existing pattern for every
  secret (JWT, encryption key, Paystack key, DB/Redis creds).

## Gotchas / non-obvious behavior

- `spring-boot:run` must be invoked as `./mvnw -f app/pom.xml spring-boot:run`,
  **not** `./mvnw -pl app -am spring-boot:run` — the goal isn't bound to a
  lifecycle phase, so `-pl`/`-am` runs it against every reactor project
  including the root aggregator `pom`, which fails immediately trying to
  run a `pom`-packaged project as a Spring Boot app. README explains this
  in detail.
- `.env.properties` isn't application config — it's read by the
  `spring-dotenv` library (added in `payments`/root `pom.xml`) to locate
  the actual `.env` file. Its one line, `directory=${maven.multiModuleProjectDirectory}`,
  is Maven-resource-filtered at build time to the repo's absolute path, so
  `.env` auto-loads correctly regardless of which directory `mvnw` was
  invoked from. `app/pom.xml`'s `<resources>` block deliberately filters
  *only* this one file — everything else in `src/main/resources` is
  copied unfiltered, so `application.yml`'s `${NAWILL_DB_URL}`-style
  placeholders aren't touched at build time (they must stay literal for
  Spring to resolve at runtime).
- Paystack config is bound to the typed, validated `PaystackProperties`
  (common-core, registered by `@ConfigurationPropertiesScan`) from
  `PAYSTACK_PRIVATE_KEY` - one key per environment, the app never chooses
  test vs live. Optional (empty default): unset, name enquiry answers
  `BANK_VERIFICATION_UNAVAILABLE` and BVN checks use the fallback.

## Depends on / depended on by

Depends on all four other modules — `common-core` → `reference-data` →
`payments` → `onboarding-auth-rbac` → `app`, per the README's stated build
order. Nothing depends on `app`; it's the leaf.
