#!/usr/bin/env bash
# Post-deploy verification against the LIVE deployment (GATE E).
#
#   bash scripts/verify-live.sh https://vetspace.vercel.app
#
# Optionally check the refresh-cookie flags too, which needs a real login:
#   LIVE_EMAIL=you@example.com LIVE_PASSWORD='...' bash scripts/verify-live.sh https://vetspace.vercel.app
#
# Two GATE E items are deliberately NOT here, because they cannot be checked
# from outside honestly:
#   * the student journey — the captcha is enabled in production (as it must be),
#     so registration cannot be automated. Walk it in a browser; see docs/deploy.md §8.
#   * SEED_ADMIN removed — that is the state of your Railway dashboard, not
#     something the running app reports. Confirm it there.
set -uo pipefail

SPA="${1:-}"
if [[ -z "$SPA" ]]; then
  echo "usage: bash scripts/verify-live.sh https://your-spa-domain [https://your-api-domain]" >&2
  exit 2
fi
SPA="${SPA%/}"
# With the Vercel proxy the API is same-origin; pass a second argument only if
# you dropped the proxy and serve the API from its own domain.
API="${2:-$SPA}"
API="${API%/}"

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
warn() { printf '  \033[33mWARN\033[0m  %s\n' "$1"; }
FAILURES=0

echo "==> TLS and reachability"
[[ "$SPA" == https://* ]] && pass "SPA is HTTPS" || fail "SPA is not HTTPS: $SPA"
[[ "$API" == https://* ]] && pass "API is HTTPS" || fail "API is not HTTPS: $API"

code=$(curl -s -o /dev/null -w '%{http_code}' "$SPA")
[[ "$code" == "200" ]] && pass "SPA responds 200" || fail "SPA returned $code"

# http:// must not serve content in the clear.
insecure=$(curl -s -o /dev/null -w '%{http_code}' "http://${SPA#https://}" || echo 000)
[[ "$insecure" =~ ^(301|302|307|308|000)$ ]] && pass "plain HTTP redirects or refuses ($insecure)" \
  || warn "plain HTTP returned $insecure — expected a redirect to HTTPS"

echo "==> API health"
health=$(curl -s "$API/actuator/health")
[[ "$health" == *'"status":"UP"'* ]] && pass "health is UP" || fail "health not UP: ${health:0:120}"
[[ "$health" != *'"components"'* ]] && pass "health hides component details" \
  || fail "health leaks internals — show-details is not 'never'"

echo "==> Nothing sensitive is exposed"
for path in /swagger-ui/index.html /v3/api-docs /actuator/env /actuator/beans /actuator/heapdump /actuator/metrics; do
  c=$(curl -s -o /dev/null -w '%{http_code}' "$API$path")
  [[ "$c" != "200" ]] && pass "$path not exposed ($c)" || fail "$path is PUBLIC (200)"
done

echo "==> CORS allowlist"
foreign=$(curl -s -o /dev/null -D - -X OPTIONS "$API/api/auth/login" \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' 2>/dev/null \
  | grep -i '^access-control-allow-origin' | tr -d '\r' || true)
[[ -z "$foreign" ]] && pass "foreign origin gets no CORS grant" \
  || fail "foreign origin was granted CORS: $foreign"

echo "==> Error shape (no stack traces)"
body=$(curl -s -X POST "$API/api/auth/login" -H 'Content-Type: application/json' -d '{"bad":"payload"}')
[[ "$body" != *"at com.vetspace"* && "$body" != *"Exception"* ]] \
  && pass "errors carry no stack trace" || fail "error body leaks internals: ${body:0:160}"

echo "==> No secrets in the shipped bundle"
asset=$(curl -s "$SPA" | grep -oE '/assets/index-[A-Za-z0-9_-]+\.js' | head -1)
if [[ -n "$asset" ]]; then
  if curl -s "$SPA$asset" | grep -qiE "BEGIN [A-Z ]*PRIVATE KEY|aws_secret|service_role|sk_live_"; then
    fail "possible secret material in $asset"
  else
    pass "no secret material in the main bundle"
  fi
else
  warn "could not locate the main bundle to scan"
fi

echo "==> Refresh cookie flags"
if [[ -n "${LIVE_EMAIL:-}" && -n "${LIVE_PASSWORD:-}" ]]; then
  hdrs=$(mktemp)
  curl -s -o /dev/null -D "$hdrs" -X POST "$SPA/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$LIVE_EMAIL\",\"password\":\"$LIVE_PASSWORD\"}"
  cookie=$(grep -i '^set-cookie: refresh_token' "$hdrs" | tr -d '\r' || true)
  rm -f "$hdrs"
  if [[ -z "$cookie" ]]; then
    fail "no refresh cookie issued — check credentials, or the captcha rejected the request"
  else
    for flag in HttpOnly Secure 'Path=/api/auth'; do
      [[ "$cookie" == *"$flag"* ]] && pass "cookie has $flag" || fail "cookie missing $flag"
    done
    # Which SameSite is correct depends on the topology (docs/deploy.md §3):
    # proxied same-origin => Lax; SPA calling another domain directly => None.
    if [[ "$cookie" == *"SameSite=Lax"* ]]; then
      pass "cookie is SameSite=Lax — first-party via the /api proxy (Safari-safe)"
    elif [[ "$cookie" == *"SameSite=None"* ]]; then
      warn "cookie is SameSite=None — the SPA is calling the API cross-domain."
      warn "  That is a THIRD-PARTY cookie: Safari blocks it, so Safari users are"
      warn "  logged out on every page reload. Restore the /api rewrite in vercel.json,"
      warn "  or move both onto one registrable domain. See docs/deploy.md §3."
    else
      fail "cookie has no recognised SameSite: $cookie"
    fi
  fi
else
  warn "set LIVE_EMAIL and LIVE_PASSWORD to check cookie flags (skipped)"
fi

echo
if [[ "$FAILURES" -eq 0 ]]; then
  echo "live checks: ALL PASSED"
  echo "Still to confirm by hand: the student journey in a browser, and that"
  echo "SEED_ADMIN / ADMIN_PASSWORD / ADMIN_EMAIL are deleted from Railway."
else
  echo "live checks: $FAILURES FAILED"
  exit 1
fi
