# NAWILL PAY

## Contribution Guide, Naming Conventions & Process Flows

*Engineering Handbook — How We Build, Name Things, and Move Money*

Version 0.1 (MVP Draft)

Prepared by: Nawill Technology Ltd — Engineering

Date: August 2026

## Part A — Contribution Guide

### A.1 Getting Started

1.  Clone the relevant module repository (or the monorepo, if the modular monolith is kept in one repo — recommended at MVP stage).

2.  Run the local Docker Compose stack (Postgres, Redis, Kafka) — see README for the docker-compose.dev.yml.

3.  Copy .env.example to .env.local and populate sandbox credentials (never commit real credentials).

4.  Run ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev.

### A.2 Definition of Ready / Definition of Done

|  |  |
|----|----|
| **Definition of Ready (before starting)** | **Definition of Done (before merging)** |
| Ticket has clear acceptance criteria, linked to a requirement ID (FR-x/NFR-x) where applicable | Unit + integration tests written and passing |
| Design/API contract agreed for any new external-facing endpoint | Swagger/OpenAPI docs auto-generated and reviewed for accuracy |
| Any schema change has a draft migration reviewed by another engineer | No new Sonar/dependency-scan critical findings |
|  | Structured logging + request-id propagation present on new endpoints |
|  | PR approved by at least one other engineer; CI green |

### A.3 Pull Request Standards

- One logical change per PR — prefer several small PRs over one large one.

- PR description must state: what changed, why, which requirement/ticket it addresses, and how it was tested.

- Financial-logic PRs (fees, splits, idempotency, RBAC) require a second reviewer with domain context, in addition to the standard approval.

- Squash-merge to main with a Conventional Commit message (see A.4).

### A.4 Commit Message Convention

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

### A.5 Code Review Checklist (financial code specifically)

- Does every mutating money-movement endpoint require and correctly persist an idempotency key?

- Are all monetary values handled as integer minor units (kobo), never floating point?

- Is every business-scoped query filtered by the caller's businessId, not just permission-checked?

- Does the change log the request-id and, for privileged actions, the acting admin's identity?

- Are new external calls wrapped in a circuit breaker / timeout, per §6 of the Security & Deployment Deep-Dive?

## Part B — Naming Conventions

### B.1 Git

|  |  |
|----|----|
| **Item** | **Convention** |
| Branches | feature/\<TICKET\>-\<kebab-slug\>, fix/\<TICKET\>-\<kebab-slug\>, hotfix/\<TICKET\>-\<kebab-slug\>, release/\<vX.Y.Z\> — e.g. feature/NW-142-payment-links |
| Commits | Conventional Commits — see A.4 |
| Tags | v\<MAJOR\>.\<MINOR\>.\<PATCH\>, Semantic Versioning — e.g. v0.3.1 |

### B.2 Java Code

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

### B.3 Database

|  |  |
|----|----|
| **Element** | **Convention** |
| Tables | snake_case, plural — transactions, virtual_accounts, payment_processors |
| Columns | snake_case — created_at, idempotency_key, transaction_status |
| Foreign keys | \<singular_table\>\_id — user_id, business_id, payment_processor_id |
| Indexes | idx\_\<table\>\_\<column(s)\> — idx_transactions_idempotency_key |
| Migration files | Flyway: V\<version\>\_\_\<description\>.sql, e.g. V0007\_\_add_settlement_split_percentage.sql |

### B.4 API

|  |  |
|----|----|
| **Element** | **Convention** |
| Base path | /api/v{n}/\<resource\>, plural nouns — /api/v1/transactions, /api/v1/virtual-accounts |
| Versioning | URI-based major versioning (/v1, /v2); breaking changes always bump the major version; additive fields do not |
| Headers | Idempotency-Key, X-Request-Id, Authorization: Bearer \<jwt\> |
| Query params | camelCase — /transactions?fromDate=&toDate=&status= |
| JSON fields | camelCase to match DTOs, consistent both directions (request/response) |

### B.5 Environments & Config

- Spring profiles: dev, staging, prod — never a custom ad-hoc profile name.

- Environment variables: UPPER_SNAKE_CASE, module-prefixed where ambiguous — PAYMENTS_DB_URL, AUTH_JWT_SECRET.

- Feature flags: \<module\>.\<feature\>-enabled, e.g. payments.new-settlement-engine-enabled.

## Part C — Process Flows

Represented as sequential swimlane tables (Actor → System step) for portability. Each can be redrawn as a swimlane diagram/organogram in Miro/Lucidchart for stakeholder presentations without changing the underlying logic documented here.

### C.1 Onboarding Flow

Covers both individual (USER) and business signup, per FR-1, FR-8, FR-9.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **User/Business** | Submits signup form: name, email, phone, (CAC number if business). |
| 2 | **System** | Creates User/Business record with status ACTIVE, isVerified = false; sends email/phone OTP. |
| 3 | **User** | Verifies OTP. |
| 4 | **System** | Marks contact as verified; prompts for 2FA setup (configurable) and Tier-1 KYC (BVN/NIN or CAC). |
| 5 | **System (KYC service)** | Calls third-party identity verification provider; stores result in KYC table with tier assigned. |
| 6 | **System** | On successful Tier-1 KYC, auto-provisions a VirtualAccount (FR-1) and default Role assignment (USER, or Business Owner). |
| 7 | **System** | Sends welcome notification with virtual account details. |
| 8 | **User/Business (optional)** | If a PSSP/partner: requests or accepts an invite, then generates API key pair + registers webhook URL (FR-9). |
| 9 | **Business Owner (optional)** | Creates scoped internal roles (Admin, Account Officer) and invites staff (FR-5a). |
| 10 | **User** | Attempts a transaction above ₦50,000 (or configured threshold) → system triggers Tier-2/enhanced KYC before allowing it (FR-8a). |

### C.2 Transaction (Collection & Settlement) Flow

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

### C.3 Reporting / Statement Generation Flow

Per FR-Report-1.

|  |  |  |
|----|----|----|
| **\#** | **Actor** | **Step** |
| 1 | **Business/Admin** | Requests a report from the admin portal or API, specifying date range and filters (status, processor, business). |
| 2 | **System (Reporting module)** | Queries the read-replica / materialized view (not the primary transactional DB) to avoid contending with live payments. |
| 3 | **System** | Compiles report (transaction statement, settlement report, or reconciliation report) and renders to CSV/PDF. |
| 4 | **System** | Persists the generated report reference and notifies the requester when ready (for large async reports) or returns it synchronously for small ranges. |
| 5 | **Business/Admin** | Downloads the report; action is recorded in UserChangeLog/admin action logs. |

### C.4 Admin / RBAC Management Flow

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

### C.5 Logging & Audit Flow (App & User Activity)

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
