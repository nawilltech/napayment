#!/usr/bin/env bash
# Waits for the natively-installed (non-Docker) Postgres and Redis used by
# `.env` to accept connections before you run the app against them.
#
# Run before `./mvnw -f app/pom.xml spring-boot:run ...`. Not used in CI —
# CI/Testcontainers manage their own containers.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

# Pull host/port out of .env if present, else fall back to native defaults.
DB_HOST="localhost"
DB_PORT="5432"
REDIS_HOST="localhost"
REDIS_PORT="6379"

if [[ -f "$ENV_FILE" ]]; then
  db_url="$(grep -E '^NAWILL_DB_URL=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
  if [[ "$db_url" =~ jdbc:postgresql://([^:/]+):([0-9]+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
  fi
  env_redis_host="$(grep -E '^NAWILL_REDIS_HOST=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
  env_redis_port="$(grep -E '^NAWILL_REDIS_PORT=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
  [[ -n "$env_redis_host" ]] && REDIS_HOST="$env_redis_host"
  [[ -n "$env_redis_port" ]] && REDIS_PORT="$env_redis_port"
fi

TIMEOUT_SECONDS="${WAIT_FOR_SERVICES_TIMEOUT:-20}"

wait_for() {
  local name="$1" check_cmd="$2" restart_hint="$3"
  local waited=0
  until eval "$check_cmd" >/dev/null 2>&1; do
    if (( waited >= TIMEOUT_SECONDS )); then
      echo "✗ $name not reachable after ${TIMEOUT_SECONDS}s." >&2
      echo "  Tried: $check_cmd" >&2
      echo "  Try: $restart_hint" >&2
      return 1
    fi
    sleep 1
    waited=$((waited + 1))
  done
  echo "✓ $name is up (waited ${waited}s)"
}

wait_for "Postgres ($DB_HOST:$DB_PORT)" \
  "pg_isready -h '$DB_HOST' -p '$DB_PORT'" \
  "brew services restart postgresql@14"

wait_for "Redis ($REDIS_HOST:$REDIS_PORT)" \
  "redis-cli -h '$REDIS_HOST' -p '$REDIS_PORT' ping" \
  "brew services restart redis"
