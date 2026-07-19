#!/usr/bin/env bash
# Brings up the seeded e2e stack, runs the Playwright smoke, tears down.
set -euo pipefail
cd "$(dirname "$0")/.."

cleanup() { docker compose --profile e2e down -v || true; }
trap cleanup EXIT

echo "▶ Building + starting the seeded e2e stack (postgres, minio, mailpit, backend, frontend)…"
docker compose --profile e2e up -d --build

echo "▶ Waiting for backend health…"
for _ in $(seq 1 60); do
  curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1 && break || sleep 3
done
echo "▶ Waiting for frontend…"
for _ in $(seq 1 30); do
  curl -sf http://localhost:8088 >/dev/null 2>&1 && break || sleep 2
done

echo "▶ Running Playwright…"
cd e2e
npm ci
npx playwright install --with-deps chromium
E2E_BASE_URL=http://localhost:8088 E2E_SEED_CODE=E2E1-2345-6789-ABCD npm test
