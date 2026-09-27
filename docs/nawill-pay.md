# NAWILL PAY

## Master Specification & Engineering Handbook

*Consolidated: Requirements Specification · Technical Architecture & Database Schema · Security, Idempotency & Deployment Deep-Dive · Contribution Guide, Naming Conventions & Process Flows*

Version 0.1.x (MVP core + Settlement, Collection Account, API Keys, Payment Links & Dynamic Accounts)

Prepared by: Nawill Technology Ltd — Engineering

Date: August 2026

Market Focus: Nigeria, expanding to Sub-Saharan Africa

---

This single document consolidates what were previously four separate specs into one, each retained as a numbered chapter below. **Citation scheme is unchanged and load-bearing**: throughout the codebase (Java javadoc, SQL migration comments, this document's own cross-references) you'll see citations like `doc 1 §3.2`, `FR-2`, `doc 3 §2.5`, `doc 2 §7 ADR-6` — "doc N" refers to the chapter number below (Chapter 1 = doc 1, Chapter 2 = doc 2, etc.), and every `§` section number inside a chapter is exactly what it was in that chapter's original standalone file. Nothing was renumbered during consolidation, only nested one heading level deeper under its chapter.

## Table of Contents

- **[Chapter 1 — Requirements Specification](#chapter-1--requirements-specification-doc-1)** *(doc 1)*
  1. Introduction — 1.1 Purpose · 1.2 Product Vision · 1.3 Definitions & Abbreviations · 1.4 Scope
  2. Actors & User Types
  3. Functional Requirements — 3.1 Onboarding/Auth/Identity · 3.2 Wallets/Virtual Accounts/Collections · 3.3 Transactions/Fees/Payment Links · 3.4 Processor Integration/Reconciliation · 3.5 Administration/Access Control · 3.6 Notifications/Reporting
  4. Non-Functional Requirements
  5. Assumptions & Constraints
  6. MVP Scope & Versioning Approach
- **[Chapter 2 — Technical Architecture & Database Schema](#chapter-2--technical-architecture--database-schema-doc-2)** *(doc 2)*
  1. Technology Stack — 1.1 Architectural Style · 1.2 Kafka vs. RabbitMQ
  2. Module Breakdown — 2.1 Onboarding/Auth/RBAC · 2.2 Payments · 2.3 Supporting Modules
  3. High-Level Architecture
  4. Database Schema Design — 4.1 Schema Contract · 4.2 Core Entities
  5. Entity Relationship Overview
  6. Repository & File Layout
  7. Architecture Decision Records (ADR-1 – ADR-11)
- **[Chapter 3 — Security, Idempotency & Deployment Deep-Dive](#chapter-3--security-idempotency--deployment-deep-dive-doc-3)** *(doc 3)*
  1. Idempotency Handling — 1.1 Key Design · 1.2 Database Layer · 1.3 Concurrency Control · 1.4 Reconciliation Job
  2. Security Best Practices — 2.1 Auth/Session · 2.2 RBAC · 2.3 Transport/Data Security · 2.4 Application-Layer Hardening · 2.5 Third-Party API Key Auth & IP Whitelisting
  3. NIBSS Integration Considerations
  4. Git Workflow & Branching Strategy
  5. Deployment Strategy
  6. Withstanding Heavy Load
  7. Logging & Observability
- **[Chapter 4 — Contribution Guide, Naming Conventions & Process Flows](#chapter-4--contribution-guide-naming-conventions--process-flows-doc-4)** *(doc 4)*
  - Part A — Contribution Guide
  - Part B — Naming Conventions
  - Part C — Process Flows (C.1 – C.8)

---

## Chapter 1 — Requirements Specification (doc 1)

### 1. Introduction

Nawill Pay is an independent payment collection and processing platform designed to serve as a payment integrator for businesses and individuals across Nigeria, with a growth path into other Sub-Saharan African markets. It sits between merchants/collectors and Nigeria's payment rails (NIBSS, commercial and microfinance banks, licensed Payment Solution Service Providers) to receive money on behalf of users, record it against virtual accounts, and settle or disburse it according to configured rules.

A defining design constraint is Nigeria's uneven network infrastructure. Nawill Pay's local communities and merchants frequently operate on poor or intermittent connectivity, so the platform is deliberately designed with resilience, graceful degradation, and offline-to-online fallback strategies for sensitive operations, rather than assuming always-on connectivity typical of platforms designed for developed markets.

#### 1.1 Purpose

This document defines the functional and non-functional requirements for the Nawill Pay MVP and establishes a foundation for subsequent iterations, including multi-currency support for other African markets.

#### 1.2 Product Vision

To become a leading, locally-resilient payment collector and processor for Nigeria's underserved communities and SMEs, later expanding across Sub-Saharan Africa, while maintaining bank-grade security and regulatory alignment with CBN guidelines.

#### 1.3 Definitions & Abbreviations

|  |  |
|----|----|
| **Term** | **Definition** |
| NIBSS | Nigeria Inter-Bank Settlement System — the national interbank settlement infrastructure used for real-time transfers (NIP) between Nigerian banks. |
| PSSP | Payment Solution Service Provider — a CBN-licensed entity permitted to provide payment processing/switching services (e.g. Paystack, Interswitch, Flutterwave). |
| KYC / KYB | Know Your Customer / Know Your Business — identity and business verification processes required for regulatory compliance. |
| RBAC | Role-Based Access Control — permission model restricting system actions by assigned role. |
| Virtual Account | A unique, non-globally-shared account number issued to a user/business for the purpose of receiving collections, mapped internally to a ledger balance. |
| Settlement Account | The linked bank account (commercial/microfinance bank) to which a virtual account's funds are settled/disbursed. |
| Collection Account | Nawill Pay's single pooled bank account, held with a partner commercial or microfinance bank, where actual NIBSS deposits land before internal ledger allocation. Settable up only by a platform admin (FR-Settle-2); its ledger balance mirrors every virtual-account credit and settlement debit platform-wide. Distinct from a business's own Settlement Account. |
| Public Key | The non-secret identifier half of a business's API credential pair (FR-9), sent as the `X-Public-Key` header on every third-party request; safe to log or embed in client code. |
| Secret Key | The private half of a business's API credential pair (FR-9), used to HMAC-sign requests; shown once at generation or regeneration, stored only in encrypted form thereafter, never re-displayed. |
| Dynamic Virtual Account | A per-transaction, expiring bank-style account number minted against a business's permanent Virtual Account (FR-DynAcct-1), used to reconcile one specific inbound bank transfer unambiguously without relying on the payer's free-text narration. |
| Idempotency Key | A client- or server-generated unique key attached to a transaction request to guarantee it is processed at most once. |

#### 1.4 Scope

- In scope (MVP): user & business onboarding, KYC/2FA, virtual account issuance, inbound collections, transaction fees, payment splits/settlement, payment links, payment processor integrations (Paystack, Interswitch, etc.), webhooks (inbound/outbound), reconciliation, admin & RBAC, notifications, reporting, audit logging.

- Out of scope (MVP, planned for later phases): multi-currency wallets and FX settlement, card issuing, lending/credit products, agency banking, full offline transaction queuing beyond critical-path fallback.

- Currency: MVP defaults to Naira (NGN) only, but the schema and configuration layer must be designed so that additional African currencies (e.g. GHS, KES, XOF) can be added without structural rework.

### 2. Actors & User Types

The platform recognizes the following user types, defined at the schema level via a userType enumeration:

|  |  |
|----|----|
| **User Type** | **Description** |
| USER | An individual end user who has signed up to collect payments, hold a virtual account, and transact. |
| ADMIN | An internal or business-created privileged user (e.g. account officer) with configurable, scoped permissions defined via RBAC roles. |
| SUPERADMIN | A seeded, internal Nawill Pay staff role with unrestricted access by default across the platform. Not self-serve creatable. |
| PSSP | A licensed Payment Solution Service Provider / partner integrator onboarded to consume the platform's APIs (signup, API keys, webhooks) as a technical partner rather than an end merchant. |
| BUSINESS | A registered business entity (as opposed to an individual), able to onboard staff (admins/account officers) under it. |

A business or non-super-admin client should be able to create scoped internal roles (e.g. Admin, Account Officer) under its own account, each with permissions limited to that business's data — never platform-wide access.

### 3. Functional Requirements

#### 3.1 Onboarding, Authentication & Identity

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-1** | Users shall be able to sign up as an individual or as a business. On successful signup, the platform shall automatically provision a virtual account for that individual/business for the purpose of receiving collections. |
| **FR-8** | Onboarding shall include a KYC step (BVN/NIN verification for individuals, CAC/RC number verification for businesses in the Nigerian context). Two-factor authentication (2FA) shall be configurable per account (SMS OTP, authenticator app, or email OTP). |
| **FR-8a** | Transfers/collections above a configurable threshold (default: ₦50,000) shall trigger an additional, enhanced KYC/tier-upgrade process before the transaction is permitted, in line with CBN tiered-KYC guidance. |
| **FR-9** | Onboarded clients (businesses/PSSPs) shall be able to set up as partners: configure their account, or receive invites to sign up, and generate API credentials — secret key and public key pairs — plus configure a webhook URL and register for the webhook API. |
| **FR-ApiKey-1** | Realizing FR-9's key-pair requirement: every business may hold exactly one active public/secret key pair at a time. The secret is shown once, at generation or regeneration, and never again; regenerating immediately revokes the prior pair (no dual-active-pair window). The pair authenticates third-party requests to collect (credit) and withdraw (settle) against the business's own virtual account via HMAC-SHA256 request signing — not bearer-token/session auth. |
| **FR-Security-3** | Extending FR-9 and NFR-7: a business may restrict its API key pair to a caller-managed whitelist of IP addresses/CIDR ranges; requests from an unlisted source are rejected. An empty whitelist is unrestricted by default, so a newly generated key works immediately without forcing whitelist setup before first use. |
| **FR-Auth-1** | Users shall be able to onboard and authenticate with minimal friction ("without stress") and, once verified, be able to fund their wallet and transfer money within a configurable minimum transfer range. |
| **FR-Auth-2** | Extending FR-Auth-1: money shall move only after the sender confirms a 4-digit transaction PIN. A user sets the PIN once (entered twice, confirmed with their current password); changing it also requires the current PIN, and a transfer attempted before a PIN is set is refused. The PIN is stored hashed exactly like the password — never in clear, never logged. After 3 consecutive wrong PINs (configurable) the user's transfer capability is locked for 30 minutes (configurable); login itself is not affected. Setting the PIN, each failed attempt and each lockout are written to the security audit log. |

#### 3.2 Wallets, Virtual Accounts & Collections

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-10** | The platform functions as a revenue/payment collector: money received on behalf of a user is recorded against that user's virtual account balance internally, while the actual NIBSS deposit is received into Nawill Pay's pooled collection account, held in partnership with a commercial or microfinance bank. Internal ledger balances must always reconcile against the real pooled balance. |
| **FR-2** | Users shall be able to configure settlement splits: an inbound payment can be split by percentage across multiple recipients/settlement accounts, with amounts automatically distributed and disbursed to the due settlement account(s) per the configured split. |
| **FR-Settle-1** | Realizing FR-2: a business attaches one or more of its own real bank accounts to its virtual account as settlement accounts, each carrying a split percentage; the platform rejects any configuration whose percentages would sum above 100% for one virtual account. If configured percentages sum to less than 100%, only that proportional share settles out — the remainder stays in the virtual account, it is not swept. Settlement may be triggered manually on request, or automatically in real time immediately after each successful collection if the business enables an auto-settle toggle. |
| **FR-Settle-2** | Nawill Pay holds a single pooled Collection Account (see Definitions), set up only by a platform admin. Its ledger balance mirrors every virtual-account credit and settlement debit platform-wide, so the sum-of-virtual-account-balances-reconciles-to-the-pooled-balance invariant (doc 3 §3) is mechanically maintained, not just conceptual. |
| **FR-DynAcct-1** | A business may mint a dynamic (temporary) virtual account: a one-time, expiring, CBN-style 10-digit account number tied to one specific payment intent, used to reconcile a single inbound bank transfer unambiguously without relying on the payer's free-text narration. It accepts at most one deposit and expires automatically after a configurable window (default 30 minutes) — expiry is enforced at lookup/deposit time, not by a background sweep. |
| **FR-12** | The minimum transferable/collectable amount shall default to ₦50 and shall be configurable per environment/business. |
| **FR-13** | The default platform currency is Naira (NGN). The currency field must be modelled to support additional currencies in future phases without a schema rewrite. |

#### 3.3 Transactions, Fees & Payment Links

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-11** | Every transaction processed on the platform shall attract a configurable transaction charge based on amount tiers. Default tiers: \< ₦2,000 → ₦50; ₦2,000–₦15,000 → ₦100; ₦15,000–₦50,000 → ₦200; \> ₦50,000 → ₦300. Tier boundaries and amounts must be admin-configurable. |
| **FR-14** | The system shall be able to generate payment links, including permanent payment links and temporary (time-bound / single-use) payment links, both with configurable expiration. |
| **FR-Link-2** | Realizing FR-14: every payment link is addressable via a short, shareable URL generated from the link itself — the short code doubles as the URL, so no separate general-purpose URL-shortening capability is provided, since nothing else in the platform needs one. Permanent links never expire and reject an explicit expiry at creation; temporary links default to a 24-hour window if none is supplied. A single-use link accepts exactly one successful payment and then permanently refuses further payments regardless of remaining time. |
| **FR-Txn-1** | Every transaction shall be idempotency-protected via a client-supplied or system-generated idempotency key, guaranteeing a given payment intent is processed and charged at most once even under retries. |
| **FR-Txn-2** | Every transaction shall carry a transaction type (Credit / Debit), a transaction status (see §5, Schema Design), a session ID, a linked payment processor reference, and a recipient account reference. |

#### 3.4 Payment Processor Integration & Reconciliation

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-6** | Payment processors (e.g. Paystack, Interswitch, Flutterwave) shall be onboarded and configured on the platform as first-class entities. Activating/configuring a processor shall be restricted to a privileged "Supply Admin" role only. |
| **FR-7** | The platform shall receive and process inbound webhooks from payment processors and perform reconciliation against internal transaction records. |
| **FR-Recon-1** | A scheduled reconciliation job shall run every 6 hours to reconcile transactions against processor and NIBSS records, flagging mismatches for admin review. |
| **FR-Recon-2** | The platform shall support outbound webhooks (notifying integrating partners of transaction events) as well as inbound webhooks (receiving processor events), and shall support requery of a transaction's true status directly from the processor when a webhook is missed or delayed. |

#### 3.5 Administration & Access Control

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-3** | There shall be an admin portal/page where authorized users can view analytics such as transactions, business performance, and settlement summaries, filterable by business, date range, and transaction status. |
| **FR-4** | There shall be a well-defined RBAC model governing the admin portal. Access to any admin capability must be gated by an explicit permission, not an implicit role check. |
| **FR-5** | SUPERADMIN shall have unrestricted, seeded access by default and shall not be a self-service-creatable role. |
| **FR-5a** | A business/client account (non-superadmin) shall be able to create scoped admin/staff roles (e.g. Admin, Account Officer) under itself, each with permissions limited to its own business/account scope — never platform-wide. |
| **FR-Admin-1** | Realizing FR-3 for platform staff — business directory: a user holding `platform-businesses:read` shall be able to list every business on the platform (paginated), search by business name or CAC number, filter by KYC status, and see headline counts of businesses per KYC status. Opening a business shows everything needed for a KYC decision: the submitted details, the CAC registry lookup result, the owner's identity check with BVN/NIN masked, and the uploaded KYC documents. |
| **FR-Admin-2** | KYC review: a user holding `platform-kyc:review` shall be able to download a business's KYC documents and approve or reject a business whose KYC is awaiting review. A rejection requires a reason (max 512 characters), which is shown back to the business so it can fix and resubmit. Each decision records who made it and when; deciding on a business that is not awaiting review is refused; resubmission clears the previous decision. Every approval and rejection is written to the security audit log. |
| **FR-Admin-3** | Audit log: a user holding `platform-audit:read` shall be able to search the security audit log across all businesses (paginated), filtered by event type, outcome, user, business, email and date range. |
| **FR-Admin-4** | Platform access: the admin capabilities above are served by a separate admin console, open only to platform user types (ADMIN, SUPERADMIN) — `userType` decides, not the presence of a business. The three `platform-*` permissions are granted to no seeded role: SUPERADMIN passes every check (FR-5), and ADMIN staff get them only through an explicitly assigned role (FR-4). SUPERADMIN additionally sees transactions across all businesses, filterable by business; every other account sees only its own. |

#### 3.6 Notifications & Reporting

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-Notif-1** | The platform shall notify users of key events — successful/failed transactions, debits, credits, and profile/security updates — via configurable channels (email, SMS, push, in-app). |
| **FR-Report-1** | Businesses and admins shall be able to generate reports (transaction statements, settlement reports, reconciliation reports) for a given date range, exportable in common formats (CSV/PDF). |

### 4. Non-Functional Requirements

|  |  |  |
|----|----|----|
| **ID** | **Category** | **Requirement** |
| NFR-1 | **Reliability** | The application shall not crash under any load or input circumstance; all unhandled failure paths must degrade gracefully rather than terminate the process. |
| NFR-2 | **Network Resilience** | The system shall be resilient against bad network conditions. Critical user-facing components shall include an offline-to-online fallback strategy (e.g. local request queuing with sync-on-reconnect, optimistic UI states) so that sensitive flows can partially function despite poor or no connectivity. |
| NFR-3 | **Observability / Logging** | There shall be comprehensive, structured logging across the platform (see §7 of the companion Security & Deployment Deep-Dive). |
| NFR-4 | **Environments** | There shall be a well-defined environment setup: isolated Development, Staging, and Production environments with independent configuration, credentials, and data. |
| NFR-5 | **CI/CD** | There shall be a clear, automated CI/CD pipeline in which key components of the system are testable in isolation (unit, integration, contract tests) before promotion. |
| NFR-6 | **Idempotency** | Idempotency on transactions shall be properly and consistently handled at the API and persistence layer (see companion deep-dive). |
| NFR-7 | **Security** | The platform shall be well secured via authentication, RBAC, SSL/TLS in transit, encryption at rest, and IP whitelisting for sensitive/administrative and partner-facing endpoints. |
| NFR-8 | **Notifications** | There shall be a proper notification system to alert users of key events — transactions, debits, and account/security updates. |
| NFR-9 | **Versioning** | There shall be a well-documented, well-defined API and release versioning system (see Contribution & Naming Conventions document). |
| NFR-10 | **Scale & Performance** | The system shall be able to withstand heavy load and comfortably process on the order of ₦1 billion in transaction value per day, sustaining roughly 1,000 requests per second at peak (see Assumption note below) with acceptable p95 latency. |
| NFR-11 | **Request Authenticity** | Every third-party request authenticated via a business API key pair (FR-ApiKey-1) shall be HMAC-signed and verified server-side, with a bounded clock-skew window to prevent replay, and shall be rate-limited per key and per source IP. This is distinct from and additional to NFR-7: NFR-7 protects confidentiality (TLS, encryption at rest) and IP whitelisting; NFR-11 protects the authenticity/integrity of who actually sent a given request. |

*Assumption note (NFR-10): the source specification states "1000 requests per millisecond," which is not realistic for a payments platform of this scale and is assumed to be a slip for 1000 requests per second (a very healthy peak-throughput target for a national-scale collection platform). This should be confirmed with the product owner and updated once confirmed.*

### 5. Assumptions & Constraints

- Settlement to external bank accounts (NIBSS NIP) is executed through a partner commercial or microfinance bank; Nawill Pay does not hold a direct CBN settlement license at MVP stage.

- The MVP targets the Nigerian market only; the data model (countries, states, local government hierarchy, currency) is intentionally generalized so Ghana, Kenya, Francophone West Africa (XOF) and other Sub-Saharan markets can be added later.

- Nigeria's administrative hierarchy is modelled at three levels: Level 1 — State, Level 2 — Local Government Area (LGA), Level 3 — Ward, to support address capture and future geo-tiered reporting.

- KYC integrations (BVN/NIN verification, CAC lookup) are assumed to be via third-party identity verification providers operating in Nigeria.

- All monetary values are stored in the lowest currency unit (kobo) as integers to avoid floating-point rounding errors.

- All database entities carry a default status of Active/Inactive, and soft-delete (deletedAt) rather than hard-delete, to preserve audit trail integrity.

### 6. MVP Scope & Versioning Approach

Per direction to "design for MVP and show the different versions," requirements are tagged by target release below. This lets engineering sequence delivery while keeping the full target architecture visible.

|  |  |  |
|----|----|----|
| **Release** | **Theme** | **Representative Requirements** |
| v0.1 — MVP | Core collection loop | FR-1, FR-8, FR-9, FR-10, FR-11, FR-12, FR-13, FR-3, FR-4, FR-5, NFR-1 to NFR-7, NFR-9 |
| v0.1.x — Settlement, collection account, API keys, payment links & dynamic accounts | FR-2 and FR-14 realized (split settlement, addressable payment links); Collection Account, API keys and dynamic accounts added | FR-Settle-1, FR-Settle-2, FR-ApiKey-1, FR-Security-3, FR-Link-2, FR-DynAcct-1, NFR-11 — implemented against a sandboxed money-movement layer (see doc 3 §2.5, doc 2 §7 ADR-6); real NIBSS disbursement remains v0.2 scope alongside FR-6/FR-7 |
| v0.2 | Splits, links & partners | FR-6, FR-7, FR-Recon-1, FR-Recon-2, FR-5a |
| v0.3 | Scale & resilience hardening | NFR-2 (offline fallback), NFR-10 (load), FR-Notif-1, FR-Report-1 |
| v1.0 | Multi-currency / Pan-African | Additional currencies, FX handling, country/state expansion beyond Nigeria |

---

## Chapter 2 — Technical Architecture & Database Schema (doc 2)

### 1. Technology Stack

This document covers the API layer only (backend services). Client applications (web/mobile) are out of scope here.

|  |  |
|----|----|
| **Layer** | **Choice & Rationale** |
| Language / Runtime | Java 17 (LTS) — required baseline per spec; enables records, pattern matching, and virtual-thread readiness for future migration to Java 21. |
| Framework | Spring Boot 3.x, built on Spring Framework 6 (Jakarta EE namespace). |
| Security | Spring Security 6 — authentication, method-level and endpoint-level RBAC, OAuth2 Resource Server for JWT validation. |
| Persistence | PostgreSQL 15+ as system of record. Spring Data JPA / Hibernate for ORM, with hand-tuned native queries for reporting/aggregation paths. |
| Caching | Redis — session/token caching, idempotency-key locks, rate-limiting counters, hot-read caches (fee tiers, processor config, FX rates). |
| Messaging / Async Jobs | Apache Kafka (recommended) for transaction events, webhook delivery, and audit-log streaming — see §2 for the Kafka vs. RabbitMQ comparison. |
| API Documentation | springdoc-openapi — Swagger/OpenAPI docs auto-generated from controller annotations; no hand-maintained spec files. |
| Build Tool | Maven (multi-module) — one module per bounded context, see §3. |
| Containerization | Docker images per service/module; orchestrated via Kubernetes in staging/production. |

#### 1.1 Architectural Style

The platform is built as a well-structured modular monolith rather than microservices at MVP stage. Each business capability is isolated into its own Maven module/package with a clear public API (interfaces/DTOs) and no cross-module reach into another module's internal repositories or entities. This keeps operational overhead low for a small team while preserving clean seams that allow individual modules (e.g. Payments) to be extracted into standalone services later, once load or team size justifies it.

Design adheres to SOLID and DRY principles throughout: single-responsibility services, dependency-inverted integrations (payment processors implemented behind a common interface), and shared cross-cutting concerns (logging, idempotency, exception handling) centralized in a common/core module rather than duplicated.

#### 1.2 Kafka vs. RabbitMQ — Recommendation

|  |  |  |
|----|----|----|
| **Consideration** | **Kafka** | **RabbitMQ** |
| Best fit for | High-throughput event streams, durable replay, audit/event-sourcing | Task queues, RPC-style jobs, complex routing |
| Throughput at scale | Very high; built for the ₦1bn/day, ~1,000 req/s target | Good, but generally lower ceiling than Kafka at this volume |
| Message replay / audit | Native (log retention) — valuable for reconciliation and dispute resolution | Not native; requires additional tooling |
| Operational complexity | Higher (ZooKeeper/KRaft, partitions, consumer groups) | Lower; simpler to operate for a small team |
| Delivery semantics needed | At-least-once with idempotent consumers — matches our idempotency-key design | At-least-once achievable, less natural for replay-based reconciliation |

*Recommendation: use Kafka for the core transaction-event backbone (payment.received, payment.settled, webhook.inbound, webhook.outbound, reconciliation.mismatch) because replay-ability directly supports reconciliation and dispute investigation, and its throughput ceiling comfortably covers the stated NFR-10 target. Use a lighter-weight mechanism — Spring's own @Async / a scheduled-task table, or RabbitMQ if a second broker is truly warranted — for low-volume, latency-insensitive background jobs (e.g. sending a single onboarding email). Avoid running two brokers unless a concrete requirement (e.g. RPC-style synchronous job response) emerges; Kafka alone is sufficient for MVP and keeps operational surface area smaller.*

### 2. Module Breakdown

#### 2.1 Onboarding, Authentication & RBAC Module

Covers all aspects of onboarding and authentication for every user type — individual USER, ADMIN, SUPERADMIN, PSSP/partner, and organizations/businesses. Responsibilities:

- Signup / login (individual and business), password & credential management

- 2FA enrolment and verification (configurable per account)

- KYC/KYB orchestration (BVN/NIN, CAC lookups) and tiered-KYC upgrade triggers

- Role & permission management (RBAC): role CRUD, permission CRUD, role-assignment, scoped-to-business role creation

- API key / secret key issuance and rotation for PSSP/partner accounts, webhook URL registration — **implemented** (FR-ApiKey-1): one active pair per business, HMAC-SHA256 request signing on the third-party collect/withdraw surface, per-key IP whitelisting (FR-Security-3). See §7 ADR-1–ADR-3.

- JWT issuance/validation, session and refresh-token handling

#### 2.2 Payments Module

Handles all transaction inflows and outflows. Responsibilities:

- Virtual account provisioning and balance/ledger management

- Payment processor configuration and setup (Paystack, Interswitch, Flutterwave, etc.) — restricted to Supply Admin role

- Transaction initiation, fee calculation (configurable tiers), idempotency enforcement

- Settlement/split-payment engine — percentage-based distribution to settlement accounts — **implemented** (FR-Settle-1): manual and real-time auto-settle, backed by a sandboxed disbursement gateway (§7 ADR-6). The pooled Collection Account (FR-Settle-2) also lives here.

- Payment link generation (permanent and temporary/expiring) — **implemented** (FR-Link-2), plus dynamic/temporary virtual accounts (FR-DynAcct-1) as the transfer-based sibling mechanism

- Webhook handling — inbound (from processors) and outbound (to partners), plus manual requery

- Reconciliation job (scheduled every 6 hours) against processor and NIBSS records

#### 2.3 Supporting Modules (recommended additions to standard practice)

- Notification Module — templated email/SMS/push dispatch for transactional and security events, decoupled via the Kafka event backbone.

- Reporting & Analytics Module — read-optimized query layer (materialized views or a reporting replica) backing the admin portal's analytics and exportable statements.

- Audit & Activity Log Module — central write path for UserChangeLog/activity logs, request-id correlation, and structured audit trails (see companion Security & Deployment Deep-Dive).

- Reference-Data Module — countries, states/LGAs/wards, banks, currencies — shared, cached, rarely-written lookup data consumed by other modules.

### 3. High-Level Architecture

Represented as a layered flow rather than a graphic, for portability across viewers:

|  |
|----|
| **Client Apps (Web / Mobile / Partner Integrations)** |
| ↓ HTTPS / TLS 1.2+ |
| **API Gateway — TLS termination, IP whitelisting, rate limiting, request-id injection** |
| ↓ |
| **Spring Boot Modular Monolith — Onboarding/Auth/RBAC · Payments · Notifications · Reporting · Audit · Reference-Data** |
| ↓ ↓ ↓ |
| **PostgreSQL (system of record) Redis (cache, idempotency locks, rate limits) Kafka (transaction & webhook events)** |
| ↓ |
| **External Integrations — Payment Processors (Paystack/Interswitch/etc.) · NIBSS (via partner bank) · Commercial/Microfinance Settlement Bank · SMS/Email Providers** |

### 4. Database Schema Design

#### 4.1 Schema Design Contract

Every schema/entity in the system inherits from a common abstract base (e.g. an AbstractAuditableEntity in JPA, or a shared BaseEntity interface) that establishes what fields and behaviours are guaranteed across the platform. This is the "schema contract" referenced in the design notes — it defines what every table can, and cannot, expose at the schema level.

Standard base fields inherited by every entity, unless explicitly noted:

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key, never a sequential integer, to avoid enumeration and to support future multi-region sharding. |
| status | Enum: ACTIVE / INACTIVE | Every table has a default status of Active or Inactive (soft state toggle, distinct from soft-delete). |
| createdAt | Timestamp | Auto-set on insert (DB default now()). |
| updatedAt | Timestamp | Auto-updated on every modification. |
| createdBy | UUID (nullable) | References the acting user, or null/self for system- or self-generated records. |
| deletedAt | Timestamp (nullable) | Soft-delete marker; records are never hard-deleted. |

#### 4.2 Core Entities

##### Users

Central identity table for all user types.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| firstName | String | Required |
| middleName | String | Optional |
| lastName | String | Required |
| email | String | Unique, required |
| phoneNo | String | Unique, required |
| isVerified | Boolean | True once KYC + email/phone verification complete |
| dob | Date | Optional |
| userType | Enum | USER / ADMIN / SUPERADMIN / PSSP |
| addressId | UUID (FK → Address) | Optional |
| businessId | UUID (FK → Business) | Optional — null for pure individuals |
| createdBy | UUID (FK → Users, self-referencing) | Optional — self if self-registered |
| status, createdAt, updatedAt, deletedAt | — | Inherited base fields |

##### Business

Registered business entities that can own users, roles, and virtual accounts.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| name / registeredName | String | Required |
| cacNumber | String | Nigeria business registration number; optional pre-KYB |
| addressId | UUID (FK → Address) | Optional |
| ownerId | UUID (FK → Users) | The primary account owner |
| status, createdAt, updatedAt, deletedAt | — | Inherited base fields |

##### Roles / Permissions

RBAC model. A Role is a named, business-scoped or platform-scoped bundle of Permissions.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| Role.id / name / businessId (nullable for platform roles) | — | businessId null ⇒ platform-level role (e.g. SUPERADMIN) |
| Permission.id / name / resource / action | — | Fine-grained, e.g. resource=transactions, action=read\|write |
| RolePermission (join) | — | Many-to-many between Role and Permission |
| UserRole (join) | — | Many-to-many between Users and Role |

##### Countries

Reference data for multi-country expansion.

|                      |                   |                                  |
|----------------------|-------------------|----------------------------------|
| **Field**            | **Type**          | **Notes**                        |
| id                   | UUID              | Primary key                      |
| name                 | String            |                                  |
| code / iso3          | String            | ISO country codes                |
| flag                 | String (URL)      |                                  |
| currency             | String (ISO 4217) | Default currency for the country |
| createdAt, updatedAt | —                 |                                  |

##### States / AdminDivisions

Nigeria's administrative hierarchy is modelled 3 levels deep (state, LGA, ward); other countries reuse the same table with their own hierarchy depth.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| countryId | UUID (FK → Countries) |  |
| name | String |  |
| level | Integer | 1 = State, 2 = Local Government Area, 3 = Ward |
| parentId | UUID (FK → self, nullable) | Self-referencing hierarchy |

##### Banks

Reference data for settlement bank selection.

|           |          |               |
|-----------|----------|---------------|
| **Field** | **Type** | **Notes**     |
| id        | UUID     | Primary key   |
| name      | String   |               |
| code      | String   | CBN bank code |
| createdAt | —        |               |

##### Address

Physical address, linkable to a User or Business.

|                      |                             |                          |
|----------------------|-----------------------------|--------------------------|
| **Field**            | **Type**                    | **Notes**                |
| id                   | UUID                        | Primary key              |
| countryId            | UUID (FK → Countries)       |                          |
| stateId              | UUID (FK → States)          |                          |
| localGovernmentId    | UUID (FK → States, level=2) |                          |
| streetName / no      | String                      |                          |
| description          | String                      | Optional freeform        |
| latitude / longitude | Decimal                     | Optional geo-coordinates |
| createdAt, updatedAt | —                           |                          |

##### VirtualAccount

The account a user/business receives collections into. Automatically provisioned at signup (FR-1).

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| accountNumber | String | Unique within Nawill Pay (not a globally-routable NUBAN by default) |
| userId | UUID (FK → Users, nullable) |  |
| businessId | UUID (FK → Business, nullable) | One of userId/businessId is set |
| currency | String (ISO 4217) | Defaults to NGN |
| balance | BigInteger (minor units) | Internal ledger balance, kobo-denominated |
| meta | JSON | Extensible metadata |
| autoSettle | Boolean | Default false (FR-Settle-1) — when true, settlement fires in real time immediately after each successful CREDIT, instead of only on manual request |
| status, createdAt, updatedAt | — | Inherited base fields |

##### SettlementAccount — implemented (FR-Settle-1)

The bank account a virtual account's funds settle to.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| virtualAccountId | UUID (FK → VirtualAccount) |  |
| bankAccountId | UUID (FK → BankAccount) |  |
| splitPercentage | Decimal(5,2) | Supports FR-2 percentage-based splits when multiple settlement accounts exist per virtual account. `SettlementAccountService` rejects a create that would push the sum of active percentages for one virtual account above 100 — validated while holding a row lock on the parent VirtualAccount, so two concurrent creates can never both pass. |
| createdAt, updatedAt | — |  |

##### BankAccount — implemented (FR-Settle-1)

A business's own registered real bank account, later attached to a SettlementAccount. `bankId` is a bare column, validated against reference-data's Banks table at creation time but never a JPA relation across modules (the established cross-module convention).

|             |                   |                                 |
|-------------|-------------------|---------------------------------|
| **Field**   | **Type**          | **Notes**                       |
| id          | UUID              | Primary key                     |
| bankId      | UUID (FK → Banks) |                                 |
| accountNumber | String          |                                 |
| accountName | String            |                                 |
| businessId  | UUID (FK → Business) | Owner — bank accounts are a business concept, not an individual-user one |

##### Settlement — implemented (FR-Settle-1)

One disbursement out of a virtual account to one of its settlement accounts, mirroring Transaction's state-machine shape.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| virtualAccountId | UUID (FK → VirtualAccount) |  |
| settlementAccountId | UUID (FK → SettlementAccount) |  |
| amount | BigInteger (minor units) | This split's share — see doc 2 §7 ADR-7 for the rounding rule when percentages don't divide evenly |
| settlementStatus | Enum | PENDING / PROCESSING / COMPLETED / FAILED |
| reference | String (nullable) | Gateway-assigned reference on success |
| idempotencyKey | String | Unique constraint; per-split key derived from the request's Idempotency-Key |
| version | Long | Optimistic lock on the status state machine, mirroring Transaction |

##### CollectionAccount — implemented (FR-Settle-2)

Nawill Pay's single pooled bank account (see doc 1 §1.3 Definitions). Deliberately not named SettlementAccount, which is reserved for a business's own external bank account.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| bankId | UUID (FK → Banks) |  |
| accountNumber | String |  |
| accountName | String |  |
| balance | BigInteger (minor units) | Mirrors every VirtualAccount credit and settlement debit platform-wide (ADR-5) — makes the doc 3 §3 reconciliation invariant mechanically checkable, not just conceptual |

Enforced as a singleton at the database level via a partial unique index on `status = 'ACTIVE'` — at most one row can ever be active, regardless of application-level races. `collection-account:manage` is the gating permission, and it is deliberately assigned to no seeded role — only SUPERADMIN's hard-coded bypass in `PermissionChecker` can create or view it.

##### ApiKeyCredential / ApiKeyIpWhitelist — implemented (FR-ApiKey-1, FR-Security-3)

A business's public/secret key pair, and its optional IP whitelist.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| ApiKeyCredential.id / businessId / publicKey / secretKeyEncrypted | — | One active pair per business (partial unique index on businessId where status='ACTIVE'). publicKey is `pk_live_…`, unique. secretKeyEncrypted is AES-256-GCM ciphertext — reversible, never a one-way hash, since verifying an HMAC signature requires recovering the plaintext secret (doc 2 §7 ADR-6). |
| ApiKeyIpWhitelist.id / apiKeyId (FK) / cidr | — | Zero or more CIDR entries per key. An empty whitelist means unrestricted (ADR-8). |

##### PaymentLink — implemented (FR-14, FR-Link-2)

A shareable, payer-facing payment intent.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| businessId | UUID |  |
| virtualAccountId | UUID (FK → VirtualAccount) |  |
| shortCode | String | Unique, 8-char base62 — this IS the short URL |
| amount | BigInteger (minor units, nullable) | Null means the payer enters their own amount at pay time |
| currency | String (ISO 4217) |  |
| linkType | Enum | PERMANENT / TEMPORARY |
| expiresAt | Timestamp (nullable) | Required-absent for PERMANENT, defaults to +24h for TEMPORARY if not supplied |
| singleUse | Boolean |  |
| linkStatus | Enum | ACTIVE / EXPIRED / REDEEMED / REVOKED |

##### DynamicVirtualAccount — implemented (FR-DynAcct-1)

A per-transaction, expiring bank-style account number — the transfer-based sibling of a payment link, solving bank-transfer reconciliation without relying on the payer's narration. Deposits land on the parent VirtualAccount; this is a routing/reconciliation shell around it, not a second ledger.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| businessId | UUID |  |
| virtualAccountId | UUID (FK → VirtualAccount) | The real, permanent account the deposit actually credits |
| accountNumber | String | Unique, same "9" + 9-digit CBN-NUBAN-style generation as VirtualAccount (shared `AccountNumberGenerator`) |
| expectedAmount | BigInteger (minor units, nullable) |  |
| expiresAt | Timestamp | Default +30 minutes if not supplied at creation |
| dynamicAccountStatus | Enum | ACTIVE / EXPIRED / PAID / REVOKED |
| reference | String (nullable) | Business-supplied correlation id (e.g. an invoice number) |

Expiry is checked lazily at lookup/deposit time, not via a scheduled sweep (ADR-11).

##### KYC

Identity/business verification records.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| userId | UUID (FK → Users, nullable) |  |
| businessId | UUID (FK → Business, nullable) |  |
| tier | Enum | Tier 1 / 2 / 3 — governs transaction limits |
| bvn / nin / cacNumber | String (encrypted) | Stored encrypted at rest |
| verifiedAt | Timestamp |  |
| status, createdAt, updatedAt | — |  |

##### Avatar

Profile image reference.

|           |                   |                                  |
|-----------|-------------------|----------------------------------|
| **Field** | **Type**          | **Notes**                        |
| id        | UUID              | Primary key                      |
| userId    | UUID (FK → Users) |                                  |
| userType  | Enum              | Denormalized for quick filtering |
| imageUrl  | String            |                                  |

##### PaymentProcessor

Onboarded processor integrations (FR-6).

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| name | String | e.g. Interswitch, Paystack, Flutterwave |
| createdBy | UUID (FK → Users) | Restricted to Supply Admin role |
| status | Enum | ACTIVE / INACTIVE — governs whether new transactions may route through it |

##### Transactions

The core transaction ledger.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| amount | BigInteger (minor units) |  |
| charge | BigInteger (minor units) | Fee computed per FR-11 tiers |
| idempotencyKey | String | Unique constraint; see Security & Deployment Deep-Dive §1 |
| paymentProcessorId | UUID (FK → PaymentProcessor) |  |
| transactionStatus | Enum | PAID / PENDING / FAILED / PROCESSING / ON_HOLD |
| transactionType | Enum | CREDIT (CR) / DEBIT (DR) |
| sessionId | String | Correlates multi-step payment sessions |
| recipientAccountId | UUID (FK → SettlementAccount / BankAccount) |  |
| status, createdAt, updatedAt | — | Inherited base fields (status distinct from transactionStatus) |

##### UserChangeLog / ActivityLog

Append-only audit trail of user and system activity.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| userId | UUID (FK → Users) |  |
| type | Enum | LOGIN / CREATE / UPDATE / DELETE / TRANSACTION / SECURITY |
| activity | String / JSON | Human-readable description + structured payload |
| ipAddress | String |  |
| createdAt | Timestamp |  |

### 5. Entity Relationship Overview

Relationships are summarized below rather than as a graphical ERD, so the document remains portable; a generated ERD image (e.g. via dbdiagram.io / SchemaSpy from the actual DDL) should be attached as Appendix A once the schema is implemented in code.

|  |  |  |
|----|----|----|
| **Relationship** | **Cardinality** | **Notes** |
| Countries → States | 1 : N | A country has many states/admin-divisions |
| States → States (self) | 1 : N | Self-referencing hierarchy: State → LGA → Ward |
| Address → Countries / States | N : 1 | Each address references one country and one state/LGA |
| Users → Address | N : 1 | Optional |
| Users → Business | N : 1 | A business has many staff users; a user belongs to at most one business |
| Business → Users (owner) | 1 : 1 | Owner reference |
| Users ↔ Role | N : N | Via UserRole join table |
| Role ↔ Permission | N : N | Via RolePermission join table |
| Users / Business → VirtualAccount | 1 : N | A user/business may hold more than one virtual account (e.g. per currency) |
| VirtualAccount → SettlementAccount | 1 : N | Supports split settlement across multiple bank accounts |
| SettlementAccount → BankAccount | N : 1 |  |
| BankAccount → Banks | N : 1 |  |
| Users / Business → KYC | 1 : N | History of KYC tier upgrades retained |
| Users → Avatar | 1 : 1 |  |
| Transactions → PaymentProcessor | N : 1 |  |
| Transactions → SettlementAccount/BankAccount (recipient) | N : 1 |  |
| Transactions → VirtualAccount (implicit via sessionId/recipient) | N : 1 | Every transaction ultimately debits/credits a virtual account balance |
| Users → UserChangeLog | 1 : N | Append-only |
| BankAccount → Business | N : 1 | Owner |
| SettlementAccount → Settlement | 1 : N | Every disbursement out of a virtual account is one Settlement row against one SettlementAccount |
| CollectionAccount | — (singleton) | Not owned by any user/business — one pooled platform-wide row, enforced by a partial unique index |
| Business → ApiKeyCredential | 1 : N | Historically — only one row may be status=ACTIVE at a time; regeneration keeps the prior row as INACTIVE for audit |
| ApiKeyCredential → ApiKeyIpWhitelist | 1 : N |  |
| Business / VirtualAccount → PaymentLink | 1 : N |  |
| Business / VirtualAccount → DynamicVirtualAccount | 1 : N |  |

### 6. Repository & File Layout

A map of where each concept above actually lives in the codebase, kept current as of the settlement/API-key/payment-link/dynamic-account feature set. Paths are relative to the repository root.

```
napayment/
├── common-core/                    Shared kernel — every other module depends on this
│   └── src/main/java/ng/com/nawill/pay/common/
│       ├── entity/                 BaseEntity, EntityStatus (§4.1 schema contract)
│       ├── exception/              ApiException + subclasses (BadRequestException,
│       │                           UnauthorizedException, ForbiddenException,
│       │                           ConflictException, ResourceNotFoundException,
│       │                           AccountLockedException, TooManyRequestsException),
│       │                           GlobalExceptionHandler
│       ├── security/                CurrentUser, CurrentUserResolver, PermissionChecker,
│       │                           ApiKeyAuthenticationToken (§7 ADR-2/ADR-3)
│       ├── crypto/                 EncryptionService (AES-256-GCM), HmacSigner (§7 ADR-1/ADR-6)
│       ├── ratelimit/              RateLimitService (§7 ADR-10)
│       ├── idempotency/            @Idempotent, IdempotencyAspect, IdempotencyService (doc 3 §1)
│       ├── logging/                RequestIdFilter, UserContextMdcFilter, PiiMasker
│       ├── validation/             @StrongPassword password-policy validator
│       └── config/                 LoggingFilterConfig, OpenApiConfig, JpaAuditingConfig
│
├── reference-data/                 Countries, states/LGAs/wards, banks
│   └── src/main/java/ng/com/nawill/pay/referencedata/
│
├── payments/                       Virtual accounts, transactions, settlement, payment
│   │                               links, dynamic accounts, third-party collect/withdraw
│   └── src/main/java/ng/com/nawill/pay/payments/
│       ├── virtualaccount/         VirtualAccount, VirtualAccountProvisioningService,
│       │                           VirtualAccountQueryService (requireSoleVirtualAccountForCaller)
│       ├── transaction/            Transaction, TransactionService (create / createForPaymentIntent)
│       ├── processor/              PaymentProcessorGateway + SandboxPaymentProcessorGateway
│       ├── bankaccount/            BankAccount (a business's own real bank account)
│       ├── settlement/             SettlementAccount, Settlement, SettlementService,
│       │                           SettlementGateway + SandboxSettlementGateway,
│       │                           SettlementController (JWT manual trigger)
│       ├── collectionaccount/      CollectionAccount (the single pooled account, admin-only)
│       ├── thirdparty/             CollectController, WithdrawController (API-key/HMAC-authenticated)
│       ├── paymentlink/            PaymentLink, PaymentLinkService,
│       │                           PaymentLinkController (business-facing) +
│       │                           PaymentLinkPayController (public, permitAll)
│       ├── dynamicaccount/         DynamicVirtualAccount, DynamicVirtualAccountService
│       └── util/                   AccountNumberGenerator (shared CBN-NUBAN-style generator)
│
├── onboarding-auth-rbac/           Signup/login, JWT, RBAC, business, API key issuance
│   └── src/main/java/ng/com/nawill/pay/onboarding/
│       ├── auth/                   AuthController, AuthService, LoginAttemptService,
│       │                           PasswordResetService, ForgotPassword/ResetPassword/
│       │                           ChangePassword request-response types
│       ├── security/               SecurityConfig (two SecurityFilterChain beans, §7 ADR-2),
│       │                           ApiKeyAuthenticationFilter, CachedBodyHttpServletRequest
│       ├── apikey/                 ApiKeyCredential, ApiKeyIpWhitelist, ApiKeyService,
│       │                           ApiKeyController
│       ├── rbac/                   Role, Permission, RolePermission, UserRole,
│       │                           RoleService, PermissionResolutionService
│       ├── business/                Business entity
│       └── user/                   User entity, UserType
│
└── app/                            Composition root
    └── src/main/resources/
        ├── application.yml         Base config incl. nawill.security.* (encryption key, etc.)
        ├── application-dev.yml     Dev-only literal defaults
        └── db/migration/           Flyway migrations, V0001–V0025 as of this feature set
                                     (V0016–V0025 are the settlement/API-key/payment-link/
                                     dynamic-account tables — see the file list in doc 4 §B.3)
```

Doc 1's FR/NFR IDs and doc 2's §4.2 entity names above are the canonical cross-reference — grep either against this tree to find the implementing class.

### 7. Architecture Decision Records

Decisions made while implementing the settlement/collection-account/API-key/payment-link/dynamic-account feature set (FR-Settle-1/2, FR-ApiKey-1, FR-Security-3, FR-Link-2, FR-DynAcct-1, NFR-11), recorded here per-decision with the alternative explicitly considered and rejected.

**ADR-1 — HMAC-SHA256 request signing, not payload encryption or TLS-only.**
Decision: third-party requests are authenticated via `X-Public-Key`/`X-Timestamp`/`X-Signature` headers, signature = `HMAC-SHA256(secretKey, timestamp + "." + rawBody)`.
Rejected: (a) full asymmetric payload encryption — a much heavier integration burden (client-side key generation/PEM handling) for no peer in this space; (b) TLS-only with a bare API key — no per-request integrity guarantee, a leaked key alone would be enough to forge requests.
Rationale: matches FR-9's existing "public key and secret key" wording exactly, is the industry-standard pattern (Paystack/Flutterwave/Stripe), and needs no new dependency — the JDK's `javax.crypto.Mac` is sufficient. Confidentiality remains TLS's job (NFR-7); this adds authenticity/integrity (NFR-11).

**ADR-2 — A second `SecurityFilterChain`, not an extension of the single JWT chain.**
Decision: `SecurityConfig` now declares `@Order(1) apiKeyFilterChain` (matched via `.securityMatcher("/api/v1/collect/**", "/api/v1/withdraw/**")`) alongside the original `@Order(2)` JWT chain.
Rejected: cramming API-key logic into the existing single chain via a conditional filter.
Rationale: the standard Spring Security idiom for mixing auth mechanisms by path; keeps each mechanism's permitAll list and filter wiring independently readable, at the cost of two chains to keep in sync.

**ADR-3 — A filter, not an AOP annotation, for API-key authentication.**
Decision: `ApiKeyAuthenticationFilter` (`OncePerRequestFilter`), not a `@RequireSignedRequest`-style annotation mirroring `@Idempotent`'s AOP pattern.
Rejected: an `@Around`-advice aspect, consistent with `@Idempotent`.
Rationale: authentication must run before Spring MVC dispatch (to populate `SecurityContextHolder` before `@PreAuthorize` evaluates) and needs raw request IP/headers; AOP advice runs after Spring Security and after `@RequestBody` deserialization. It is also mandatory/chain-wide for its two paths, unlike idempotency's per-method opt-in.

**ADR-4 — Real-time auto-settle, not a scheduled batch sweep.**
Decision: `TransactionService` calls `SettlementService.autoSettleIfEnabled(virtualAccount)` immediately after a successful CREDIT, when the toggle is on.
Rejected: a periodic job (mirroring FR-Recon-1's 6-hourly reconciliation shape) sweeping all auto-settle-enabled accounts.
Rationale: instant settlement UX, no new scheduler component. Tradeoff: couples settlement latency to the collection request's own latency — mitigated by `REQUIRES_NEW` (ADR-9) so a slow/failed settlement never blocks or fails the collection response.

**ADR-5 — `CollectionAccount.balance` is ledger-mirrored, not a static record.**
Decision: every CREDIT also credits `CollectionAccount.balance`; every settlement debit also debits it, alongside the `VirtualAccount` mutation, in the same transaction.
Rejected: a purely informational row (bank details only, no balance tracking).
Rationale: makes doc 3 §3's "sum of virtual-account balances reconciles to the pooled balance" invariant mechanically checkable end to end, not just a conceptual claim — verified directly in this session's smoke test (collect 5000 → auto-settle → both VirtualAccount and CollectionAccount balances return to 0). Tradeoff: touches the pre-existing `TransactionService` ledger-effect code.

**ADR-6 — AES-256-GCM app-level symmetric key, not a real KMS.**
Decision: `EncryptionService` encrypts `secretKeyEncrypted` with a single symmetric key from `nawill.security.encryption-key` config.
Rejected: real envelope encryption via a managed KMS (AWS KMS/GCP KMS/Vault), which doc 3 §2.3 already names as the target for PII at rest.
Rationale: explicit, documented stand-in — same "scoped, not real infra" posture as `SandboxPaymentProcessorGateway`. A one-way hash was never an option here: verifying an HMAC signature requires recovering the plaintext secret, so the storage must be reversible.

**ADR-7 — Split-percentage rounding: remainder to the last split.**
Decision: `amountToSettle = amount × totalPercentage ÷ 100` (rounded down); each non-last split gets `amountToSettle × (itsPercentage ÷ totalPercentage)` (rounded down); the *last* split gets whatever remains.
Rejected: rounding every split independently (parts could fail to sum to the whole, leaking or losing a kobo).
Rationale: guarantees the disbursed parts always sum exactly to the settled sub-total.

**ADR-8 — Empty IP whitelist means unrestricted.**
Decision: an `ApiKeyCredential` with zero `ApiKeyIpWhitelist` rows accepts requests from any source IP.
Rejected: deny-by-default until at least one CIDR is registered.
Rationale: a newly generated key must work immediately for first-time integration/testing; forcing whitelist setup before any request succeeds is an onboarding footgun. Businesses that want the stricter posture doc 3 §2.3(b) describes opt in by adding entries.

**ADR-9 — Pessimistic row locking for every balance mutation, with a fixed lock-ordering rule.**
Decision: `VirtualAccountRepository.findByIdForUpdate` and `CollectionAccountRepository.findActiveForUpdate` (`PESSIMISTIC_WRITE`) replace plain `findById` on every ledger-mutating path, including the pre-existing `TransactionService.create()` (a genuine latent-bug fix — `VirtualAccount.balance` had no `@Version` and no lock before this). **Rule, enforced everywhere with no exceptions: VirtualAccount is always locked before CollectionAccount, never the reverse.**
Rejected: optimistic locking with retry-on-conflict (as `Transaction.version` already uses for its own status field).
Rationale: a lost credit/debit is a worse failure mode than a request waiting briefly for a row lock; the fixed ordering makes deadlocking between concurrent settlements structurally impossible rather than merely unlikely. `SettlementService.settle()` also runs `@Transactional(propagation = REQUIRES_NEW)`: called from inside the same transaction as the triggering credit, an uncaught exception in a `REQUIRED`-propagation method would mark the *shared* transaction rollback-only at the Spring proxy boundary even if the caller catches it — `REQUIRES_NEW` means a failed auto-settle rolls back only itself, and the collection still commits (see doc 3's Concurrency Control section).

**ADR-10 — Redis fixed-window rate limiting, not a true token-bucket; scoped to the API-key surface only.**
Decision: `RateLimitService` does `INCR` + `EXPIRE`-on-first-hit (the same shape as `LoginAttemptService`'s attempt counter), applied per-IP and per-API-key inside `ApiKeyAuthenticationFilter` only.
Rejected: a true token-bucket (doc 3 §2.4's original phrasing) requiring a Lua script for atomic refill; applying rate limiting to every endpoint (JWT dashboard traffic, signup/login).
Rationale: a fixed window is materially simpler to implement correctly and sufficient for this MVP's actual traffic. Scope is deliberately narrow — login already has its own purpose-built lockout mechanism, and broadening rate limiting further is future work, not built here. Like idempotency and lockout, it fails open on Redis unavailability (NFR-2 posture).

**ADR-11 — Dynamic-account expiry checked lazily, not via a scheduled sweep.**
Decision: `DynamicVirtualAccount.isExpired()` is evaluated at lookup/deposit time; there is no `@Scheduled` job flipping ACTIVE→EXPIRED in the background.
Rejected: a periodic sweep (the same shape rejected for auto-settle in ADR-4).
Rationale: nothing needs to *react* to expiry happening — the only requirement is refusing to honor a deposit after the window closes, which a lazy check satisfies exactly. This codebase has no scheduled components at all yet; introducing the first one for a check that doesn't need to be proactive would be unjustified complexity.

Decisions made while implementing peer-to-peer transfer and the transaction PIN (FR-Auth-1, FR-Auth-2):

**ADR-12 — A cross-module gateway interface can be owned by the module that calls it, even against the normal dependency direction; the implementing module is the one with a compile-time dependency on the caller.**
Decision: `payments.transfer` defines `RecipientDirectory` and `TransactionPinGateway` — payments needs to resolve a phone number to a user and verify a PIN, but never depends on `onboarding-auth-rbac` (the module `User`/PIN storage actually lives in) and structurally can't, since the Maven dependency runs the other way (`onboarding-auth-rbac` depends on `payments`, never the reverse — see each module's own "Depends on" section). `onboarding-auth-rbac`'s `UserRecipientDirectory` and `TransactionPinService` implement these interfaces directly, which compiles cleanly precisely because it already depends on `payments`. `NawillPayApplication`'s whole-application component scan (`scanBasePackages = "ng.com.nawill.pay"`) wires interface to implementation at runtime regardless of which module declared which.
Rejected: (a) moving `User`/PIN storage into `common-core` so `payments` could depend on it directly — pulls a business-domain concept (identity) into the shared kernel, which doc 2 §2's module boundaries deliberately keep domain-free; (b) a callback/event-based design (payments publishes a "verify this PIN" event, onboarding-auth-rbac subscribes) — no messaging infrastructure exists in this codebase (see doc 3 §6's "no scheduled components... yet" note, same posture applies to async messaging), and it would turn a simple synchronous check into an unnecessary async round-trip.
Rationale: every existing gateway in this codebase (`PaymentProcessorGateway`, `SettlementGateway`, `BankVerificationGateway`, `EmailGateway`) is intra-module — interface and implementation in the same module, swapped for sandbox-vs-real. This is the first *cross-module* application of the same dependency-inversion idea, and it works for the same reason those do: the consumer defines the contract it needs, in its own vocabulary, and doesn't care who satisfies it.

**ADR-13 — Peer-to-peer transfer reuses the `Transaction` ledger with two additive columns, not a parallel `Transfer` table, and never touches `CollectionAccount`.**
Decision: a transfer produces exactly two `Transaction` rows (a DEBIT on the sender, a CREDIT on the recipient) sharing one `transferGroupId`, each naming the other as `counterpartyAccountId`. `payment_processor_id` becomes nullable — null on these two rows, since no external processor is involved. `TransferService.transfer()` applies both ledger effects directly and never calls `CollectionAccountRepository`.
Rejected: (a) a new `Transfer`/`TransferLeg` entity pair — would duplicate everything `Transaction`, `TransactionRepository`, and the existing list/analytics endpoints already do, for no behavioral difference (a transfer debit/credit is not meaningfully different from a collection debit/credit to anything downstream); (b) seeding a fake "Nawill Internal" `PaymentProcessor` row to keep the column `NOT NULL` — would misrepresent a table meant to hold real processor integrations only; (c) routing a transfer through `TransactionService.process()` (reusing the existing collection/withdrawal code path) — `process()` unconditionally calls `mirrorOnCollectionAccount(...)`, and ADR-5's reconciliation invariant depends on `CollectionAccount` reflecting only *real, external* bank movement. A transfer changes which internal virtual account holds a value without a single kobo entering or leaving the real pooled bank account; mirroring it onto `CollectionAccount` would corrupt that invariant, not preserve it.
Rationale: existing consumers of `Transaction` (list/analytics, `TransactionResponse`) keep working completely unmodified — a transfer just appears as an ordinary DEBIT/CREDIT row in each party's own history, with `transferGroupId` as the only new thing linking the two.

**ADR-14 — Transfer locks two `VirtualAccount` rows in a fixed order independent of sender/recipient direction, extending ADR-9's rule beyond `VirtualAccount`-before-`CollectionAccount`.**
Decision: `TransferService` compares the two accounts' UUIDs and locks the lexicographically-smaller one first, regardless of which one is the sender.
Rejected: locking sender-then-recipient in call order (the naive approach).
Rationale: ADR-9 already established that fixed lock ordering, not optimistic retry, is this codebase's answer to deadlock risk on balance mutations — but its rule ("VirtualAccount before CollectionAccount") doesn't say anything about ordering *two* `VirtualAccount` rows against each other. Locking in call order would let a transfer A→B and a concurrent transfer B→A deadlock against each other (each holds the row the other wants next); an order derived from the accounts' own identity, not from who's sending, makes that structurally impossible rather than merely unlikely, the same property ADR-9 was written to guarantee.

**ADR-15 — The transaction PIN gets its own, stricter Redis lockout, separate from `LoginAttemptService`, and no reuse-history check.**
Decision: `TransactionPinAttemptService` mirrors `LoginAttemptService`'s exact shape (Redis `INCR`+`EXPIRE`-on-first-hit counter, keyed by `userId` instead of email) but as a distinct service with its own, lower default threshold (3 attempts / 30-minute lockout vs. login's 3/15) and its own Redis key prefixes. A wrong PIN never locks login, and a locked-out account can still log in.
Rejected: (a) reusing `LoginAttemptService` directly, keyed by user id instead of email — would conflate two different capabilities (authenticate vs. move money) behind one counter and one lockout duration; (b) extending `PasswordHistoryService`'s PCI DSS 8.3.7 reuse-rejection pattern to PINs.
Rationale: a 4-digit PIN has only 10,000 possible values — materially easier to brute-force than a password — which justifies a stricter, separately-tuned lockout on the specific capability it protects (transfers), not on the account as a whole. PIN reuse history was deliberately *not* built: a 4-digit space is small enough that "not one of your last 4" barely narrows a brute-force attempt, so the added storage/complexity isn't worth the security it would buy — unlike passwords, where the entropy is high enough for history-checking to meaningfully matter.

---

## Chapter 3 — Security, Idempotency & Deployment Deep-Dive (doc 3)

### 1. Idempotency Handling

Payments are the single highest-risk area for duplication: a retried request (client timeout, mobile-network drop, load-balancer retry) must never result in a user being double-charged or double-credited. Idempotency is treated as a first-class, platform-wide concern, not a per-endpoint afterthought.

#### 1.1 Idempotency Key Design

- Every mutating financial operation (initiate transaction, settle, disburse, create payment link with a value attached) requires an Idempotency-Key header, generated client-side (UUID v4) or, if absent, generated server-side from a deterministic hash of (accountId + amount + recipient + narration + minute-bucket) as a fallback safety net.

- The key is persisted with a unique constraint on the transactions.idempotencyKey column. A second request with the same key returns the original response (replayed, not reprocessed) rather than executing business logic again.

- As of the settlement/API-key/payment-link/dynamic-account feature set, `@Idempotent` (the same annotation/aspect, unchanged) now also gates: `POST /api/v1/settlements` (manual settle), `POST /api/v1/collect` and `POST /api/v1/withdraw` (API-key-authenticated third-party surface), `POST /api/v1/payment-links` (create) and `POST /api/v1/pay/{shortCode}` (public payment-link redemption), and `POST /api/v1/temporary-accounts/{accountNumber}/simulate-deposit`. Settlement introduces one wrinkle: a single settle call can produce several `Settlement` rows (one per configured settlement account), so each row's own `idempotencyKey` column is the request's Idempotency-Key suffixed with that row's settlement-account id, keeping every row's DB-level uniqueness intact while all rows still map back to the one originating request.

- Redis is used as a short-lived (e.g. 24h) idempotency lock: on first sight of a key, a lock record is SET NX with a TTL; a concurrent duplicate request arriving while the first is still processing is rejected with 409 Conflict / "processing in progress" rather than racing the database constraint.

- The lock/response cache stores the eventual HTTP status and response body, so a legitimate retry after success gets the same 200 response instead of an error.

#### 1.2 Idempotency at the Database Layer

- The unique constraint on idempotencyKey is the source of truth — Redis is an optimization, never the sole guard, since Redis availability is not guaranteed under NFR-2 (network resilience).

- State transitions (PENDING → PROCESSING → PAID/FAILED) use optimistic locking (a version column) so that two workers cannot both flip the same transaction from PENDING to PAID.

- Webhook handlers from payment processors are themselves idempotent: each inbound webhook event carries the processor's own event ID, stored in a processed_webhook_events table with a unique constraint, so a re-delivered webhook is a no-op after the first successful application.

#### 1.3 Concurrency Control — Preventing Double Credit/Debit

Idempotency (§1.1–1.2) and concurrency control solve two different problems, both necessary: idempotency guards against the *same* request being processed twice (a client retry); concurrency control guards against two *different, legitimate* concurrent requests racing on the same shared mutable state (e.g. two collections landing on one virtual account at the same instant). Neither substitutes for the other.

- **Pessimistic row locking**, not optimistic-with-retry, on every balance mutation: `VirtualAccountRepository.findByIdForUpdate` and `CollectionAccountRepository.findActiveForUpdate` (`SELECT ... FOR UPDATE` via JPA's `PESSIMISTIC_WRITE` lock mode) replace a plain `findById` wherever `balance` is about to change — including the original `/transactions` create path, which previously fetched the virtual account unlocked (a genuine latent lost-update bug fixed as part of this work, not merely a new-code precaution). Two concurrent credits to the same account are now serialized at the database, not racing in application memory.

- **Fixed lock-ordering rule, enforced everywhere with no exceptions: `VirtualAccount` is always locked before `CollectionAccount`, never the reverse.** Any code path that touches both (settlement, auto-settle) follows this order, making deadlocking between concurrent settlements structurally impossible rather than merely unlikely under load.

- **Single-use resources** (a single-use payment link, a dynamic account's one allowed deposit) use the identical pattern: lock the row (`findByShortCodeForUpdate`, `findByAccountNumberForUpdate`), check eligibility, mutate status *and* create the transaction, all inside one transaction — never a separate check-then-update, which would leave a window for two concurrent redemptions to both pass the check.

- **Settlement-account split-percentage configuration** is protected the same way: creating a `SettlementAccount` locks the parent `VirtualAccount` for the whole sum-of-existing-percentages-≤100%-then-insert sequence, so two concurrent creates can never both pass validation before either commits.

- **A transactional-isolation subtlety worth calling out explicitly**: `SettlementService.settle()` is `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Auto-settle calls it from *inside* the same transaction as the triggering credit (`TransactionService.applyLedgerEffect`). Without `REQUIRES_NEW`, an uncaught exception inside `settle()` — even one the caller catches immediately afterward — would already have marked the *shared* transaction rollback-only at the Spring AOP proxy boundary the moment it propagated out of `settle()`'s own method call, since `settle()` is a public method on a separately-transaction-managed bean. That would silently roll back the originating credit too, the opposite of the intended "a broken auto-settle must never fail the collection that triggered it." `REQUIRES_NEW` gives auto-settle its own transaction: a failure there rolls back only the settlement (the `Settlement` row is left `FAILED`, retryable manually later), and the collection commits normally regardless.

#### 1.4 Idempotency Across the Reconciliation Job

The 6-hourly reconciliation job (FR-Recon-1) itself must be idempotent and safe to re-run: it operates by comparing sets of transaction IDs already marked reconciled against processor/NIBSS statements, and only ever moves a transaction from un-reconciled to reconciled — it never re-applies a settlement action for a transaction already flagged as settled.

### 2. Security Best Practices Against Hacking / Fraud

#### 2.1 Authentication & Session Security

- Password hashing via bcrypt/Argon2id (never reversible encryption); minimum complexity and breach-list checks (e.g. HaveIBeenPwned range API) at signup/change.

- JWT access tokens, short-lived (10–15 min), with rotating refresh tokens stored server-side (allow revocation) — a stolen access token has a small blast-radius window.

- 2FA (configurable per FR-8) enforced for privileged roles (ADMIN, SUPERADMIN) and for any transaction above the Tier-2 KYC threshold.

- Account lockout / exponential backoff after repeated failed login attempts; alerting on impossible-travel or new-device login.

#### 2.2 RBAC — Role-Based Access Control

- Enforced at two layers: (a) method-level via Spring Security @PreAuthorize on service methods, expressed against explicit permission strings (transactions:read, processors:configure), never bare role names, so permissions can be re-composed into new roles without code changes; and (b) endpoint-level via a security filter chain mapping URL patterns to required authorities as a defence-in-depth backstop.

- SUPERADMIN is seeded at deployment time (not created through any signup flow) and its permission set is hard-coded to "all" rather than assembled from the Permission table, so a data-layer compromise of the permissions table cannot silently grant superadmin elsewhere.

- Business-created admin/account-officer roles are always scoped by businessId at the query layer (row-level filtering), so even a misconfigured permission cannot leak cross-tenant data — every repository query for business-scoped entities includes the caller's businessId as a mandatory predicate, not an optional filter.

- Configuring/activating a payment processor (FR-6) is gated behind a distinct permission (processors:configure) granted only to a Supply Admin role, separate from general ADMIN.

#### 2.3 Transport & Data Security

- SSL/TLS 1.2+ enforced everywhere, terminated at the API gateway/load balancer; HSTS enabled; TLS certificates auto-renewed (e.g. via Let's Encrypt/ACM) with monitoring for expiry.

- Encryption at rest for the database (managed Postgres disk encryption) plus field-level encryption for the most sensitive PII (BVN, NIN, CAC number, bank account numbers) using envelope encryption with keys held in a managed KMS (AWS KMS / GCP KMS / HashiCorp Vault) — application code never touches raw key material.

- A business API key's secret is stored the same way in spirit but with a concrete MVP implementation: AES-256-GCM, keyed by a single symmetric application key (`nawill.security.encryption-key`, required config, no default outside `dev`), *not* a real KMS yet (doc 2 §7 ADR-6 records this explicitly as a stand-in). This has to be reversible rather than a one-way hash — verifying an inbound HMAC signature requires recovering the plaintext secret to recompute it — so it is deliberately not treated the same as password storage (§2.1's bcrypt/Argon2id, one-way, never reversed).

- Third-party requests authenticated via a business API key are HMAC-SHA256-signed (NFR-11), not just bearer-token-presented: `signature = HMAC-SHA256(secretKey, timestamp + "." + rawRequestBody)`, sent as `X-Public-Key` / `X-Timestamp` / `X-Signature` headers. The server recomputes and compares in constant time, and rejects a timestamp outside a configurable clock-skew window (default 300s) to bound replay of a captured request. See §2.5 for the full verification flow.

- IP whitelisting for: (a) admin portal access from known office/VPN ranges, (b) PSSP/partner API-key usage restricted to the IP ranges the partner registers at onboarding (FR-9), and (c) inbound webhook endpoints restricted to each payment processor's published IP ranges, combined with signature verification (HMAC of the payload using the processor's webhook secret) so IP spoofing alone cannot forge a webhook.

- Secrets (DB credentials, processor API keys, JWT signing keys) are never committed to source control; managed via a secrets manager injected at deploy time, rotated on a schedule and immediately on suspected compromise.

#### 2.4 Application-Layer Hardening

- Input validation via Bean Validation (jakarta.validation) on every DTO; parameterized queries only (JPA/Hibernate prevents SQL injection by construction — raw/native queries are code-reviewed for parameter binding).

- Rate limiting per API key / per IP at the gateway (token-bucket in Redis), tuned to a level below the NFR-10 throughput ceiling so an abusive client cannot starve legitimate traffic. **Implemented** for the third-party collect/withdraw surface as a Redis *fixed-window* counter (`INCR` + `EXPIRE`-on-first-hit, the same shape as the login-attempt lockout counter), not a literal token-bucket — a deliberate simplification (doc 2 §7 ADR-10): correct without a Lua script for atomic refill, and sufficient for this MVP's actual traffic. Applied per source IP (default 120 req/min) and per API key (default 60 req/min), both `nawill.security.rate-limit.*` config, both fail-open on Redis unavailability (same NFR-2 posture as idempotency). Deliberately not yet applied to every endpoint — JWT dashboard traffic and signup/login are unrated at the application layer today (login already has its own purpose-built lockout mechanism, §2.1).

- CSRF protection on any cookie-authenticated surface (admin portal); CORS restricted to known origins.

- Dependency scanning (OWASP Dependency-Check / Snyk) and static analysis (SonarQube) run in CI on every pull request; container images scanned for CVEs before promotion.

- Regular penetration testing and, ahead of scaling, a formal PCI-DSS-aligned review given the platform's role in handling payment data.

#### 2.5 Third-Party API Key Authentication & IP Whitelisting (FR-ApiKey-1, FR-Security-3, NFR-11)

`ApiKeyAuthenticationFilter` guards the two third-party-facing endpoints, `/api/v1/collect` and `/api/v1/withdraw`, via a dedicated `SecurityFilterChain` matched only to those paths (doc 2 §7 ADR-2) — everything else in the platform stays purely JWT-authenticated. Exactly one mechanism handles a given request; there is no fallback between them.

Verification order, each step short-circuiting the request on failure (a filter runs before Spring MVC dispatch, so failures are written directly as a structured `ErrorResponse` here rather than thrown — an exception thrown from a filter never reaches `@RestControllerAdvice`, which only wraps the DispatcherServlet):

1. **Per-IP rate limit** (§2.4) — cheapest check, runs first, before any database lookup, so a flood with no valid key at all is rejected fastest.
2. **Header presence** — `X-Public-Key`, `X-Timestamp`, `X-Signature` must all be present (`401 MISSING_SIGNATURE_HEADERS`).
3. **Key lookup** — the `ApiKeyCredential` for that public key must exist and be `status = ACTIVE` (`401 INVALID_API_KEY`); a regenerated (revoked) key fails here even with an otherwise-valid signature.
4. **Per-API-key rate limit** (§2.4).
5. **Clock-skew check** — `|now − timestamp| ≤` the configured window, default 300s (`401 STALE_TIMESTAMP`).
6. **Signature verification** — the secret is decrypted (§2.3) and the canonical string `timestamp + "." + rawBody` is HMAC-SHA256'd and compared in constant time against `X-Signature` (`401 INVALID_SIGNATURE`). The raw request body has to be read here, before Spring MVC's message converter consumes it for `@RequestBody` binding downstream — a `CachedBodyHttpServletRequest` wrapper reads the body once and replays it, so both the filter and the eventual controller method see the same bytes.
7. **IP whitelist** — if the key has one or more registered CIDR entries, the caller's source IP must match one (`403 IP_NOT_WHITELISTED`); an empty whitelist is unrestricted (doc 2 §7 ADR-8).
8. On success, the filter builds an `ApiKeyAuthenticationToken` whose principal *is* a `CurrentUser` (no `userId`, `businessId` = the key's owning business, `userType = "API_CLIENT"`, `permissions = {collect:create, withdraw:create}`) and installs it in `SecurityContextHolder`. `CurrentUserResolver.resolve()` was extended with a branch recognizing this token type alongside the existing JWT one — this is the single integration point that lets `@auth.can(...)`/`PermissionChecker` and every business-scoping check downstream (e.g. `VirtualAccountQueryService.requireSoleVirtualAccountForCaller()`) work completely unmodified regardless of which auth mechanism produced the caller's identity.

`collect:create`/`withdraw:create` are never written to the `permissions`/`role_permission` tables — they exist only as literals the filter attaches directly to the synthesized `CurrentUser`, since an API-key principal has no underlying `User`/`Role` row to resolve permissions from in the first place.

### 3. NIBSS Integration Considerations

NIBSS (Nigeria Inter-Bank Settlement System) provides the interbank rails (NIP — NIBSS Instant Payment) that move real money between Nawill Pay's partner settlement bank and external banks. Nawill Pay does not integrate with NIBSS directly at MVP; it accesses NIBSS capability indirectly through its partner commercial/microfinance bank or a licensed processor (FR-10), which is the standard route for a non-deposit-taking fintech in Nigeria.

- Name Enquiry: before any settlement/disbursement, resolve the destination account name via the partner bank's NIBSS name-enquiry endpoint and require the user to confirm the resolved name — reduces misdirected-funds fraud.

- Fund Transfer (NIP): outbound settlements are submitted through the partner bank's NIP integration; the platform tracks NIBSS session IDs/reference codes against the internal transactionId for traceability.

- Reversal handling: NIBSS transactions can be reversed by the receiving bank in specific failure scenarios; the reconciliation job explicitly checks for reversal codes and reflects them back onto the internal ledger rather than leaving a phantom credit.

- Settlement account (FR-10) is the pooled account where real NIBSS deposits land; internal virtual-account balances are always a ledger abstraction over this pooled account, and the sum of all virtual-account balances must reconcile to the pooled account balance — this invariant is checked by the reconciliation job and alerts on drift.

- Cut-off times and settlement windows: NIP is largely real-time, but batch/NEFT-style rails used as a fallback (for high-value or after-hours transfers) have defined cut-off windows that the disbursement scheduler must respect.

### 4. Git Workflow & Branching Strategy

#### 4.1 Branching Model

Trunk-based development with short-lived feature branches, chosen over long-lived GitFlow branches to keep a fast-moving small team merging frequently and reduce painful integration conflicts.

|  |  |
|----|----|
| **Branch** | **Purpose** |
| main | Always deployable. Every merge to main triggers a Staging deployment (see §5). Protected — no direct pushes, PR + passing CI + 1 approval required. |
| feature/\<ticket\>-\<slug\> | Short-lived branch per unit of work, e.g. feature/NW-142-payment-links. Branched from main, rebased regularly, squash-merged back. |
| fix/\<ticket\>-\<slug\> | Bug fixes, same conventions as feature/. |
| release/\<version\> | Cut from main when preparing a Production promotion; only cherry-picked hotfixes land here; tagged on release. |
| hotfix/\<ticket\>-\<slug\> | Urgent production fix, branched from the latest production tag, merged to both release/main. |

#### 4.2 Commit & PR Conventions

Full naming and commit-message conventions are defined in the companion Contribution Guide & Naming Conventions document. In summary: Conventional Commits format (feat:, fix:, chore:, refactor:, docs:, test:), PR titles mirror the primary commit, and every PR must link its tracking ticket.

### 5. Deployment Strategy

#### 5.1 Environments

|  |  |  |
|----|----|----|
| **Environment** | **Trigger** | **Characteristics** |
| Development | Local / on feature-branch push | Local Docker Compose stack (Postgres, Redis, Kafka) or a shared dev namespace; seeded synthetic data; processor integrations run in sandbox mode. |
| Staging | Automatic on merge to main | Mirrors production topology at lower capacity; sandbox/test credentials for payment processors and NIBSS; used for QA, UAT, and load-test rehearsal. |
| Production | Manual promotion from a tagged release/\* branch, requiring sign-off | Live credentials, real money movement, full monitoring/alerting, IP-whitelisted admin access only. |

#### 5.2 CI/CD Pipeline

1.  Lint & static analysis (Checkstyle/SonarQube) on every push.

2.  Unit tests (JUnit 5 + Mockito) — required to pass, coverage threshold enforced (e.g. 80% on the Payments and Auth modules).

3.  Integration tests (Testcontainers spinning up real Postgres/Redis/Kafka) — validate module boundaries and repository behaviour against a real database engine, not H2.

4.  Contract tests for external integrations (payment processors, NIBSS name-enquiry) against recorded/mocked responses (WireMock) so pipeline runs don't depend on third-party sandbox uptime.

5.  Build & scan — Maven build, dependency vulnerability scan, Docker image build and image CVE scan.

6.  Deploy to Staging automatically; run smoke tests and a synthetic end-to-end transaction against sandbox processors.

7.  Manual approval gate, then deploy to Production using a rolling/blue-green strategy (never a hard cutover) so a bad release can be rolled back without downtime.

8.  Post-deploy health checks and automatic rollback on failed readiness/liveness probes.

#### 5.3 Rollback & Migrations

- Database migrations (Flyway/Liquibase) are additive/backward-compatible within a release (expand-contract pattern) so the previous application version keeps working against the new schema during a rollback window.

- Feature flags gate risky functionality (e.g. a new processor integration) so it can be disabled instantly without a redeploy.

### 6. Withstanding Heavy Load

Target (NFR-10): comfortably process ~₦1 billion in transaction value per day, at a sustained peak of roughly 1,000 requests/second, with acceptable latency.

- Horizontal scaling: the Spring Boot service is stateless (JWT-based auth, sessions in Redis) so it scales horizontally behind a load balancer / Kubernetes HPA driven by CPU and request-latency metrics.

- Database: connection pooling (HikariCP) tuned per instance; read replicas for reporting/analytics queries so heavy report generation never contends with the transactional write path; partitioning/indexing strategy on the Transactions table (by createdAt month) to keep hot-path queries fast as volume grows.

- Caching: Redis absorbs repeated reads of rarely-changing config (fee tiers, processor status, bank list), reducing database load under burst traffic.

- Async offload: anything not required for the synchronous payment-confirmation response (notifications, audit-log writes, webhook dispatch to partners) is pushed to Kafka and processed by separate consumer workers, keeping the critical path short.

- Backpressure & circuit breakers (Resilience4j) around every external dependency (payment processors, NIBSS/partner bank, SMS provider) so a slow/unavailable third party degrades gracefully — e.g. queue-and-retry — instead of exhausting request threads.

- Load testing (k6/Gatling) against Staging ahead of major releases, explicitly targeting the NFR-10 numbers, with results tracked over time to catch regressions.

### 7. Logging & Observability

#### 7.1 Structured, Correlated Logging

- Every API request receives (or is assigned, if absent) a Request-ID on the request header. This request-id is propagated through the MDC (Mapped Diagnostic Context) from the controller layer down through every service/repository call, and is included in every log line for that request, enabling full request tracing across a distributed call graph without a separate tracing tool at MVP stage (though the design is compatible with adding OpenTelemetry/Jaeger later).

- Logs are structured (JSON) rather than freeform text, shipped to a centralized log store (e.g. ELK/OpenSearch or a managed equivalent), with consistent fields: timestamp, level, requestId, userId (if authenticated), module, message, and a redaction filter that strips PII/secrets (BVN, NIN, full card/account numbers, tokens) before persistence.

#### 7.2 What Gets Logged

|  |  |
|----|----|
| **Log Type** | **Captured Detail** |
| Application logs | Request/response summaries (not full bodies for sensitive endpoints), errors with stack traces, external-call latencies. |
| UserChangeLog / activity | Every account activity — created, updated, login, logout, IP address, device fingerprint — persisted to the database, not just log files, for compliance retention. |
| Transaction audit trail | Every state transition of a transaction (PENDING → PROCESSING → PAID/FAILED), who/what triggered it, and the idempotency key involved. |
| Admin action logs | Every privileged action — processor activation, fee-tier change, role/permission change — logged with the acting admin's identity, immutable. |
| Security events | Failed logins, permission-denied events, IP-whitelist rejections, webhook signature failures — feed into alerting. |

#### 7.3 Monitoring & Alerting

- Metrics via Micrometer → Prometheus/Grafana: request rate, error rate, latency percentiles (p50/p95/p99) per endpoint, queue depth, reconciliation drift.

- Alert thresholds tied directly to the NFRs — e.g. alert if p95 latency breaches SLA, if error rate exceeds a threshold, or if the reconciliation job detects unreconciled value above a configurable amount.

- On-call rotation and runbooks for the highest-risk failure modes: processor outage, NIBSS/partner-bank outage, reconciliation drift, and a spike in failed authentications (possible credential-stuffing attack).

---

## Chapter 4 — Contribution Guide, Naming Conventions & Process Flows (doc 4)

### Part A — Contribution Guide

#### A.1 Getting Started

1.  Clone the relevant module repository (or the monorepo, if the modular monolith is kept in one repo — recommended at MVP stage).

2.  Run the local Docker Compose stack (Postgres, Redis, Kafka) — see README for the docker-compose.dev.yml.

3.  Copy .env.example to .env.local and populate sandbox credentials (never commit real credentials).

4.  Run ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev.

#### A.2 Definition of Ready / Definition of Done

|  |  |
|----|----|
| **Definition of Ready (before starting)** | **Definition of Done (before merging)** |
| Ticket has clear acceptance criteria, linked to a requirement ID (FR-x/NFR-x) where applicable | Unit + integration tests written and passing |
| Design/API contract agreed for any new external-facing endpoint | Swagger/OpenAPI docs auto-generated and reviewed for accuracy |
| Any schema change has a draft migration reviewed by another engineer | No new Sonar/dependency-scan critical findings |
|  | Structured logging + request-id propagation present on new endpoints |
|  | PR approved by at least one other engineer; CI green |

#### A.3 Pull Request Standards

- One logical change per PR — prefer several small PRs over one large one.

- PR description must state: what changed, why, which requirement/ticket it addresses, and how it was tested.

- Financial-logic PRs (fees, splits, idempotency, RBAC) require a second reviewer with domain context, in addition to the standard approval.

- Squash-merge to main with a Conventional Commit message (see A.4).

#### A.4 Commit Message Convention

Format: \<type\>(\<scope\>): \<short summary\>, e.g. feat(payments): add percentage-based settlement splits

|          |                                                         |
|----------|---------------------------------------------------------|
| **Type** | **Use for**                                             |
| feat     | A new feature / requirement implemented                 |
| fix      | A bug fix                                               |
| refactor | Code change that neither fixes a bug nor adds a feature |
| test     | Adding or correcting tests                              |
| docs     | Documentation only changes                              |
| chore    | Build process, tooling, dependency bumps                |
| security | A change specifically addressing a security finding     |

#### A.5 Code Review Checklist (financial code specifically)

- Does every mutating money-movement endpoint require and correctly persist an idempotency key?

- Are all monetary values handled as integer minor units (kobo), never floating point?

- Is every business-scoped query filtered by the caller's businessId, not just permission-checked?

- Does the change log the request-id and, for privileged actions, the acting admin's identity?

- Are new external calls wrapped in a circuit breaker / timeout, per §6 of the Security & Deployment Deep-Dive?

### Part B — Naming Conventions

#### B.1 Git

|  |  |
|----|----|
| **Item** | **Convention** |
| Branches | feature/\<TICKET\>-\<kebab-slug\>, fix/\<TICKET\>-\<kebab-slug\>, hotfix/\<TICKET\>-\<kebab-slug\>, release/\<vX.Y.Z\> — e.g. feature/NW-142-payment-links |
| Commits | Conventional Commits — see A.4 |
| Tags | v\<MAJOR\>.\<MINOR\>.\<PATCH\>, Semantic Versioning — e.g. v0.3.1 |

#### B.2 Java Code

|  |  |
|----|----|
| **Element** | **Convention** |
| Base package | ng.com.nawill.pay.\<module\>, e.g. ng.com.nawill.pay.payments |
| Classes / Interfaces | UpperCamelCase, noun-based — TransactionService, PaymentProcessorRepository, IdempotencyGuard |
| Interfaces vs. Impl | Interface: PaymentProcessorGateway; implementation: PaystackGateway, InterswitchGateway — never suffix the interface with Impl on the primary contract |
| Methods | lowerCamelCase, verb-first — initiateTransaction(), resolveAccountName(), reconcileBatch() |
| DTOs | Suffix by direction: CreateTransactionRequest, TransactionResponse, WebhookPayload |
| Entities | Singular noun matching the table's logical name — Transaction, VirtualAccount, PaymentProcessor |
| Enums | UPPER_SNAKE_CASE values — TransactionStatus.ON_HOLD, UserType.SUPERADMIN |
| Constants | UPPER_SNAKE_CASE — DEFAULT_MIN_TRANSFER_AMOUNT |
| Test classes | \<ClassUnderTest\>Test for unit tests, \<ClassUnderTest\>IT for integration tests |

#### B.3 Database

|  |  |
|----|----|
| **Element** | **Convention** |
| Tables | snake_case, plural — transactions, virtual_accounts, payment_processors |
| Columns | snake_case — created_at, idempotency_key, transaction_status |
| Foreign keys | \<singular_table\>\_id — user_id, business_id, payment_processor_id |
| Indexes | idx\_\<table\>\_\<column(s)\> — idx_transactions_idempotency_key |
| Migration files | Flyway: V\<version\>\_\_\<description\>.sql, e.g. V0007\_\_add_settlement_split_percentage.sql |

#### B.4 API

|  |  |
|----|----|
| **Element** | **Convention** |
| Base path | /api/v{n}/\<resource\>, plural nouns — /api/v1/transactions, /api/v1/virtual-accounts |
| Versioning | URI-based major versioning (/v1, /v2); breaking changes always bump the major version; additive fields do not |
| Headers | Idempotency-Key, X-Request-Id, Authorization: Bearer \<jwt\> |
| Query params | camelCase — /transactions?fromDate=&toDate=&status= |
| JSON fields | camelCase to match DTOs, consistent both directions (request/response) |

#### B.5 Environments & Config

- Spring profiles: dev, staging, prod — never a custom ad-hoc profile name.

- Environment variables: UPPER_SNAKE_CASE, module-prefixed where ambiguous — PAYMENTS_DB_URL, AUTH_JWT_SECRET.

- Feature flags: \<module\>.\<feature\>-enabled, e.g. payments.new-settlement-engine-enabled.

### Part C — Process Flows

Represented as sequential swimlane tables (Actor → System step) for portability. Each can be redrawn as a swimlane diagram/organogram in Miro/Lucidchart for stakeholder presentations without changing the underlying logic documented here.

#### C.1 Onboarding Flow

Covers both individual (USER) and business signup, per FR-1, FR-8, FR-9.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **User/Business** | Submits signup form: name, email, phone, (CAC number if business). |
| 2 | **System** | Creates User/Business record with status ACTIVE, isVerified = false; sends email/phone OTP. |
| 3 | **User** | Verifies OTP. |
| 4 | **System** | Marks contact as verified; prompts for 2FA setup (configurable) and Tier-1 KYC (BVN/NIN or CAC). |
| 5 | **System (KYC service)** | Calls third-party identity verification provider; stores result in KYC table with tier assigned. |
| 6 | **System** | On successful Tier-1 KYC, auto-provisions a VirtualAccount (FR-1) and default Role assignment (USER, or Business Owner). *(Settlement account setup, split configuration, API key generation, and payment-link/dynamic-account creation are deliberately not part of signup itself — they're separate authenticated follow-up steps available any time afterward; see §C.6–C.8.)* |
| 7 | **System** | Sends welcome notification with virtual account details. |
| 8 | **User/Business (optional)** | If a PSSP/partner: requests or accepts an invite, then generates API key pair + registers webhook URL (FR-9). |
| 9 | **Business Owner (optional)** | Creates scoped internal roles (Admin, Account Officer) and invites staff (FR-5a). |
| 10 | **User** | Attempts a transaction above ₦50,000 (or configured threshold) → system triggers Tier-2/enhanced KYC before allowing it (FR-8a). |

#### C.2 Transaction (Collection & Settlement) Flow

Covers inbound collection through to settlement, per FR-10, FR-11, FR-2, FR-Txn-1.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Payer** | Initiates payment to a Nawill Pay virtual account / payment link, via a payment processor's checkout. |
| 2 | **Payment Processor** | Processes payment, sends an inbound webhook to Nawill Pay with the processor's event ID. |
| 3 | **System (Payments module)** | Verifies webhook signature (HMAC) and source IP; checks processed_webhook_events for duplicate delivery (idempotent no-op if already applied). |
| 4 | **System** | Creates/updates the Transaction record using the idempotency key; computes the applicable fee tier (FR-11); sets transactionStatus = PROCESSING. |
| 5 | **System** | Confirms real fund receipt into the pooled collection account (via partner bank NIBSS notification or processor settlement confirmation). |
| 6 | **System** | Credits the payer's/merchant's VirtualAccount ledger balance; sets transactionStatus = PAID. |
| 7 | **System (Settlement engine)** | If split settlement is configured (FR-2), calculates each SettlementAccount's share by percentage. |
| 8 | **System** | Initiates disbursement to SettlementAccount(s) via the partner bank's NIBSS NIP integration, after a Name Enquiry confirmation. |
| 9 | **System** | Sends outbound webhook to the merchant/partner and an in-app/SMS/email notification to the user (FR-Notif-1). |
| 10 | **System (Reconciliation job)** | Every 6 hours, reconciles the transaction against processor and NIBSS statements; flags drift for admin review (FR-Recon-1). |

#### C.3 Reporting / Statement Generation Flow

Per FR-Report-1.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Business/Admin** | Requests a report from the admin portal or API, specifying date range and filters (status, processor, business). |
| 2 | **System (Reporting module)** | Queries the read-replica / materialized view (not the primary transactional DB) to avoid contending with live payments. |
| 3 | **System** | Compiles report (transaction statement, settlement report, or reconciliation report) and renders to CSV/PDF. |
| 4 | **System** | Persists the generated report reference and notifies the requester when ready (for large async reports) or returns it synchronously for small ranges. |
| 5 | **Business/Admin** | Downloads the report; action is recorded in UserChangeLog/admin action logs. |

#### C.4 Admin / RBAC Management Flow

Per FR-3, FR-4, FR-5, FR-5a, FR-6.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **SUPERADMIN (seeded)** | Logs in with unrestricted, pre-seeded access; no self-service creation path exists for this role. |
| 2 | **SUPERADMIN / Supply Admin** | Onboards and activates a new PaymentProcessor (restricted permission: processors:configure) (FR-6). |
| 3 | **Business Owner** | Creates a scoped Role (e.g. "Account Officer") with a subset of permissions, assigns it to a staff User (FR-5a). |
| 4 | **Admin/Account Officer** | Logs into the admin portal; is presented only with the menus/data their permission set and businessId scope allow (FR-4). |
| 5 | **Admin** | Views analytics dashboard — transactions, settlement summaries, filtered to their business scope (FR-3). |
| 6 | **System** | Every privileged action (role change, processor activation, fee-tier edit) is written to the immutable admin action log with the acting admin's identity. |

#### C.5 Logging & Audit Flow (App & User Activity)

Per NFR-3 and the Security & Deployment Deep-Dive §7.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Client** | Sends any API request, optionally with a Request-Id header. |
| 2 | **System (Gateway/Filter)** | If Request-Id is absent, generates one; places it in MDC for the lifetime of the request. |
| 3 | **System (Controller → Service → Repository)** | Every log line emitted at any layer automatically includes the requestId, module, and (if authenticated) userId. |
| 4 | **System** | On any state-changing action, writes a structured entry to UserChangeLog (activity, type, ipAddress, timestamp). |
| 5 | **System (Log shipper)** | Ships structured JSON logs to the centralized log store, applying PII redaction before persistence. |
| 6 | **Monitoring** | Metrics (latency, error rate, throughput) scraped continuously; alerts fire against NFR-tied thresholds; on-call engineer uses the requestId to trace the full path of any failed request across modules. |

#### C.6 Settlement Account Setup & Split Configuration Flow

Per FR-Settle-1, FR-Settle-2.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Business Owner** | Registers a real bank account (bank + account number + account name) against their business via `POST /api/v1/bank-accounts`. |
| 2 | **Business Owner** | Attaches it as a settlement account with a split percentage via `POST /api/v1/settlement-accounts`. |
| 3 | **System** | Locks the business's VirtualAccount row, sums its existing active split percentages plus the new one, and rejects the request if the total would exceed 100% (SPLIT_PERCENTAGE_EXCEEDED) — otherwise creates the SettlementAccount. |
| 4 | **Business Owner (optional)** | Repeats steps 1–2 to add further settlement accounts, splitting collections across several destinations. |
| 5 | **Business Owner (optional)** | Toggles auto-settle on via `PATCH /api/v1/settlement-accounts/auto-settle`. |
| 6 | **SUPERADMIN (one-time, platform-wide)** | Separately, and independently of any business, sets up the single pooled Collection Account via `POST /api/v1/collection-account` (`collection-account:manage`, no other role holds this permission). |
| 7 | **System** | From this point on, every successful collection also credits the Collection Account's balance, and — if auto-settle is on — immediately triggers settlement (§C.2 step 7 onward) without the business needing to call anything further. |

#### C.7 API Key Issuance, Signing & IP Whitelist Flow

Per FR-9, FR-ApiKey-1, FR-Security-3, NFR-11.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Business Owner** | Generates an API key pair via `POST /api/v1/api-keys`. |
| 2 | **System** | Issues `pk_live_…`/`sk_live_…`, stores the secret AES-256-GCM-encrypted, and returns the plaintext secret in this one response only — it is never shown again. |
| 3 | **Business Owner (optional)** | Registers one or more CIDR ranges via `POST /api/v1/api-keys/ip-whitelist`; until at least one exists, the key is unrestricted by source IP. |
| 4 | **Third-party system (using the key)** | For each request to `/api/v1/collect` or `/api/v1/withdraw`: computes `HMAC-SHA256(secretKey, timestamp + "." + body)` and sends it as `X-Signature`, alongside `X-Public-Key` and `X-Timestamp`. |
| 5 | **System (ApiKeyAuthenticationFilter)** | Rate-limits by IP then by key, verifies the key is active, checks the timestamp is within the clock-skew window, recomputes and compares the signature, checks the source IP against any configured whitelist — rejecting at the first failed check (doc 3 §2.5 has the exact order). |
| 6 | **System** | On success, authenticates the request as the key's owning business and proceeds to the normal collect (credit) or withdraw (settle) logic — identical to the JWT-authenticated equivalents. |
| 7 | **Business Owner (if compromised or rotating on schedule)** | Calls `POST /api/v1/api-keys/regenerate` — the old pair is immediately deactivated (further requests using it fail at step 5) and a new pair is issued per steps 1–2. |

#### C.8 Payment Link & Dynamic Account Flow

Per FR-14, FR-Link-2, FR-DynAcct-1 — two related payer-facing collection mechanisms: pay-by-redirect (payment link) and pay-by-bank-transfer (dynamic account).

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Business Owner** | Creates a payment link via `POST /api/v1/payment-links` — permanent or temporary (default 24h expiry if unspecified), fixed or payer-entered amount, single-use or reusable. |
| 2 | **System** | Generates a unique short code (the link's short URL) and returns it. |
| 3 | **Business** | Shares the resulting `/api/v1/pay/{shortCode}` URL with a payer by any channel (SMS, email, chat). |
| 4 | **Payer (anonymous, no Nawill Pay account)** | Opens `GET /api/v1/pay/{shortCode}` (public) to see the amount/details, then `POST /api/v1/pay/{shortCode}` to pay. |
| 5 | **System** | Validates the link is ACTIVE and not expired, credits the linked VirtualAccount (via the same TransactionService path used everywhere else, so auto-settle and Collection Account mirroring both apply automatically), and marks the link REDEEMED if single-use. |
| 6 | **Business Owner (alternative path)** | Instead of a link, mints a dynamic account via `POST /api/v1/temporary-accounts`, specifying an expected amount/reference and getting back a one-time 10-digit account number. |
| 7 | **Payer** | Transfers from their own bank app directly to that account number — since it's unique to this one payment intent, the transfer is unambiguous without relying on a narration/reference the payer might mistype or the bank might truncate. |
| 8 | **System (sandboxed stand-in for the real NIBSS inbound-transfer webhook)** | The receiving business calls `POST /api/v1/temporary-accounts/{accountNumber}/simulate-deposit` to represent the transfer landing — credits the parent VirtualAccount the same way, marks the dynamic account PAID. A second deposit attempt, or one after the account's expiry window, is rejected. |
