# payments

## What it owns

The core money-movement domain: virtual accounts (one per user/business,
FR-1), idempotency-protected transactions against them, split-percentage
settlement out to real bank accounts, a pooled collection account used for
reconciliation, business API keys for third-party collect/withdraw,
shareable payment links, short-lived dynamic/temporary virtual accounts,
and bank-account name verification. Everything that mutates a balance goes
through `TransactionService` or `SettlementService` — there is exactly one
place ledger effects happen, regardless of which controller triggered it
(JWT-authenticated, API-key-authenticated, or an anonymous payer via a
payment link).

## Key classes

**virtualaccount** — `VirtualAccount` (entity: balance, owner is either a
`userId` or `businessId`, never both); `VirtualAccountProvisioningService`
(called synchronously from onboarding-auth-rbac's signup, inside the same
transaction — FR-1); `VirtualAccountQueryService` (the single source of
truth for "resolve the caller's one virtual account", used by every
feature built on top of one); `VirtualAccountController` (list only — no
create endpoint, provisioning is signup-triggered only).

**transaction** — `Transaction` (entity with a state machine via
`transitionTo`); `TransactionService` (the ledger-effect engine —
`create()` for JWT-authenticated callers, `createForPaymentIntent()` for
anonymous payers where the virtual account id itself is the trust
boundary); `TransactionController`.

**processor** — the platform processor catalogue and routing (FR-Proc-1..4,
FR-Admin-5):
- `PaymentProcessor` — name, immutable `code` (maps to the integration and
  its env-var keys; secrets are never stored), `priority` (lower routes
  first), `defaultEnabled` ("for all businesses") and the inherited status
  as the platform-wide switch. Owns its `PaymentProcessorMethod` children.
- `PaymentProcessorMethod` — a processor's method, referencing a catalogue
  method by its code; retired (INACTIVE), never deleted.
- `BusinessPaymentProcessor` — a business's own ON/OFF for one processor.
  Only exceptions are stored: no row = follow `defaultEnabled`.
- `ProcessorRouter` — **the one place the availability rule lives**:
  `availabilityFor(businessId)` (every processor + available + why),
  `route(businessId, method)`, `requireUsable(...)`, `availableMethods(...)`.
- `PaymentProcessorService` (catalogue, platform switch, for-all-businesses;
  password-confirmed where FR-Admin-5 says so) and
  `BusinessPaymentProcessorService` (per-business set/reset). Both audit via
  `AuditRecorder`.
- `PaymentProcessorGateway` / `SandboxPaymentProcessorGateway` — the
  charge call itself; still sandboxed (always succeeds).

**paymentmethod** — the payment method catalogue (`PaymentMethod` entity,
`payment_methods` table): permanent `code` (referenced by processors'
methods and transactions, enforced by foreign keys), editable name,
description and display order, and a platform-wide ON/OFF.
`PaymentMethodService` + `AdminPaymentMethodController` provide CRUD; delete
only works while nothing references the method, otherwise deactivate.
Seeded (V0048) with TRANSFER (Bank transfer, the default), CARD, USSD,
BANK_DEBIT and QR.

**platform** — contracts payments needs from modules it can't depend on
(onboarding-auth-rbac implements them, like `TransactionPinGateway`):
`BusinessDirectory` (exists / isActive), `PasswordConfirmation`, and
`BusinessAccess` — the single guard every money-moving path calls so a
deactivated business can't receive or move money (FR-Admin-6).

**bankaccount** — `BankAccount` (a business's registered external
account); `BankAccountService` (creation now always resolves the
authoritative name via `BankVerificationService` — see below).

**bankverification** (new) — `BankVerificationGateway` (interface) /
`PaystackBankVerificationGateway` (live implementation, calls Paystack's
`GET /bank/resolve`) / `FakeBankVerificationGateway` (`test`-profile
stand-in); `BankVerificationService` (resolves `bankId` → `Bank.code` via
reference-data, then delegates to the gateway);
`BankVerificationController`.

**settlement** — `SettlementAccount` (a bank account + split percentage
attached to a virtual account); `SettlementAccountService` (create/list,
enforces the ≤100% total-split invariant); `SettlementService` (the actual
split-and-disburse engine, manual trigger or auto-settle);
`SettlementGateway`/`SandboxSettlementGateway` (same
sandboxed-for-v0.1 shape as `PaymentProcessorGateway`); `Settlement`
(one row per disbursed split).

**collectionaccount** — `CollectionAccount` (Nawill Pay's single pooled
account — a partial unique index enforces at most one `ACTIVE` row at the
DB level); `CollectionAccountService`.

**thirdparty** — `CollectController`/`WithdrawController`: thin,
API-key-authenticated (not JWT) wrappers that resolve the caller's own
virtual account and delegate straight into `TransactionService` /
`SettlementService` — no duplicated business logic.

**paymentlink** — `PaymentLink` (permanent or time-bound/single-use;
the short code IS the platform's URL shortener, nothing more general
exists); `PaymentLinkService` (business-facing create/list/delete) vs.
`PaymentLinkPayController` (public, unauthenticated payer-facing surface —
deliberately a separate controller since who's allowed to call each is
fundamentally different).

**dynamicaccount** — `DynamicVirtualAccount` (short-lived sub-account,
default 30 min TTL); `DynamicVirtualAccountService` (expiry checked
lazily at lookup/deposit time, no scheduled sweep — this codebase has no
scheduled components at all yet).

**util** — `AccountNumberGenerator` (CBN-NUBAN-style 10-digit numbers,
shared by `VirtualAccount` and `DynamicVirtualAccount` — the one place
that algorithm lives).

**transfer** (new, FR-Auth-1/FR-Auth-2) — `TransferService` (wallet-to-wallet
transfer between two `VirtualAccount`s; deliberately not routed through
`TransactionService.process()` — see doc 2 §7 ADR-13); `TransferController`
(`GET /resolve` preview + `POST` create, both `transfers:create`);
`RecipientDirectory` / `TransactionPinGateway` (interfaces *this* module
defines and depends on, implemented in `onboarding-auth-rbac` — doc 2 §7
ADR-12's cross-module gateway pattern; payments itself has no compile-time
dependency on either implementation).

## Endpoints

| Method & path | Permission | Purpose |
|---|---|---|
| `GET /api/v1/virtual-accounts` | `virtualaccounts:read` | List caller's virtual account(s) |
| `POST /api/v1/transactions` | `transactions:create` | Create a transaction (`@Idempotent`) |
| `GET /api/v1/transactions/{id}` | `transactions:read` | Fetch a transaction (ownership-checked) |
| `GET /api/v1/admin/payment-methods`, `/{id}` | `platform-processors:read` | The payment method catalogue, in display order |
| `POST /api/v1/admin/payment-methods` | `platform-processors:manage` | Add a method |
| `PATCH /api/v1/admin/payment-methods/{id}` | `platform-processors:manage` | Rename / describe / reorder |
| `POST /api/v1/admin/payment-methods/{id}/activate` \| `/deactivate` | `platform-processors:manage` + password | Platform-wide switch |
| `DELETE /api/v1/admin/payment-methods/{id}` | `platform-processors:manage` | Delete while unused (else 409 `PAYMENT_METHOD_IN_USE`) |
| `POST /api/v1/admin/payment-processors` | `platform-processors:manage` | Add a processor with its methods |
| `GET /api/v1/admin/payment-processors`, `/{id}` | `platform-processors:read` | List (by priority) / fetch |
| `PATCH /api/v1/admin/payment-processors/{id}` | `platform-processors:manage` | Rename / reprioritise |
| `PUT` / `DELETE /api/v1/admin/payment-processors/{id}/logo` | `platform-processors:manage` | Set / remove the optional logo (base64 data URL, PNG/JPEG/WebP, max 100 KB - `ProcessorLogo`) |
| `PUT` / `DELETE /api/v1/admin/payment-processors/{id}/methods/{method}` | `platform-processors:manage` | Add or re-enable / disable a method |
| `POST /api/v1/admin/payment-processors/{id}/activate` \| `/deactivate` | `platform-processors:manage` + password | Platform-wide switch |
| `POST /api/v1/admin/payment-processors/{id}/enable-for-all-businesses` \| `/disable-for-all-businesses` | `platform-processors:manage` + password | Set the default and clear every business's own setting |
| `GET /api/v1/admin/businesses/{businessId}/payment-processors` | `platform-processors:read` | Each processor's availability for one business, and why |
| `PUT` / `DELETE /api/v1/admin/businesses/{businessId}/payment-processors/{processorId}` | `platform-processors:manage` | Switch ON/OFF for one business / reset to default |
| `GET /api/v1/payment-methods` | authenticated | Methods the caller's account can accept now |
| `POST /api/v1/bank-accounts` | `settlements:manage` | Register a bank account (name resolved via Paystack) |
| `GET /api/v1/bank-accounts` | `settlements:read` | List caller's bank accounts |
| `GET /api/v1/banks/resolve-account` | `settlements:manage` | Preview a resolved account name before creating one |
| `POST /api/v1/settlement-accounts` | `settlements:manage` | Attach a bank account + split % to the caller's virtual account |
| `GET /api/v1/settlement-accounts` | `settlements:read` | List |
| `PATCH /api/v1/settlement-accounts/auto-settle` | `settlements:manage` | Toggle auto-settle |
| `POST /api/v1/settlements` | `settlements:manage` | Manually trigger a settlement |
| `POST /api/v1/collection-account` | `collection-account:manage` | Create the (singleton) pooled account |
| `GET /api/v1/collection-account` | `collection-account:manage` | Fetch it |
| `POST /api/v1/collect` | `collect:create` (API key) | Third-party collection (`@Idempotent`) |
| `POST /api/v1/withdraw` | `withdraw:create` (API key) | Third-party withdrawal (`@Idempotent`) |
| `POST /api/v1/payment-links` | `paymentlinks:manage` | Create a payment link |
| `GET /api/v1/payment-links` | `paymentlinks:read` | List |
| `DELETE /api/v1/payment-links/{id}` | `paymentlinks:manage` | Deactivate |
| `GET /api/v1/pay/{shortCode}` | *(public)* | Resolve a payment link |
| `POST /api/v1/pay/{shortCode}` | *(public)* | Pay it (`@Idempotent`) |
| `POST /api/v1/temporary-accounts` | `temporaryaccounts:manage` | Mint a dynamic account |
| `GET /api/v1/temporary-accounts` | `temporaryaccounts:manage` | List |
| `POST /api/v1/temporary-accounts/{accountNumber}/simulate-deposit` | `temporaryaccounts:manage` | Sandbox deposit simulation |
| `GET /api/v1/transfers/resolve?identifier=` | `transfers:create` | Preview the recipient (masked name) before sending |
| `POST /api/v1/transfers` | `transfers:create` | Peer-to-peer transfer (`@Idempotent`) — PIN-gated, see `onboarding-auth-rbac`'s `/auth/transaction-pin` |

`collection-account:manage` is assigned to no seeded role (see
`V0015`/`V0024` in app's migrations) — only `PermissionChecker`'s
hard-coded SUPERADMIN bypass ever satisfies it, so this endpoint is
SUPERADMIN-only in practice, not because of anything in this module.

## Idempotency

`@Idempotent` (common-core) + the `idempotency_records` table dedupe
retried requests by the `Idempotency-Key` header, returning the identical
first response instead of re-executing. Applied on every balance-mutating
entry point here: `POST /transactions`, `POST /collect`, `POST /withdraw`,
`POST /pay/{shortCode}`. See `docs/modules/common-core.md` for the
mechanism itself.

## External integrations

- **`PaymentProcessorGateway`** — sandboxed only for v0.1
  (`SandboxPaymentProcessorGateway`); no real network call. Real processor
  integrations are explicitly v0.2+ scope.
- **`SettlementGateway`** — same story: `SandboxSettlementGateway` always
  "succeeds" with a generated reference; real NIBSS NIP disbursement is
  v0.2+ (FR-Settle-2).
- **`BankVerificationGateway`** — the **first real external HTTP call in
  the codebase**. `PaystackBankVerificationGateway` calls Paystack's live
  `GET /bank/resolve` with a `Bearer` secret key
  (`nawill.paystack.secret-key`, from `PAYSTACK_TEST_PRIVATE_KEY`).
  Unconfigured, it throws `IllegalStateException` at call time rather than
  silently no-op'ing. `FakeBankVerificationGateway` (`@Profile("test")`)
  stands in during the integration suite (`AbstractIntegrationTest`
  activates `test`) so tests never depend on Paystack's sandbox being
  reachable.

## How to extend it

- **New payment method**: add it in the admin console (Configuration →
  Payment methods) - no code change. Only integrating a processor's actual
  support for it needs code.
- **Anything that moves money** must call `BusinessAccess.requireActive`
  and, if it takes a payment, go through `TransactionService` so
  `ProcessorRouter` picks (or validates) the processor - never read
  processors straight from the repository.
- **New payment processor integration**: implement `PaymentProcessorGateway`,
  wire it in place of (or alongside, profile-gated like
  `BankVerificationGateway`) `SandboxPaymentProcessorGateway`.
- **Settlement split invariant**: `SettlementAccountService.create()` locks
  the virtual account row (`findByIdForUpdate`) for the whole
  validate-then-insert so two concurrent creates for the same account can
  never both pass the sum-≤100% check before either commits. Follow this
  pattern for any new invariant that spans multiple rows under one virtual
  account.
- **Name Enquiry / `BankAccountService`**: `CreateBankAccountRequest` has
  no `accountName` field — creation always calls
  `BankVerificationService.resolve(bankId, accountNumber)` and stores
  *that* name, never a client-supplied one. Any new flow that registers an
  external bank account should do the same rather than trusting request
  input.
- **`SettlementService.settle()` runs `REQUIRES_NEW`**: when called from
  `autoSettleIfEnabled` inside the same transaction as the triggering
  credit, a settlement failure must roll back only the settlement, not the
  credit. Keep this propagation if you touch that path.
- **A new feature needing something from another module's domain**: define
  the interface here, in this module's own vocabulary (see
  `RecipientDirectory`/`TransactionPinGateway`), even if the natural
  implementer is a module this one doesn't depend on. Let the *other*
  module implement it — that direction always compiles, since
  `onboarding-auth-rbac` already depends on `payments` — and Spring's
  whole-application component scan wires the two together at runtime
  (doc 2 §7 ADR-12).

## Gotchas / non-obvious behavior

- Processor routing and the business-active check happen once, in
  `TransactionService.process()`; collect, payment links and one-time
  accounts just pass the requested method. A deactivated processor keeps
  every business's own setting so reactivating restores them, while
  "for all businesses" deliberately deletes those settings.
- Integration tests share one processor table: routing assertions must
  limit the test business to its own processors
  (`AbstractIntegrationTest.limitBusinessToProcessors`).

- `bankId`, `countryId`, etc. are bare UUIDs validated against
  reference-data's repositories at write time — never a JPA relation
  across the module boundary (see `BankAccount`'s javadoc for the
  rationale).
- Lock ordering is fixed codebase-wide: **VirtualAccount before
  CollectionAccount**, always, in both `TransactionService` and
  `SettlementService` — never reverse it, or concurrent operations can
  deadlock.
- The collection-account ledger mirror (`TransactionService.
  mirrorOnCollectionAccount`) is best-effort: if no active collection
  account exists yet, it logs a warning and continues rather than failing
  the transaction — it's a reconciliation aid, not a prerequisite.
- `SettlementService`'s last split absorbs the rounding remainder so splits
  always sum exactly to the settled sub-total, rather than a naive
  per-split percentage calculation risking an off-by-a-few-kobo total.
- `DynamicVirtualAccount` expiry is checked lazily, not swept — there is no
  scheduled/background job infrastructure in this codebase at all yet.
- `VirtualAccountProvisioningService.save()` retries on a unique-constraint
  collision by regenerating the account number (up to 5 attempts) rather
  than failing signup outright on a rare random collision.
- `Transaction.paymentProcessor` is nullable — null specifically means "a
  peer-to-peer transfer, no external processor involved" (doc 2 §7
  ADR-13). `TransactionResponse.from()` guards against this with a null
  check; don't assume every `Transaction` row has one.
- A transfer's two rows are linked by `transferGroupId`, not a foreign key
  to each other — `counterpartyAccountId` on each row names the *other*
  side directly, so either party's own transaction history can display the
  link without a join.
- Peer-to-peer transfer deliberately never touches `CollectionAccount` —
  see ADR-13. Don't route a new balance-mutating feature through
  `TransactionService.process()` (or copy its `mirrorOnCollectionAccount`
  call) unless the money genuinely crosses the real pooled bank account's
  boundary.

## Depends on / depended on by

Depends on `common-core` and `reference-data`. `onboarding-auth-rbac` and
`app` depend on it (build order: `common-core` → `reference-data` →
`payments` → `onboarding-auth-rbac` → `app`, per the root `README.md`).
