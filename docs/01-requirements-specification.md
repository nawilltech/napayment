# NAWILL PAY

## Requirements Specification

*Functional & Non-Functional Requirements — Payment Collection & Processing Platform*

Version 0.1 (MVP Draft)

Prepared by: Nawill Technology Ltd — Engineering

Date: August 2026

Market Focus: Nigeria, expanding to Sub-Saharan Africa

## 1. Introduction

Nawill Pay is an independent payment collection and processing platform designed to serve as a payment integrator for businesses and individuals across Nigeria, with a growth path into other Sub-Saharan African markets. It sits between merchants/collectors and Nigeria's payment rails (NIBSS, commercial and microfinance banks, licensed Payment Solution Service Providers) to receive money on behalf of users, record it against virtual accounts, and settle or disburse it according to configured rules.

A defining design constraint is Nigeria's uneven network infrastructure. Nawill Pay's local communities and merchants frequently operate on poor or intermittent connectivity, so the platform is deliberately designed with resilience, graceful degradation, and offline-to-online fallback strategies for sensitive operations, rather than assuming always-on connectivity typical of platforms designed for developed markets.

### 1.1 Purpose

This document defines the functional and non-functional requirements for the Nawill Pay MVP and establishes a foundation for subsequent iterations, including multi-currency support for other African markets.

### 1.2 Product Vision

To become a leading, locally-resilient payment collector and processor for Nigeria's underserved communities and SMEs, later expanding across Sub-Saharan Africa, while maintaining bank-grade security and regulatory alignment with CBN guidelines.

### 1.3 Definitions & Abbreviations

|  |  |
|----|----|
| **Term** | **Definition** |
| NIBSS | Nigeria Inter-Bank Settlement System — the national interbank settlement infrastructure used for real-time transfers (NIP) between Nigerian banks. |
| PSSP | Payment Solution Service Provider — a CBN-licensed entity permitted to provide payment processing/switching services (e.g. Paystack, Interswitch, Flutterwave). |
| KYC / KYB | Know Your Customer / Know Your Business — identity and business verification processes required for regulatory compliance. |
| RBAC | Role-Based Access Control — permission model restricting system actions by assigned role. |
| Virtual Account | A unique, non-globally-shared account number issued to a user/business for the purpose of receiving collections, mapped internally to a ledger balance. |
| Settlement Account | The linked bank account (commercial/microfinance bank) to which a virtual account's funds are settled/disbursed. |
| Collection Account | Nawill Pay's pooled bank account, held with a partner commercial or microfinance bank, where actual NIBSS deposits land before internal ledger allocation. |
| Idempotency Key | A client- or server-generated unique key attached to a transaction request to guarantee it is processed at most once. |

### 1.4 Scope

- In scope (MVP): user & business onboarding, KYC/2FA, virtual account issuance, inbound collections, transaction fees, payment splits/settlement, payment links, payment processor integrations (Paystack, Interswitch, etc.), webhooks (inbound/outbound), reconciliation, admin & RBAC, notifications, reporting, audit logging.

- Out of scope (MVP, planned for later phases): multi-currency wallets and FX settlement, card issuing, lending/credit products, agency banking, full offline transaction queuing beyond critical-path fallback.

- Currency: MVP defaults to Naira (NGN) only, but the schema and configuration layer must be designed so that additional African currencies (e.g. GHS, KES, XOF) can be added without structural rework.

## 2. Actors & User Types

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

## 3. Functional Requirements

### 3.1 Onboarding, Authentication & Identity

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-1** | Users shall be able to sign up as an individual or as a business. On successful signup, the platform shall automatically provision a virtual account for that individual/business for the purpose of receiving collections. |
| **FR-8** | Onboarding shall include a KYC step (BVN/NIN verification for individuals, CAC/RC number verification for businesses in the Nigerian context). Two-factor authentication (2FA) shall be configurable per account (SMS OTP, authenticator app, or email OTP). |
| **FR-8a** | Transfers/collections above a configurable threshold (default: ₦50,000) shall trigger an additional, enhanced KYC/tier-upgrade process before the transaction is permitted, in line with CBN tiered-KYC guidance. |
| **FR-9** | Onboarded clients (businesses/PSSPs) shall be able to set up as partners: configure their account, or receive invites to sign up, and generate API credentials — secret key and public key pairs — plus configure a webhook URL and register for the webhook API. |
| **FR-Auth-1** | Users shall be able to onboard and authenticate with minimal friction ("without stress") and, once verified, be able to fund their wallet and transfer money within a configurable minimum transfer range. |

### 3.2 Wallets, Virtual Accounts & Collections

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-10** | The platform functions as a revenue/payment collector: money received on behalf of a user is recorded against that user's virtual account balance internally, while the actual NIBSS deposit is received into Nawill Pay's pooled collection account, held in partnership with a commercial or microfinance bank. Internal ledger balances must always reconcile against the real pooled balance. |
| **FR-2** | Users shall be able to configure settlement splits: an inbound payment can be split by percentage across multiple recipients/settlement accounts, with amounts automatically distributed and disbursed to the due settlement account(s) per the configured split. |
| **FR-12** | The minimum transferable/collectable amount shall default to ₦50 and shall be configurable per environment/business. |
| **FR-13** | The default platform currency is Naira (NGN). The currency field must be modelled to support additional currencies in future phases without a schema rewrite. |

### 3.3 Transactions, Fees & Payment Links

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-11** | Every transaction processed on the platform shall attract a configurable transaction charge based on amount tiers. Default tiers: \< ₦2,000 → ₦50; ₦2,000–₦15,000 → ₦100; ₦15,000–₦50,000 → ₦200; \> ₦50,000 → ₦300. Tier boundaries and amounts must be admin-configurable. |
| **FR-14** | The system shall be able to generate payment links, including permanent payment links and temporary (time-bound / single-use) payment links, both with configurable expiration. |
| **FR-Txn-1** | Every transaction shall be idempotency-protected via a client-supplied or system-generated idempotency key, guaranteeing a given payment intent is processed and charged at most once even under retries. |
| **FR-Txn-2** | Every transaction shall carry a transaction type (Credit / Debit), a transaction status (see §5, Schema Design), a session ID, a linked payment processor reference, and a recipient account reference. |

### 3.4 Payment Processor Integration & Reconciliation

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-6** | Payment processors (e.g. Paystack, Interswitch, Flutterwave) shall be onboarded and configured on the platform as first-class entities. Activating/configuring a processor shall be restricted to a privileged "Supply Admin" role only. |
| **FR-7** | The platform shall receive and process inbound webhooks from payment processors and perform reconciliation against internal transaction records. |
| **FR-Recon-1** | A scheduled reconciliation job shall run every 6 hours to reconcile transactions against processor and NIBSS records, flagging mismatches for admin review. |
| **FR-Recon-2** | The platform shall support outbound webhooks (notifying integrating partners of transaction events) as well as inbound webhooks (receiving processor events), and shall support requery of a transaction's true status directly from the processor when a webhook is missed or delayed. |

### 3.5 Administration & Access Control

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-3** | There shall be an admin portal/page where authorized users can view analytics such as transactions, business performance, and settlement summaries, filterable by business, date range, and transaction status. |
| **FR-4** | There shall be a well-defined RBAC model governing the admin portal. Access to any admin capability must be gated by an explicit permission, not an implicit role check. |
| **FR-5** | SUPERADMIN shall have unrestricted, seeded access by default and shall not be a self-service-creatable role. |
| **FR-5a** | A business/client account (non-superadmin) shall be able to create scoped admin/staff roles (e.g. Admin, Account Officer) under itself, each with permissions limited to its own business/account scope — never platform-wide. |

### 3.6 Notifications & Reporting

|  |  |
|----|----|
| **ID** | **Requirement** |
| **FR-Notif-1** | The platform shall notify users of key events — successful/failed transactions, debits, credits, and profile/security updates — via configurable channels (email, SMS, push, in-app). |
| **FR-Report-1** | Businesses and admins shall be able to generate reports (transaction statements, settlement reports, reconciliation reports) for a given date range, exportable in common formats (CSV/PDF). |

## 4. Non-Functional Requirements

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

*Assumption note (NFR-10): the source specification states "1000 requests per millisecond," which is not realistic for a payments platform of this scale and is assumed to be a slip for 1000 requests per second (a very healthy peak-throughput target for a national-scale collection platform). This should be confirmed with the product owner and updated once confirmed.*

## 5. Assumptions & Constraints

- Settlement to external bank accounts (NIBSS NIP) is executed through a partner commercial or microfinance bank; Nawill Pay does not hold a direct CBN settlement license at MVP stage.

- The MVP targets the Nigerian market only; the data model (countries, states, local government hierarchy, currency) is intentionally generalized so Ghana, Kenya, Francophone West Africa (XOF) and other Sub-Saharan markets can be added later.

- Nigeria's administrative hierarchy is modelled at three levels: Level 1 — State, Level 2 — Local Government Area (LGA), Level 3 — Ward, to support address capture and future geo-tiered reporting.

- KYC integrations (BVN/NIN verification, CAC lookup) are assumed to be via third-party identity verification providers operating in Nigeria.

- All monetary values are stored in the lowest currency unit (kobo) as integers to avoid floating-point rounding errors.

- All database entities carry a default status of Active/Inactive, and soft-delete (deletedAt) rather than hard-delete, to preserve audit trail integrity.

## 6. MVP Scope & Versioning Approach

Per direction to "design for MVP and show the different versions," requirements are tagged by target release below. This lets engineering sequence delivery while keeping the full target architecture visible.

|  |  |  |
|----|----|----|
| **Release** | **Theme** | **Representative Requirements** |
| v0.1 — MVP | Core collection loop | FR-1, FR-8, FR-9, FR-10, FR-11, FR-12, FR-13, FR-3, FR-4, FR-5, NFR-1 to NFR-7, NFR-9 |
| v0.2 | Splits, links & partners | FR-2, FR-14, FR-6, FR-7, FR-Recon-1, FR-Recon-2, FR-5a |
| v0.3 | Scale & resilience hardening | NFR-2 (offline fallback), NFR-10 (load), FR-Notif-1, FR-Report-1 |
| v1.0 | Multi-currency / Pan-African | Additional currencies, FX handling, country/state expansion beyond Nigeria |
