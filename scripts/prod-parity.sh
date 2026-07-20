#!/usr/bin/env bash
# Production-parity check: run the artifacts that actually ship — the backend
# Docker image on the prod profile and the production frontend bundle — with
# MinIO standing in for R2, then drive the full student journey against them.
#
# Two phases, because they need different data:
#   A. prod profile alone  -> hardening assertions (swagger/actuator dead,
#      cookie flags, health detail hidden). No content required.
#   B. dev,prod            -> the dev seeder supplies a catalog to play through;
#      prod is last so its configuration still wins. Runs the smoke journey.
#
# Deviation from real production: the captcha is disabled (it cannot be solved
# unattended). Everything else — profile, image, bundle, cross-origin SPA — matches.
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE=(docker compose -f prod-parity.compose.yml)
API=http://localhost:19080
SPA=http://localhost:19088

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
FAILURES=0

cleanup() { "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true; }
trap cleanup EXIT

wait_for_api() {
  for _ in $(seq 1 90); do
    curl -sf "$API/actuator/health" >/dev/null 2>&1 && return 0
    sleep 2
  done
  echo "backend never became healthy; recent logs:" >&2
  "${COMPOSE[@]}" logs --tail=40 backend >&2
  return 1
}

echo "==> Building production images"
cleanup
PROFILES=prod "${COMPOSE[@]}" build

echo "==> Phase A: prod profile only — hardening"
PROFILES=prod "${COMPOSE[@]}" up -d
wait_for_api

health=$(curl -s "$API/actuator/health")
[[ "$health" == *'"status":"UP"'* ]] && pass "health is UP" || fail "health not UP: $health"
# show-details: never — the DB vendor and disk paths must not be public.
[[ "$health" != *'"components"'* ]] && pass "health hides component details" \
  || fail "health leaks components: $health"

for path in /swagger-ui/index.html /v3/api-docs /actuator/env /actuator/beans /actuator/heapdump; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$API$path")
  [[ "$code" != "200" ]] && pass "$path is not exposed ($code)" || fail "$path returned 200"
done

# Log in through the SPA origin, not the API directly: nginx proxies /api here
# exactly as the Vercel rewrite does in production, so the Set-Cookie observed
# below is the one a real browser would store — first-party, on the SPA's origin.
# The admin exists only because SEED_ADMIN ran on an empty database.
login=$(mktemp)
code=$(curl -s -o "$login" -w '%{http_code}' -D "$login.h" \
  -X POST "$SPA/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@vetspace.local","password":"parity-admin-password"}')
[[ "$code" == "200" ]] && pass "SEED_ADMIN bootstrapped an admin, reachable through the /api proxy" \
  || fail "admin login through the proxy failed ($code)"

cookie=$(grep -i '^set-cookie: refresh_token' "$login.h" || true)
[[ -n "$cookie" ]] && pass "refresh cookie issued" || fail "no refresh cookie on login"
# Lax (not None) is the whole point of proxying: a first-party cookie that
# Safari will not drop. See docs/deploy.md §3.
for flag in HttpOnly Secure 'SameSite=Lax' 'Path=/api/auth'; do
  [[ "$cookie" == *"$flag"* ]] && pass "cookie has $flag" || fail "cookie missing $flag: $cookie"
done
rm -f "$login" "$login.h"

echo "==> Phase B: dev,prod — seeded catalog, full journey"
# Reset the database first. The dev seeder only populates an EMPTY database, and
# Phase A's SEED_ADMIN bootstrap leaves an admin behind — without this reset the
# catalog is never seeded and the journey fails on an empty École dropdown.
"${COMPOSE[@]}" down -v >/dev/null 2>&1
PROFILES=dev,prod "${COMPOSE[@]}" up -d
wait_for_api

spa_code=$(curl -s -o /dev/null -w '%{http_code}' "$SPA")
[[ "$spa_code" == "200" ]] && pass "production bundle is served" || fail "SPA returned $spa_code"

echo "==> Smoke journey against the production artifacts"
if (cd ../e2e && E2E_BASE_URL="$SPA" E2E_SEED_CODE=E2E1-2345-6789-ABCD CI=1 npx playwright test); then
  pass "student journey completed"
else
  fail "student journey failed"
fi

echo
if [[ "$FAILURES" -eq 0 ]]; then
  echo "prod parity: ALL CHECKS PASSED"
else
  echo "prod parity: $FAILURES CHECK(S) FAILED"
  exit 1
fi
