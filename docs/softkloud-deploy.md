# Deploying to the softkloud server

The softkloud box already runs a shared stack (`/opt/stack`): Postgres and
Redis as containers on a Docker network called `nawill-net`, plus Caddy for
TLS + reverse-proxying every app's subdomain. Every app deployed here
(`spend-wise`, `app.nawill.ng`, and now `napayment`) joins that same network
rather than running its own Postgres/Redis or exposing ports on the host
directly - mirrors `app.nawill.ng`'s deploy setup exactly (see that repo's
`docs/TECHNICAL.md` §8 for the canonical write-up this one follows).

CI (`.github/workflows/deploy-dev.yml`, calling the reusable
`_deploy.yml`) redeploys the `dev` environment automatically on every push
to the `dev` branch. Nothing in this file happens automatically - each step
below is one-time, by hand.

## 1. One-time server-side setup

On the server, as the `nawilltech` user:

**Database.** A dedicated, scoped Postgres role + database (not the shared
`postgres` superuser - same pattern as the existing `spendwise` role):

```bash
docker exec postgres psql -U postgres \
  -c "CREATE ROLE nawill_pay WITH LOGIN PASSWORD '<a strong password>';" \
  -c "CREATE DATABASE nawill_pay OWNER nawill_pay;"
```

The app runs its own Flyway migrations on startup against this database -
it just needs to exist and be reachable, not pre-populated with schema.

**App checkout.** `git clone https://github.com/nawilltech/napayment.git
/opt/apps/napayment` (mirrors `/opt/apps/spend-wise`, `/opt/apps/app.nawill.ng`).
Requires the server's cached GitHub credential to have read access to this
repo - if `git fetch` fails with a credential error, that credential (a
fine-grained PAT, most likely) needs `napayment` added to its repo
allowlist.

**Caddy site block**, added to `/opt/stack/Caddyfile`:

```
api.dev.napayment.nawill.ng {
	reverse_proxy napayment-api:8080
}
```

Validate before reloading (`docker exec caddy caddy validate --config
/etc/caddy/Caddyfile`), then `docker exec caddy caddy reload --config
/etc/caddy/Caddyfile` - a graceful reload, not a restart, so other sites on
the same Caddy instance are never dropped.

## 2. One-time GitHub repo setup

Generate a dedicated deploy keypair (don't reuse another app's key, so each
is independently revocable):

```bash
ssh-keygen -t ed25519 -C "napayment-ci-deploy-dev" -f ./napayment-deploy-key -N ""
```

Add the **public** key to the server's `~/.ssh/authorized_keys` for
`nawilltech`. Note: this is a full, unrestricted key (matching
`app.nawill.ng`'s convention, not `spend-wise`'s forced-command one) -
`_deploy.yml` rewrites `.env` from a GitHub secret on every deploy, which a
forced single-command restriction can't accommodate. `nawilltech` has
passwordless sudo, so treat this key with the same care as root access.

Create a GitHub **Environment** named `dev` (Settings → Environments → New
environment), and add these as environment secrets (not repo-wide
secrets - keeping `prod`'s eventual secrets out of reach of a `dev` run):

| Secret | Value |
|---|---|
| `SSH_HOST` | the server's hostname or IP |
| `SSH_USER` | `nawilltech` |
| `SSH_KEY` | the **private** half of the keypair generated above |
| `APP_PATH` | `/opt/apps/napayment` |
| `API_ENV_FILE` | full contents of the app's `.env` for this environment (see below) - pasted in as one multi-line secret, not enumerated key-by-key |

Delete the local private key file once it's in GitHub.

### `API_ENV_FILE` contents

Every key the app's `application.yml` binds to an env var:

```env
NAWILL_DB_URL=jdbc:postgresql://postgres:5432/nawill_pay
NAWILL_DB_USERNAME=nawill_pay
NAWILL_DB_PASSWORD=<the role's password from step 1>
NAWILL_REDIS_HOST=redis
NAWILL_REDIS_PORT=6379
NAWILL_REDIS_PASSWORD=<the shared stack's Redis password>

SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_USER=<smtp user>
SMTP_PASSWORD=<smtp password>
SMTP_FROM=noreply@nawill.ng

FRONTEND_BASEURL=https://dev.napayment.nawill.ng

AUTH_JWT_SECRET=<a long random string>
AUTH_SUPERADMIN_EMAIL=nawilltechltd@gmail.com
AUTH_SUPERADMIN_PASSWORD=<a strong password - only used the very first boot to seed the account>

NAWILL_ENCRYPTION_KEY=<base64-encoded 32 random bytes - see EncryptionService, AES-256>
PAYSTACK_TEST_PRIVATE_KEY=<optional - if BVN/bank-verification is enabled>

SERVER_PORT=8080
```

`postgres` and `redis` above are the shared stack's container names on
`nawill-net`, not `localhost` - this app never talks to a bare-metal
Postgres/Redis on this box, unlike what an earlier draft of this doc
assumed.

## 3. What happens on every push to `dev`

1. `verify` (`ci.yml`) - full `mvn clean verify`. This already gated the PR
   that landed the push; `deploy-dev.yml` doesn't re-run it.
2. `deploy-dev.yml` calls the reusable `_deploy.yml`, which SSHes in and:
   - `git fetch` + `git reset --hard origin/dev` in `/opt/apps/napayment`.
   - Rewrites `.env` from the `API_ENV_FILE` secret.
   - `docker compose -f docker-compose.prod.yml up -d --build`, then prunes
     dangling images.

`docker-compose.prod.yml` builds the existing multi-stage `Dockerfile`
(compiles the whole Maven reactor, ships only
`app/target/nawill-pay-app-exec.jar`) and starts it as `napayment-api` on
`nawill-net`, with no ports published to the host - Caddy reaches it by
container name.

## 4. Adding `prod` later

Add a `prod` GitHub Environment with the same secret shape (pointing
`APP_PATH` at a separate checkout, e.g. `/opt/apps/napayment-prod`, and a
production `API_ENV_FILE`), a `deploy-prod.yml` triggering on push to
`main` and calling `_deploy.yml` with `environment_name: prod, branch:
main`, and a Caddy block for the prod domain. `_deploy.yml` itself needs no
changes - same pattern as `app.nawill.ng`'s `deploy-prod.yml`.

## Not covered yet

- **No rollback automation.** `git reset --hard` to a previous commit SHA
  on the server, then re-run `docker compose -f docker-compose.prod.yml up
  -d --build` by hand, is the manual fallback.
- **Single instance, brief downtime on deploy.** `--build` recreates the
  container in place; there's a short gap while the new image builds and
  starts, not a zero-downtime rolling deploy.
