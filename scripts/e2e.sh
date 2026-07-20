#!/usr/bin/env bash
# Brings up the seeded e2e stack, runs the Playwright smoke, tears down.
set -euo pipefail
cd "$(dirname "$0")/.."

# Its own compose FILE, not a profile in docker-compose.yml. `down -v` deletes every
# volume in the project it runs against, and a profile in the shared file could not be
# isolated: container_name is global, and profile-less services start on every
# invocation, so an e2e run collided with the dev containers by name. See the header of
# e2e.compose.yml.
# Absolute path, resolved once: the script cd's into e2e/ before Playwright, and a
# relative -f path then fails to resolve — which made the EXIT trap's teardown a silent
# no-op (swallowed by `|| true`), leaving containers and volumes behind after every run.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE=(docker compose -f "$SCRIPT_DIR/e2e.compose.yml")

# Overridable: a developer running the backend locally already owns 8080, and the e2e
# stack should not require them to stop it.
#   E2E_API_PORT=18080 E2E_WEB_PORT=18088 bash scripts/e2e.sh
export E2E_API_PORT="${E2E_API_PORT:-8080}"
export E2E_WEB_PORT="${E2E_WEB_PORT:-8088}"
API="http://localhost:$E2E_API_PORT"
WEB="http://localhost:$E2E_WEB_PORT"

cleanup() {
  "${COMPOSE[@]}" down -v || echo "WARNING: e2e teardown failed — check for leftover containers/volumes" >&2
}
trap cleanup EXIT

echo "▶ Building + starting the seeded e2e stack (postgres, minio, mailpit, backend, frontend)…"
"${COMPOSE[@]}" up -d --build

echo "▶ Waiting for backend health…"
for _ in $(seq 1 60); do
  curl -sf "$API/actuator/health" >/dev/null 2>&1 && break || sleep 3
done
curl -sf "$API/actuator/health" >/dev/null 2>&1 || {
  echo "backend never became healthy; recent logs:" >&2
  "${COMPOSE[@]}" logs --tail=40 backend >&2
  exit 1
}

# Health is UP as soon as the port binds, but the dev seeder is an ApplicationRunner
# that executes after that — starting Playwright here races it and lands on an empty
# École dropdown. Wait for the seeded catalog itself.
echo "▶ Waiting for the seeded catalog…"
for _ in $(seq 1 45); do
  count=$(curl -s "$API/api/public/stats" 2>/dev/null \
    | grep -o '"questions":[0-9]*' | grep -o '[0-9]*' || echo 0)
  [ "${count:-0}" -gt 0 ] && break || sleep 2
done

echo "▶ Waiting for frontend…"
for _ in $(seq 1 30); do
  curl -sf "$WEB" >/dev/null 2>&1 && break || sleep 2
done

echo "▶ Running Playwright…"
cd e2e
npm ci
npx playwright install --with-deps chromium
E2E_BASE_URL="$WEB" E2E_SEED_CODE=E2E1-2345-6789-ABCD npm test
