# SSHing into the softkloud server

For when you need to look at what's actually running - container status,
logs, Caddy config, the database - rather than going through CI. This is
the same server `docs/softkloud-deploy.md` deploys to; this file is the
"look around" companion to that one.

Real host/user/key values live in `softkloud-credentials.txt` (kept
outside git, never committed) - this doc is deliberately generic so it's
safe to keep in the repo.

## 1. Connect

```bash
ssh <ssh-user>@<softkloud-host>
```

Uses your personal key (already authorized on the server - see the
credentials file), not one of the restricted CI deploy keys. This gets you
a full shell as `nawilltech`, who has passwordless sudo - treat the session
accordingly.

## 2. Orient yourself

Everything lives under `/opt`:

```
/opt/stack/            shared Postgres + Redis + Caddy (docker-compose.yml, Caddyfile)
/opt/apps/napayment/     backend checkout (git repo, deployed by CI)
/opt/apps/napayment-fe/  frontend checkout (git repo, deployed by CI)
/opt/apps/spend-wise/    unrelated app sharing this box
/opt/apps/app.nawill.ng/ unrelated app sharing this box
```

All apps' containers join one Docker network, `nawill-net` - see it with
`docker network inspect nawill-net`.

## 3. Container status & logs

```bash
docker ps                                  # everything running on the box
docker ps --filter name=napayment          # just this app's containers

docker logs napayment-api --tail 200       # last 200 lines
docker logs napayment-api --tail 200 -f    # follow live
docker logs napayment-web --tail 200 -f
```

If a container is missing entirely, check whether the last deploy actually
ran (GitHub Actions tab on the repo) rather than assuming it's a server
problem.

## 4. Health checks

```bash
curl -s http://localhost:8080/actuator/health   # only works run from inside
                                                  # a container on nawill-net,
                                                  # or via docker exec below -
                                                  # nothing is published to
                                                  # the host directly.
docker exec napayment-api curl -s http://localhost:8080/actuator/health

curl -s https://api.dev.napayment.nawill.ng/actuator/health   # from anywhere,
                                                                 # through Caddy
curl -s https://dev.napayment.nawill.ng
```

## 5. The shared database

```bash
docker exec -it postgres psql -U postgres           # superuser, sees every DB
docker exec -it postgres psql -U nawill_pay -d nawill_pay   # scoped to this app only
```

`\l` lists databases, `\dt` lists tables once connected to one.

## 6. Caddy (reverse proxy / TLS)

Config is `/opt/stack/Caddyfile`, mounted read-only into the `caddy`
container.

```bash
# After editing /opt/stack/Caddyfile by hand:
docker exec caddy caddy validate --config /etc/caddy/Caddyfile   # do this first
docker exec caddy caddy reload --config /etc/caddy/Caddyfile     # graceful,
                                                                    # doesn't
                                                                    # drop other
                                                                    # sites
```

Always `cp /opt/stack/Caddyfile /opt/stack/Caddyfile.bak.$(date +%Y%m%d%H%M%S)`
before editing it - other apps' sites live in the same file.

## 7. Manually redeploying (bypassing CI)

Each app directory is a plain git checkout - CI does exactly this, over
SSH, with a restricted or dedicated deploy key:

```bash
cd /opt/apps/napayment
git fetch origin dev
git reset --hard origin/dev
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f
```

Same for `/opt/apps/napayment-fe`. Useful for testing a fix quickly before
it's merged, or for rolling back: `git reset --hard <previous-sha>` instead
of `origin/dev`, then rebuild.

## 8. Disk / resource pressure

```bash
docker system df       # image/container/volume disk usage
df -h                  # host disk
docker stats --no-stream   # live CPU/mem per container, one-shot
```

`docker image prune -f` (already run by every deploy) usually recovers
space from stale image layers; add `docker system prune -f` for a more
thorough (but slower) sweep if disk pressure is actually a problem.
