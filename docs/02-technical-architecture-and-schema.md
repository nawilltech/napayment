# NAWILL PAY

## Technical Architecture & Database Schema

*Java / Spring Boot API Platform — Schema Design, ERD & Module Breakdown*

Version 0.1 (MVP Draft)

Prepared by: Nawill Technology Ltd — Engineering

Date: August 2026

## 1. Technology Stack

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

### 1.1 Architectural Style

The platform is built as a well-structured modular monolith rather than microservices at MVP stage. Each business capability is isolated into its own Maven module/package with a clear public API (interfaces/DTOs) and no cross-module reach into another module's internal repositories or entities. This keeps operational overhead low for a small team while preserving clean seams that allow individual modules (e.g. Payments) to be extracted into standalone services later, once load or team size justifies it.

Design adheres to SOLID and DRY principles throughout: single-responsibility services, dependency-inverted integrations (payment processors implemented behind a common interface), and shared cross-cutting concerns (logging, idempotency, exception handling) centralized in a common/core module rather than duplicated.

### 1.2 Kafka vs. RabbitMQ — Recommendation

|  |  |  |
|----|----|----|
| **Consideration** | **Kafka** | **RabbitMQ** |
| Best fit for | High-throughput event streams, durable replay, audit/event-sourcing | Task queues, RPC-style jobs, complex routing |
| Throughput at scale | Very high; built for the ₦1bn/day, ~1,000 req/s target | Good, but generally lower ceiling than Kafka at this volume |
| Message replay / audit | Native (log retention) — valuable for reconciliation and dispute resolution | Not native; requires additional tooling |
| Operational complexity | Higher (ZooKeeper/KRaft, partitions, consumer groups) | Lower; simpler to operate for a small team |
| Delivery semantics needed | At-least-once with idempotent consumers — matches our idempotency-key design | At-least-once achievable, less natural for replay-based reconciliation |

*Recommendation: use Kafka for the core transaction-event backbone (payment.received, payment.settled, webhook.inbound, webhook.outbound, reconciliation.mismatch) because replay-ability directly supports reconciliation and dispute investigation, and its throughput ceiling comfortably covers the stated NFR-10 target. Use a lighter-weight mechanism — Spring's own @Async / a scheduled-task table, or RabbitMQ if a second broker is truly warranted — for low-volume, latency-insensitive background jobs (e.g. sending a single onboarding email). Avoid running two brokers unless a concrete requirement (e.g. RPC-style synchronous job response) emerges; Kafka alone is sufficient for MVP and keeps operational surface area smaller.*

## 2. Module Breakdown

### 2.1 Onboarding, Authentication & RBAC Module

Covers all aspects of onboarding and authentication for every user type — individual USER, ADMIN, SUPERADMIN, PSSP/partner, and organizations/businesses. Responsibilities:

- Signup / login (individual and business), password & credential management

- 2FA enrolment and verification (configurable per account)

- KYC/KYB orchestration (BVN/NIN, CAC lookups) and tiered-KYC upgrade triggers

- Role & permission management (RBAC): role CRUD, permission CRUD, role-assignment, scoped-to-business role creation

- API key / secret key issuance and rotation for PSSP/partner accounts, webhook URL registration

- JWT issuance/validation, session and refresh-token handling

### 2.2 Payments Module

Handles all transaction inflows and outflows. Responsibilities:

- Virtual account provisioning and balance/ledger management

- Payment processor configuration and setup (Paystack, Interswitch, Flutterwave, etc.) — restricted to Supply Admin role

- Transaction initiation, fee calculation (configurable tiers), idempotency enforcement

- Settlement/split-payment engine — percentage-based distribution to settlement accounts

- Payment link generation (permanent and temporary/expiring)

- Webhook handling — inbound (from processors) and outbound (to partners), plus manual requery

- Reconciliation job (scheduled every 6 hours) against processor and NIBSS records

### 2.3 Supporting Modules (recommended additions to standard practice)

- Notification Module — templated email/SMS/push dispatch for transactional and security events, decoupled via the Kafka event backbone.

- Reporting & Analytics Module — read-optimized query layer (materialized views or a reporting replica) backing the admin portal's analytics and exportable statements.

- Audit & Activity Log Module — central write path for UserChangeLog/activity logs, request-id correlation, and structured audit trails (see companion Security & Deployment Deep-Dive).

- Reference-Data Module — countries, states/LGAs/wards, banks, currencies — shared, cached, rarely-written lookup data consumed by other modules.

## 3. High-Level Architecture

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

## 4. Database Schema Design

### 4.1 Schema Design Contract

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

### 4.2 Core Entities

#### Users

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

#### Business

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

#### Roles / Permissions

RBAC model. A Role is a named, business-scoped or platform-scoped bundle of Permissions.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| Role.id / name / businessId (nullable for platform roles) | — | businessId null ⇒ platform-level role (e.g. SUPERADMIN) |
| Permission.id / name / resource / action | — | Fine-grained, e.g. resource=transactions, action=read\|write |
| RolePermission (join) | — | Many-to-many between Role and Permission |
| UserRole (join) | — | Many-to-many between Users and Role |

#### Countries

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

#### States / AdminDivisions

Nigeria's administrative hierarchy is modelled 3 levels deep (state, LGA, ward); other countries reuse the same table with their own hierarchy depth.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| countryId | UUID (FK → Countries) |  |
| name | String |  |
| level | Integer | 1 = State, 2 = Local Government Area, 3 = Ward |
| parentId | UUID (FK → self, nullable) | Self-referencing hierarchy |

#### Banks

Reference data for settlement bank selection.

|           |          |               |
|-----------|----------|---------------|
| **Field** | **Type** | **Notes**     |
| id        | UUID     | Primary key   |
| name      | String   |               |
| code      | String   | CBN bank code |
| createdAt | —        |               |

#### Address

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

#### VirtualAccount

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
| status, createdAt, updatedAt | — | Inherited base fields |

#### SettlementAccount

The bank account a virtual account's funds settle to.

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| virtualAccountId | UUID (FK → VirtualAccount) |  |
| bankAccountId | UUID (FK → BankAccount) |  |
| splitPercentage | Decimal | Supports FR-2 percentage-based splits when multiple settlement accounts exist per virtual account |
| createdAt, updatedAt | — |  |

#### BankAccount

External bank account details.

|             |                   |                                 |
|-------------|-------------------|---------------------------------|
| **Field**   | **Type**          | **Notes**                       |
| id          | UUID              | Primary key                     |
| bankId      | UUID (FK → Banks) |                                 |
| accountNo   | String            |                                 |
| accountName | String            | Resolved via NIBSS name-enquiry |
| bankCode    | String            | Denormalized for fast lookup    |

#### KYC

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

#### Avatar

Profile image reference.

|           |                   |                                  |
|-----------|-------------------|----------------------------------|
| **Field** | **Type**          | **Notes**                        |
| id        | UUID              | Primary key                      |
| userId    | UUID (FK → Users) |                                  |
| userType  | Enum              | Denormalized for quick filtering |
| imageUrl  | String            |                                  |

#### PaymentProcessor

Onboarded processor integrations (FR-6).

|  |  |  |
|----|----|----|
| **Field** | **Type** | **Notes** |
| id | UUID | Primary key |
| name | String | e.g. Interswitch, Paystack, Flutterwave |
| createdBy | UUID (FK → Users) | Restricted to Supply Admin role |
| status | Enum | ACTIVE / INACTIVE — governs whether new transactions may route through it |

#### Transactions

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

#### UserChangeLog / ActivityLog

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

## 5. Entity Relationship Overview

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
