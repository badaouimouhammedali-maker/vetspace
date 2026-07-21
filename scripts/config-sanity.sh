#!/usr/bin/env bash
#
# Prod-parity checks that CI can run on every push — the parts of a deployment that
# nothing else exercises:
#
#   1. the placeholder guard actually fails on a placeholder (both directions);
#   2. the production Dockerfile builds;
#   3. the built image boots against a real Postgres with a prod-like environment and
#      reports /actuator/health UP;
#   4. the same image REFUSES to boot without JWT_SECRET, and exits non-zero.
#
# (4) is the one that matters most. The fail-fast validator has unit coverage, but the
# claim being made is "the real image, as deployed, will not start unsafely" — and that
# spans the entrypoint, the prod profile and the container's exit code, none of which a
# Spring context test touches.
#
# Usage:  scripts/config-sanity.sh            (uses a throwaway postgres container)
#         DB_HOST=… DB_PORT=… scripts/config-sanity.sh   (uses an existing one, e.g. a
#                                                         GitHub Actions service)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

IMAGE=vetspace-backend:config-sanity
APP_CONTAINER=vetspace-config-sanity-app
DB_CONTAINER=vetspace-config-sanity-db
API_PORT="${API_PORT:-18081}"

# Own network so the app can reach the database by name whether we started it or not.
NETWORK=vetspace-config-sanity-net
OWN_DB=0

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
step()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail()  { red "✗ $*"; exit 1; }
pass()  { green "✓ $*"; }

cleanup() {
  docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
  if [[ "$OWN_DB" == "1" ]]; then
    docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
    docker network rm "$NETWORK" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 1. Placeholder guard — proven in BOTH directions
# ---------------------------------------------------------------------------
# Asserting only that the check passes would also pass if the check were broken, and
# asserting only that it fails would leave main permanently red (vercel.json ships a
# deliberate REPLACE-ME until the Railway domain is known). So: prove it rejects the
# placeholder, then prove it accepts a configured file.
step "Placeholder guard"
cd "$ROOT/frontend"

npm run build --silent >/dev/null

if npm run predeploy --silent >/dev/null 2>&1; then
  fail "predeploy check PASSED with REPLACE-ME still in vercel.json — the guard is not working"
fi
pass "guard rejects the unconfigured vercel.json"

# Same check against a configured copy. Restored on exit even if this fails.
cp vercel.json /tmp/vercel.json.orig
restore_vercel() { cp /tmp/vercel.json.orig "$ROOT/frontend/vercel.json" 2>/dev/null || true; }
trap 'restore_vercel; cleanup' EXIT

sed -i.bak 's/REPLACE-ME\.up\.railway\.app/api.vetspace.example/' vercel.json && rm -f vercel.json.bak
if ! npm run predeploy --silent; then
  fail "predeploy check FAILED on a configured vercel.json — the guard has a false positive"
fi
pass "guard accepts a configured vercel.json, and dist/ is free of placeholders and local addresses"
restore_vercel

# ---------------------------------------------------------------------------
# 2. The production image builds
# ---------------------------------------------------------------------------
step "Docker build"
docker build -q -t "$IMAGE" "$ROOT/backend" >/dev/null
pass "backend/Dockerfile builds"

# ---------------------------------------------------------------------------
# Database: reuse an injected one (CI service) or start a throwaway.
# ---------------------------------------------------------------------------
if [[ -n "${DB_HOST:-}" ]]; then
  # An injected database (a GitHub Actions `services:` block) is published on the
  # runner's localhost, which is NOT the container's localhost. `--network host` would
  # work on Linux and quietly not on macOS, so the branch CI takes would be the one
  # nobody can test locally. host-gateway resolves to the host on both.
  host="$DB_HOST"
  if [[ "$host" == "localhost" || "$host" == "127.0.0.1" ]]; then
    host="host.docker.internal"
  fi
  DB_URL="jdbc:postgresql://${host}:${DB_PORT:-5432}/${DB_NAME:-vetspace}"
  NET_ARGS=(--add-host "host.docker.internal:host-gateway")
  step "Using injected Postgres at ${DB_HOST}:${DB_PORT:-5432} (as ${host} from the container)"

  # Refuse to run against a database that already holds data.
  #
  # This exists because it happened: a local run meant to simulate CI pointed DB_HOST at
  # localhost:5432, the throwaway container failed to bind that port because a real
  # database was already on it, and the prod image booted against a developer's dev
  # database and ran Flyway on it. In CI the target is an empty service container and
  # this check costs nothing; anywhere else, migrating a database you did not mean to
  # touch is not something to discover afterwards.
  existing=$(PGPASSWORD="${DB_PASSWORD:-vetspace}" docker run --rm -i \
    --add-host "host.docker.internal:host-gateway" \
    -e PGPASSWORD="${DB_PASSWORD:-vetspace}" postgres:16 \
    psql -h "$host" -p "${DB_PORT:-5432}" -U "${DB_USER:-vetspace}" -d "${DB_NAME:-vetspace}" \
      -tAc "select coalesce((select count(*) from users), 0)" 2>/dev/null | tr -d '[:space:]' || echo 0)

  if [[ "${existing:-0}" =~ ^[0-9]+$ ]] && [[ "${existing:-0}" -gt 0 ]] \
     && [[ "${ALLOW_NON_EMPTY_DB:-0}" != "1" ]]; then
    fail "$(cat <<MSG
refusing to run: ${DB_HOST}:${DB_PORT:-5432}/${DB_NAME:-vetspace} already contains ${existing} users.
      This script boots the prod image against it, which runs Flyway and migrates the schema.
      CI points DB_HOST at an empty service container; locally, run it with no DB_HOST at all
      and it will start its own throwaway Postgres. Set ALLOW_NON_EMPTY_DB=1 to override.
MSG
)"
  fi
  pass "target database is empty"
else
  step "Starting a throwaway Postgres"
  OWN_DB=1
  docker network create "$NETWORK" >/dev/null 2>&1 || true
  docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$DB_CONTAINER" --network "$NETWORK" \
    -e POSTGRES_DB=vetspace -e POSTGRES_USER=vetspace -e POSTGRES_PASSWORD=vetspace \
    postgres:16 >/dev/null
  for _ in $(seq 1 40); do
    docker exec "$DB_CONTAINER" pg_isready -U vetspace -d vetspace >/dev/null 2>&1 && break
    sleep 2
  done
  docker exec "$DB_CONTAINER" pg_isready -U vetspace -d vetspace >/dev/null 2>&1 \
    || fail "throwaway Postgres never became ready"
  DB_URL="jdbc:postgresql://${DB_CONTAINER}:5432/vetspace"
  NET_ARGS=(--network "$NETWORK")
  pass "Postgres ready"
fi

# ---------------------------------------------------------------------------
# A prod-like environment that satisfies every fail-fast rule.
# ---------------------------------------------------------------------------
# Generated per run, never committed: 48 random bytes, comfortably over the 32-byte
# minimum and not one of the known-default values the validator rejects.
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"

prod_env=(
  -e SPRING_PROFILES_ACTIVE=prod
  -e DB_URL="$DB_URL"
  -e DB_USER="${DB_USER:-vetspace}"
  -e DB_PASSWORD="${DB_PASSWORD:-vetspace}"
  # MAIL_MODE=log is refused in prod: resets would be printed to stdout and look sent.
  -e MAIL_MODE=smtp
  -e MAIL_HOST=smtp.invalid
  -e MAIL_PORT=587
  # A localhost origin is refused too — it means the real SPA is silently blocked.
  -e CORS_ALLOWED_ORIGINS=https://vetspace.example
  -e MEDIA_ENDPOINT=https://media.vetspace.example
  -e MEDIA_PUBLIC_BASE_URL=https://media.vetspace.example/vetspace
)

# ---------------------------------------------------------------------------
# 3. Boots with a valid prod config and reports healthy
# ---------------------------------------------------------------------------
step "Boot with a valid prod config"
docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$APP_CONTAINER" "${NET_ARGS[@]}" \
  "${prod_env[@]}" -e JWT_SECRET="$JWT_SECRET" \
  -p "${API_PORT}:8080" "$IMAGE" >/dev/null

healthy=0
for _ in $(seq 1 60); do
  if curl -sf "http://localhost:${API_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  # Exit early with real logs if the container died rather than polling for 3 minutes.
  if [[ "$(docker inspect -f '{{.State.Running}}' "$APP_CONTAINER" 2>/dev/null)" != "true" ]]; then
    docker logs "$APP_CONTAINER" 2>&1 | tail -40
    fail "container exited while waiting for health"
  fi
  sleep 3
done
[[ "$healthy" == "1" ]] || { docker logs "$APP_CONTAINER" 2>&1 | tail -40; fail "never reported UP"; }
pass "prod profile boots and /actuator/health is UP"

docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
# 4. Negative case: no JWT_SECRET must be a hard, non-zero failure
# ---------------------------------------------------------------------------
# Without this the validator could regress to a warning and nobody would notice until a
# deployment came up with a fallback signing key — at which point anyone can mint an
# admin token. Asserting on the exit code is the point: a container that logs an error
# and keeps running is exactly the failure mode we are ruling out.
step "Boot with JWT_SECRET unset (must refuse)"
set +e
docker run --name "$APP_CONTAINER" "${NET_ARGS[@]}" "${prod_env[@]}" "$IMAGE" \
  >/tmp/config-sanity-nosecret.log 2>&1
exit_code=$?
set -e

if [[ "$exit_code" -eq 0 ]]; then
  tail -30 /tmp/config-sanity-nosecret.log
  fail "container started successfully WITHOUT JWT_SECRET — fail-fast is not working"
fi
pass "container refused to start, exit code $exit_code"

if ! grep -q "JWT_SECRET is not set" /tmp/config-sanity-nosecret.log; then
  tail -30 /tmp/config-sanity-nosecret.log
  fail "refused, but not for the expected reason — the message should name JWT_SECRET"
fi
pass "refusal names JWT_SECRET and how to generate one"

# The refusal must not echo the secret or any other value back into the logs.
if grep -qi "$JWT_SECRET" /tmp/config-sanity-nosecret.log; then
  fail "the log contains a secret value"
fi
pass "no secret values in the failure output"

printf '\n'
green "config-sanity: all checks passed"
