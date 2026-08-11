# NAWILL PAY

## Security, Idempotency & Deployment Deep-Dive

*Engineering Deep-Dive — RBAC, SSL/TLS, IP Whitelisting, NIBSS, CI/CD, Load & Observability*

Version 0.1 (MVP Draft)

Prepared by: Nawill Technology Ltd — Engineering

Date: August 2026

## 1. Idempotency Handling

Payments are the single highest-risk area for duplication: a retried request (client timeout, mobile-network drop, load-balancer retry) must never result in a user being double-charged or double-credited. Idempotency is treated as a first-class, platform-wide concern, not a per-endpoint afterthought.

### 1.1 Idempotency Key Design

- Every mutating financial operation (initiate transaction, settle, disburse, create payment link with a value attached) requires an Idempotency-Key header, generated client-side (UUID v4) or, if absent, generated server-side from a deterministic hash of (accountId + amount + recipient + narration + minute-bucket) as a fallback safety net.

- The key is persisted with a unique constraint on the transactions.idempotencyKey column. A second request with the same key returns the original response (replayed, not reprocessed) rather than executing business logic again.

- Redis is used as a short-lived (e.g. 24h) idempotency lock: on first sight of a key, a lock record is SET NX with a TTL; a concurrent duplicate request arriving while the first is still processing is rejected with 409 Conflict / "processing in progress" rather than racing the database constraint.

- The lock/response cache stores the eventual HTTP status and response body, so a legitimate retry after success gets the same 200 response instead of an error.

### 1.2 Idempotency at the Database Layer

- The unique constraint on idempotencyKey is the source of truth — Redis is an optimization, never the sole guard, since Redis availability is not guaranteed under NFR-2 (network resilience).

- State transitions (PENDING → PROCESSING → PAID/FAILED) use optimistic locking (a version column) so that two workers cannot both flip the same transaction from PENDING to PAID.

- Webhook handlers from payment processors are themselves idempotent: each inbound webhook event carries the processor's own event ID, stored in a processed_webhook_events table with a unique constraint, so a re-delivered webhook is a no-op after the first successful application.

### 1.3 Idempotency Across the Reconciliation Job

The 6-hourly reconciliation job (FR-Recon-1) itself must be idempotent and safe to re-run: it operates by comparing sets of transaction IDs already marked reconciled against processor/NIBSS statements, and only ever moves a transaction from un-reconciled to reconciled — it never re-applies a settlement action for a transaction already flagged as settled.

## 2. Security Best Practices Against Hacking / Fraud

### 2.1 Authentication & Session Security

- Password hashing via bcrypt/Argon2id (never reversible encryption); minimum complexity and breach-list checks (e.g. HaveIBeenPwned range API) at signup/change.

- JWT access tokens, short-lived (10–15 min), with rotating refresh tokens stored server-side (allow revocation) — a stolen access token has a small blast-radius window.

- 2FA (configurable per FR-8) enforced for privileged roles (ADMIN, SUPERADMIN) and for any transaction above the Tier-2 KYC threshold.

- Account lockout / exponential backoff after repeated failed login attempts; alerting on impossible-travel or new-device login.

### 2.2 RBAC — Role-Based Access Control

- Enforced at two layers: (a) method-level via Spring Security @PreAuthorize on service methods, expressed against explicit permission strings (transactions:read, processors:configure), never bare role names, so permissions can be re-composed into new roles without code changes; and (b) endpoint-level via a security filter chain mapping URL patterns to required authorities as a defence-in-depth backstop.

- SUPERADMIN is seeded at deployment time (not created through any signup flow) and its permission set is hard-coded to "all" rather than assembled from the Permission table, so a data-layer compromise of the permissions table cannot silently grant superadmin elsewhere.

- Business-created admin/account-officer roles are always scoped by businessId at the query layer (row-level filtering), so even a misconfigured permission cannot leak cross-tenant data — every repository query for business-scoped entities includes the caller's businessId as a mandatory predicate, not an optional filter.

- Configuring/activating a payment processor (FR-6) is gated behind a distinct permission (processors:configure) granted only to a Supply Admin role, separate from general ADMIN.

### 2.3 Transport & Data Security

- SSL/TLS 1.2+ enforced everywhere, terminated at the API gateway/load balancer; HSTS enabled; TLS certificates auto-renewed (e.g. via Let's Encrypt/ACM) with monitoring for expiry.

- Encryption at rest for the database (managed Postgres disk encryption) plus field-level encryption for the most sensitive PII (BVN, NIN, CAC number, bank account numbers) using envelope encryption with keys held in a managed KMS (AWS KMS / GCP KMS / HashiCorp Vault) — application code never touches raw key material.

- IP whitelisting for: (a) admin portal access from known office/VPN ranges, (b) PSSP/partner API-key usage restricted to the IP ranges the partner registers at onboarding (FR-9), and (c) inbound webhook endpoints restricted to each payment processor's published IP ranges, combined with signature verification (HMAC of the payload using the processor's webhook secret) so IP spoofing alone cannot forge a webhook.

- Secrets (DB credentials, processor API keys, JWT signing keys) are never committed to source control; managed via a secrets manager injected at deploy time, rotated on a schedule and immediately on suspected compromise.

### 2.4 Application-Layer Hardening

- Input validation via Bean Validation (jakarta.validation) on every DTO; parameterized queries only (JPA/Hibernate prevents SQL injection by construction — raw/native queries are code-reviewed for parameter binding).

- Rate limiting per API key / per IP at the gateway (token-bucket in Redis), tuned to a level below the NFR-10 throughput ceiling so an abusive client cannot starve legitimate traffic.

- CSRF protection on any cookie-authenticated surface (admin portal); CORS restricted to known origins.

- Dependency scanning (OWASP Dependency-Check / Snyk) and static analysis (SonarQube) run in CI on every pull request; container images scanned for CVEs before promotion.

- Regular penetration testing and, ahead of scaling, a formal PCI-DSS-aligned review given the platform's role in handling payment data.

## 3. NIBSS Integration Considerations

NIBSS (Nigeria Inter-Bank Settlement System) provides the interbank rails (NIP — NIBSS Instant Payment) that move real money between Nawill Pay's partner settlement bank and external banks. Nawill Pay does not integrate with NIBSS directly at MVP; it accesses NIBSS capability indirectly through its partner commercial/microfinance bank or a licensed processor (FR-10), which is the standard route for a non-deposit-taking fintech in Nigeria.

- Name Enquiry: before any settlement/disbursement, resolve the destination account name via the partner bank's NIBSS name-enquiry endpoint and require the user to confirm the resolved name — reduces misdirected-funds fraud.

- Fund Transfer (NIP): outbound settlements are submitted through the partner bank's NIP integration; the platform tracks NIBSS session IDs/reference codes against the internal transactionId for traceability.

- Reversal handling: NIBSS transactions can be reversed by the receiving bank in specific failure scenarios; the reconciliation job explicitly checks for reversal codes and reflects them back onto the internal ledger rather than leaving a phantom credit.

- Settlement account (FR-10) is the pooled account where real NIBSS deposits land; internal virtual-account balances are always a ledger abstraction over this pooled account, and the sum of all virtual-account balances must reconcile to the pooled account balance — this invariant is checked by the reconciliation job and alerts on drift.

- Cut-off times and settlement windows: NIP is largely real-time, but batch/NEFT-style rails used as a fallback (for high-value or after-hours transfers) have defined cut-off windows that the disbursement scheduler must respect.

## 4. Git Workflow & Branching Strategy

### 4.1 Branching Model

Trunk-based development with short-lived feature branches, chosen over long-lived GitFlow branches to keep a fast-moving small team merging frequently and reduce painful integration conflicts.

|  |  |
|----|----|
| **Branch** | **Purpose** |
| main | Always deployable. Every merge to main triggers a Staging deployment (see §5). Protected — no direct pushes, PR + passing CI + 1 approval required. |
| feature/\<ticket\>-\<slug\> | Short-lived branch per unit of work, e.g. feature/NW-142-payment-links. Branched from main, rebased regularly, squash-merged back. |
| fix/\<ticket\>-\<slug\> | Bug fixes, same conventions as feature/. |
| release/\<version\> | Cut from main when preparing a Production promotion; only cherry-picked hotfixes land here; tagged on release. |
| hotfix/\<ticket\>-\<slug\> | Urgent production fix, branched from the latest production tag, merged to both release/main. |

### 4.2 Commit & PR Conventions

Full naming and commit-message conventions are defined in the companion Contribution Guide & Naming Conventions document. In summary: Conventional Commits format (feat:, fix:, chore:, refactor:, docs:, test:), PR titles mirror the primary commit, and every PR must link its tracking ticket.

## 5. Deployment Strategy

### 5.1 Environments

|  |  |  |
|----|----|----|
| **Environment** | **Trigger** | **Characteristics** |
| Development | Local / on feature-branch push | Local Docker Compose stack (Postgres, Redis, Kafka) or a shared dev namespace; seeded synthetic data; processor integrations run in sandbox mode. |
| Staging | Automatic on merge to main | Mirrors production topology at lower capacity; sandbox/test credentials for payment processors and NIBSS; used for QA, UAT, and load-test rehearsal. |
| Production | Manual promotion from a tagged release/\* branch, requiring sign-off | Live credentials, real money movement, full monitoring/alerting, IP-whitelisted admin access only. |

### 5.2 CI/CD Pipeline

1.  Lint & static analysis (Checkstyle/SonarQube) on every push.

2.  Unit tests (JUnit 5 + Mockito) — required to pass, coverage threshold enforced (e.g. 80% on the Payments and Auth modules).

3.  Integration tests (Testcontainers spinning up real Postgres/Redis/Kafka) — validate module boundaries and repository behaviour against a real database engine, not H2.

4.  Contract tests for external integrations (payment processors, NIBSS name-enquiry) against recorded/mocked responses (WireMock) so pipeline runs don't depend on third-party sandbox uptime.

5.  Build & scan — Maven build, dependency vulnerability scan, Docker image build and image CVE scan.

6.  Deploy to Staging automatically; run smoke tests and a synthetic end-to-end transaction against sandbox processors.

7.  Manual approval gate, then deploy to Production using a rolling/blue-green strategy (never a hard cutover) so a bad release can be rolled back without downtime.

8.  Post-deploy health checks and automatic rollback on failed readiness/liveness probes.

### 5.3 Rollback & Migrations

- Database migrations (Flyway/Liquibase) are additive/backward-compatible within a release (expand-contract pattern) so the previous application version keeps working against the new schema during a rollback window.

- Feature flags gate risky functionality (e.g. a new processor integration) so it can be disabled instantly without a redeploy.

## 6. Withstanding Heavy Load

Target (NFR-10): comfortably process ~₦1 billion in transaction value per day, at a sustained peak of roughly 1,000 requests/second, with acceptable latency.

- Horizontal scaling: the Spring Boot service is stateless (JWT-based auth, sessions in Redis) so it scales horizontally behind a load balancer / Kubernetes HPA driven by CPU and request-latency metrics.

- Database: connection pooling (HikariCP) tuned per instance; read replicas for reporting/analytics queries so heavy report generation never contends with the transactional write path; partitioning/indexing strategy on the Transactions table (by createdAt month) to keep hot-path queries fast as volume grows.

- Caching: Redis absorbs repeated reads of rarely-changing config (fee tiers, processor status, bank list), reducing database load under burst traffic.

- Async offload: anything not required for the synchronous payment-confirmation response (notifications, audit-log writes, webhook dispatch to partners) is pushed to Kafka and processed by separate consumer workers, keeping the critical path short.

- Backpressure & circuit breakers (Resilience4j) around every external dependency (payment processors, NIBSS/partner bank, SMS provider) so a slow/unavailable third party degrades gracefully — e.g. queue-and-retry — instead of exhausting request threads.

- Load testing (k6/Gatling) against Staging ahead of major releases, explicitly targeting the NFR-10 numbers, with results tracked over time to catch regressions.

## 7. Logging & Observability

### 7.1 Structured, Correlated Logging

- Every API request receives (or is assigned, if absent) a Request-ID on the request header. This request-id is propagated through the MDC (Mapped Diagnostic Context) from the controller layer down through every service/repository call, and is included in every log line for that request, enabling full request tracing across a distributed call graph without a separate tracing tool at MVP stage (though the design is compatible with adding OpenTelemetry/Jaeger later).

- Logs are structured (JSON) rather than freeform text, shipped to a centralized log store (e.g. ELK/OpenSearch or a managed equivalent), with consistent fields: timestamp, level, requestId, userId (if authenticated), module, message, and a redaction filter that strips PII/secrets (BVN, NIN, full card/account numbers, tokens) before persistence.

### 7.2 What Gets Logged

|  |  |
|----|----|
| **Log Type** | **Captured Detail** |
| Application logs | Request/response summaries (not full bodies for sensitive endpoints), errors with stack traces, external-call latencies. |
| UserChangeLog / activity | Every account activity — created, updated, login, logout, IP address, device fingerprint — persisted to the database, not just log files, for compliance retention. |
| Transaction audit trail | Every state transition of a transaction (PENDING → PROCESSING → PAID/FAILED), who/what triggered it, and the idempotency key involved. |
| Admin action logs | Every privileged action — processor activation, fee-tier change, role/permission change — logged with the acting admin's identity, immutable. |
| Security events | Failed logins, permission-denied events, IP-whitelist rejections, webhook signature failures — feed into alerting. |

### 7.3 Monitoring & Alerting

- Metrics via Micrometer → Prometheus/Grafana: request rate, error rate, latency percentiles (p50/p95/p99) per endpoint, queue depth, reconciliation drift.

- Alert thresholds tied directly to the NFRs — e.g. alert if p95 latency breaches SLA, if error rate exceeds a threshold, or if the reconciliation job detects unreconciled value above a configurable amount.

- On-call rotation and runbooks for the highest-risk failure modes: processor outage, NIBSS/partner-bank outage, reconciliation drift, and a spike in failed authentications (possible credential-stuffing attack).
