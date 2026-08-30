# reference-data

## What it owns

Static-ish lookup data other modules need but don't own: countries, Nigeria's
administrative hierarchy (states/LGAs/wards), and banks. Other modules never
take a JPA relation across the module boundary into these tables - they store
a bare UUID (`bankId`, `countryId`, ...) and validate it against this
module's repositories at write time. This convention is spelled out in
`payments/.../bankaccount/BankAccount.java`'s javadoc and followed by
`VirtualAccount`/`Transaction` as well.

Everything here is read-mostly: there's no create/update/delete endpoint for
any of these three entities. Rows only change via Flyway migrations (see
"Seed data pipeline" below).

## Key classes

| Class | Purpose | Path |
|---|---|---|
| `Country` | ISO3 country + currency + flag URL | `entity/Country.java` |
| `AdminDivision` | Self-referencing 3-level hierarchy (State/LGA/Ward); JPA-mapped to the `states` table | `entity/AdminDivision.java` |
| `Bank` | Name + unique code (currently populated from Paystack's own bank-code list) | `entity/Bank.java` |
| `CountryRepository`, `AdminDivisionRepository`, `BankRepository` | Plain `JpaRepository`s; `AdminDivisionRepository` adds `findByCountryId`, `findByCountryIdAndLevel`, `findByParentId` | `repository/` |
| `ReferenceDataService` | The only service in the module; thin read-only pass-through to the three repositories | `service/ReferenceDataService.java` |
| `CountryController`, `AdminDivisionController`, `BankController` | REST controllers, one per entity | `controller/` |

## Endpoints

All endpoints in this module require an authenticated caller (any valid
JWT) but no specific permission - `SecurityConfig` gates access coarsely
(`permitAll` vs `authenticated()`), and none of these routes appear in its
`permitAll` list, so there's no `@PreAuthorize` on any of these controllers.

| Method & path | Purpose |
|---|---|
| `GET /api/v1/countries` | List all countries |
| `GET /api/v1/countries/{id}` | Get one country (404 via `ResourceNotFoundException` if missing) |
| `GET /api/v1/countries/{countryId}/states?level=` | List admin divisions for a country, optionally filtered to one level (1=State, 2=LGA, 3=Ward) |
| `GET /api/v1/states/{parentId}/children` | List the direct children of one admin division (e.g. the LGAs under a State) |
| `GET /api/v1/banks` | List all banks |

## Seed data pipeline

`reference-data/src/main/resources/seed-data/*.json` fixtures are the source
of truth; `scripts/generate-seed-migrations.py` (repo root) turns them into
numbered Flyway migrations under `app/src/main/resources/db/migration/`.
Flyway migration history is immutable once applied, so re-running the
generator after editing a fixture appends *new* migration files - it never
rewrites `V0027`-`V0030`.

| Fixture | Rows | Source |
|---|---|---|
| `countries.json` | 246 | [mledoze/countries](https://github.com/mledoze/countries) (public domain) |
| `nigeria-states.json` | 37 (36 states + FCT), each with a `capital` | Wikipedia, "States of Nigeria" |
| `nigeria-lgas.json` | 774 | [xosasx/nigerian-local-government-areas](https://github.com/xosasx/nigerian-local-government-areas) - the official NPC/INEC count; cross-checked against a second source that came in 5 short before this one was picked |
| `nigeria-banks.json` | 276 | Paystack's live `GET /bank` list (`api.paystack.co/bank`), **not** a generic bank-code dataset - deliberately kept in sync with what `payments`' `BankVerificationGateway` actually calls against, so a `bankId` that resolves here is guaranteed resolvable there too |

Resulting migrations: `V0026` adds the `capital` column to `states` (it
didn't exist before), `V0027` seeds countries, `V0028` seeds the 37 states,
`V0029` seeds the 774 LGAs (parented to their state via a `JOIN` on state
name - must run after `V0028`), `V0030` seeds banks.

## How to extend it

- **Add/update a country**: edit `seed-data/countries.json`, rerun
  `python3 scripts/generate-seed-migrations.py`, review the new migration.
- **Refresh the bank list** (e.g. Paystack adds a bank): re-fetch
  `GET https://api.paystack.co/bank?country=nigeria&perPage=200` (no auth
  needed for this endpoint), regenerate `nigeria-banks.json` in the same
  shape (`{"name": ..., "code": ...}`, active + non-deleted only), rerun the
  generator script.
- **Add ward-level (level 3) data**: not seeded at all currently - only
  levels 1 (State) and 2 (LGA) exist in the DB. `AdminDivision.LEVEL_WARD`
  is defined and the schema supports it, but no dataset has been sourced/
  verified for it yet (~8,800+ wards, no single authoritative public source
  found so far).
- **Add a new country's admin hierarchy**: `AdminDivision` is explicitly
  designed to be reused by other countries at their own hierarchy depth -
  same table, filter by `country_id`.

## Gotchas / non-obvious behavior

- States, LGAs, *and* (eventually) Wards all live in one `states` table via
  self-referencing `parent_id` + a `level` integer, not three separate
  tables - `AdminDivisionRepository.findByParentId` is how you walk down a
  level regardless of which level you're at.
- The countries and banks seed migrations use `ON CONFLICT (iso3) DO NOTHING`
  / `ON CONFLICT (code) DO NOTHING` specifically because Nigeria already
  exists from `V0001` (seeded inline in that migration, before this seed
  pipeline existed), and a local dev DB can carry a stray manually-inserted
  test bank row from earlier ad hoc testing - the seed migrations are
  written to be safe to apply alongside either.
- `BankRepository`/`CountryRepository` have no query methods beyond
  `JpaRepository` defaults - `ReferenceDataService.listCountries()`/
  `listBanks()` really do return every row, no filtering.

## Depends on / depended on by

Depends on `common-core` only (for `BaseEntity`, exceptions). Per the
README's build order (`common-core` → `reference-data` → `payments` →
`onboarding-auth-rbac` → `app`), both `payments` (bank accounts, virtual
accounts) and `onboarding-auth-rbac` depend on it, validating bare UUIDs
against its repositories rather than taking a JPA relation.
