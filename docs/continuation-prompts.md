# VetSpace — Continuation Prompts (remaining phases)

State when written: Prompts 0–12 done, `./mvnw verify` 82/82 green, frontend
lint+build clean. Missing: hardening, landing page, tests/CI/README,
deployment. Run in order, one per Claude Code session, gate before advancing.

## Prompt A — Housekeeping (do first, 5 min)

```
Read CLAUDE.md. Small cleanup pass:
1. Review the 24 uncommitted changes (notification WIP). Finish or revert
   them so the tree is clean; run ./mvnw verify; commit as its own commit.
2. From now on: one commit per completed phase.
```

**GATE A**: clean `git status` · verify green.

## Prompt B — Security hardening & audit (was Prompt 14)

```
Read CLAUDE.md. Hardening pass on the existing code. Output a table
check | result | action for every item.

Backend:
1. Add a security headers filter on all responses: X-Content-Type-Options
   nosniff, X-Frame-Options DENY, Referrer-Policy
   strict-origin-when-cross-origin; Cache-Control no-store on /api/auth/**,
   /api/users/me, /api/stats/**. Test asserting headers present.
2. Gate the swagger permitAll matchers to the dev profile only (springdoc
   is already disabled in prod config, but the route rules should match).
3. Default-deny probe: 8 random endpoints tokenless → 401; student on 3
   admin routes → 403; foreign session/note/label id → 404.
4. Add owasp dependency-check-maven; fix or justify-suppress HIGH/CRITICAL.
5. Grep audit: no password/token/code values logged; no string-concat SQL;
   no stray @CrossOrigin; no entity types returned from controllers.
6. Sanitizer regression additions: javascript: URLs, <img onerror>,
   style="background:url(...)", nested spans, data: URIs — all stripped.
7. Session abuse tests: answer a question not in session → 404; answer
   after submit → 409; negative secondsSpent → 400.
8. Max request size 2MB (5MB media routes); verify multipart limits.
9. CORS: tighten allowedHeaders from "*" to the explicit list the app
   uses (Authorization, Content-Type).
Frontend:
10. npm audit --omit=dev → fix HIGH/CRITICAL.
11. Build, then grep dist/ for anything secret-like.
12. List every dangerouslySetInnerHTML call site; each must go through
    sanitizeHtml().

Run ./mvnw verify + npm run lint && npm run build.
```

**GATE B**: table all PASS · both builds green · new tests green.
Also run `/security-review` in Claude Code and resolve findings.

## Prompt C — Public landing page + code splitting (was Prompt 13)

```
Read CLAUDE.md. Two jobs:

1. Public marketing site at / (public routes in the same app), French,
   VetSpace brand (green #0F766E primary, navy #12355B, Manrope):
   - Sticky navy header: logo, Accueil, Fonctionnalités, Spécifications,
     Abonnements, Connexion.
   - Hero: headline + pitch + animated counters (add GET /api/public/stats
     → questions/examens/mindmaps totals, permitAll, cached 60s) +
     S'inscrire / Se connecter CTAs.
   - Fonctionnalités: alternating blocks with screenshot placeholders
     from /public/landing/ (I will replace the images).
   - Spécifications: 6 check-list cards (Performance, Mises à jour,
     Contenu, Notification, Schématisation, Support), one accent card.
   - Abonnements: packs from GET /api/packs grouped by école, price DA,
     "Jusqu'à la fin de l'année universitaire", CTA → /register.
   - FAQ accordion (pack choice, password reset, mobile, paiement par
     codes d'activation / CCP / BaridiMob, signaler un problème, guides).
   - Footer: contact, socials. SEO: helmet titles/OG, brand favicon.
2. Route-based code splitting: React.lazy for /admin/*, /app/* page
   groups and the landing page; keep vendor chunking sane. Target: no
   initial chunk > 400 kB. Show the vite build output before/after.

npm run lint && npm run build. Verify at 375/768/1440 widths.
```

**GATE C**: build+lint clean · initial chunk < 400 kB · counters live ·
responsive · all public links work logged-out.

## Prompt D — Tests, CI, README (was Prompt 15)

```
Read CLAUDE.md.
1. JaCoCo ≥70% line coverage on service layer, build fails below; add
   missing tests to reach it (priority: SessionService repeat modes,
   RedemptionService concurrency, StatsService math).
2. Vitest+RTL: auth refresh single-flight, guards, session player
   (mocked API: select → validate → correction rendered sanitized),
   redeem form states.
3. Playwright smoke against docker compose profile "e2e" (seeded):
   register → login → redeem → create session → answer 3 → submit →
   stats visible.
4. .github/workflows/ci.yml: backend verify (Testcontainers), frontend
   lint+test+build, audits, e2e; Maven+npm caches.
5. README.md: setup ≤10 commands, env table, mermaid architecture,
   first admin, activation-code sales runbook.
Run everything locally as CI would.
```

**GATE D**: local run green · GitHub Actions green on push.

## Prompt E — Production deployment (was Prompt 16)

```
Read CLAUDE.md. No secrets in code; list every env var and where it goes.
1. backend/Dockerfile multi-stage (maven → temurin-21-jre, non-root,
   HEALTHCHECK /actuator/health).
2. application-prod.yml: flyway on, swagger off, SQL log off,
   forward-headers framework, PORT env, cookie Secure +
   SameSite=None (cross-domain SPA — document why), captcha enforced.
3. Document Railway setup: postgres DATABASE_URL→jdbc conversion,
   JWT_SECRET (openssl rand -base64 48), SMTP creds, R2 bucket+token,
   CORS_ALLOWED_ORIGINS, SEED_ADMIN=true once then removed.
4. Vercel: VITE_API_URL, SPA rewrites, reCAPTCHA site key.
   R2: public bucket/domain + CORS for GET from frontend origin.
5. docs/deploy.md: click-by-click + post-deploy checklist (health UP,
   register+login on live domain, cookie flags, redeem+session live,
   swagger/actuator dead, R2 images load, admin password rotated).
Prove prod parity locally: built Docker image + built frontend + minio,
complete the smoke journey.
```

**GATE E (final)**: local parity smoke passes · live journey over HTTPS ·
prod cookie HttpOnly/Secure/SameSite=Lax, first-party via the Vercel
/api/* proxy (COOKIE_SAME_SITE env; switch to custom domains later per
deploy.md §3) · direct cross-origin calls to Railway rejected · seed flag
removed.

## Prompt F — Design system elevation (run BEFORE Prompt C if not started;
otherwise right after it)

```
Read CLAUDE.md. Upgrade the frontend from functional to professional.
Work in this order — foundation first so it cascades — and show me the
app in the browser after each step; do not proceed until I approve.

STEP 1 — Tokens (tailwind.config + index.css):
- Full scale extensions: borderRadius (sm 6, md 10, lg 14, xl 20),
  boxShadow (subtle: 0 1px 2px rgb(18 53 91 / 0.06); card: 0 1px 3px
  rgb(18 53 91/0.08), 0 4px 12px rgb(18 53 91/0.05); pop: 0 8px 30px
  rgb(18 53 91/0.12)), spacing rhythm on the default 4px grid only.
- Type scale: display 30/36 bold -0.02em; h1 24/32 bold; h2 18/28
  semibold; body 15/24; caption 13/20 text-gray-500. Encode as Tailwind
  fontSize entries (display, h1, h2, body, caption) and use them.
- Surface tokens: bg-surface (white), bg-canvas (gray-50), border-subtle
  (gray-200/70). Success/warning/danger token trio with soft (bg /10) and
  strong variants, replacing raw red-500/green-500 usage.
- Global: focus-visible ring (2px brand-green offset 2), selection color,
  smooth scrolling, ::-webkit-scrollbar styling in the navy sidebar,
  prefers-reduced-motion media query that zeroes all transitions.

STEP 2 — UI kit (src/components/ui.tsx and siblings):
- Button component with variants (primary, secondary = navy outline,
  ghost, danger) × sizes (sm, md), consistent: rounded-md, font-semibold,
  transition-colors duration-150, focus-visible ring, disabled:opacity-50,
  loading state with inline spinner replacing label. Replace every raw
  <button className=...> in pages with it.
- Card (surface + shadow-card + rounded-lg + p-5/p-6), SectionHeader
  (h2 + optional action slot), Badge/Chip (the stats precision chip and
  status badges unify here), Input/Select/Textarea with consistent
  border, focus ring, error state + message slot, label typography.
- Skeleton component (animate-pulse blocks matching real layout shapes);
  build DashboardSkeleton, TableSkeleton, CardGridSkeleton and use them
  in every react-query loading state (replace spinners).
- EmptyState (icon, title, caption, CTA button) used on: no sessions, no
  labels, no notes, no notifications, empty stats, empty admin tables.
- Modal upgrade: backdrop-blur-sm, scale+fade enter (150ms), focus trap,
  aria-modal, initial focus on first field, body scroll lock.
- Toast upgrade: slide-in bottom-right, auto-dismiss with pause on
  hover, success/error/info variants with icons.

STEP 3 — Shell polish (AppLayout/AdminLayout):
- Sidebar: 8px spacing rhythm, active item = brand-green left bar +
  green-tinted bg + white text, inactive gray-300 hover white,
  icon+label alignment on a 40px row grid, section dividers, user block
  pinned bottom with avatar.
- Topbar: h-16, subtle bottom border, notification bell with animated
  badge (scale-in), avatar menu with proper dropdown (shadow-pop,
  150ms fade, click-outside + Escape close).
- Page container: max-w-7xl, consistent px-4 sm:px-6 lg:px-8 py-8,
  every page starts with the same header pattern (title + caption +
  action button right).

STEP 4 — Page sweep (apply the kit; no page-local one-off styles):
dashboard cards (equal heights, stat cards with icon in soft-tinted
square, hover lift shadow transition), session cards (uniform grid,
donut + meta alignment, favorite star animation), play screen (proposition
rows: rounded border, hover bg, selected = green border + soft bg,
correction VRAI/FAUX with soft success/danger backgrounds and 4px left
border instead of raw colored text), stats table (sticky header, zebra
rows, right-aligned numerics with tabular-nums), auth pages (one Card,
brand logo, proper vertical rhythm), admin tables (consistent toolbar:
search left, filters middle, primary action right).

STEP 5 — Verification:
npm run lint && npm run build (zero warnings). Then walk every page at
375px and 1440px; list any page still using raw colors, raw buttons, or
spinner loading states — must be zero. Screenshot-worthy check: pick the
dashboard, session list, play screen, stats — do they look like one
product? Report.
```

**GATE F**: lint+build clean · no raw buttons/colors/spinners left (grep
`bg-red-500\|bg-green-500\|<button` in pages → only kit components) ·
every loading state is a skeleton · every empty state designed · you
approve each step visually before the next.

---

# Gap-closure prompts (from the audit). Run G before any deploy; H–J after.

## Prompt G — Production blockers & auth hardening (audit #1–5, #7)

```
Read CLAUDE.md. These block or endanger production. First commit any
uncommitted docs changes so the tree is clean.

1. Fail-fast config validation (prod profile): a startup check
   (@Profile("prod"), SmartInitializingSingleton or similar) that refuses
   to boot — clear error message, non-zero exit — when: JWT_SECRET is
   unset, equals any known default, or < 32 bytes; MAIL_MODE=log;
   CORS_ALLOWED_ORIGINS empty or contains localhost; ADMIN_PASSWORD set
   but shorter than 12. Remove the change-me default from application.yml
   entirely (dev profile may keep a dev-only value in
   application-dev.yml). Test with @ActiveProfiles("prod").
2. vercel.json: replace REPLACE-ME with an env-driven value or add a
   predeploy validation script (npm run predeploy) that greps for
   REPLACE-ME/change-me/localhost in vercel.json + dist/ and fails.
   Wire it into the Vercel build command.
3. Email verification: add email_verified bool + verification tokens
   (hashed, 24h, single-use). Register → send verification email; login
   before verification → 403 EMAIL_NOT_VERIFIED with a dedicated frontend
   screen offering resend (rate-limited 3/hour). Dev/e2e profile flag
   AUTO_VERIFY_EMAILS=true so existing tests and seeds keep working.
   Migration marks all EXISTING users verified (they predate the rule).
4. Per-account lockout (DB-backed so it survives restarts and instances):
   5 failed logins within 15 min → account locked 15 min → generic
   "identifiants invalides ou compte temporairement verrouillé" message
   (no oracle). Counter resets on success. Tests: lockout triggers,
   expires, message identical to wrong-password case.
5. Activation code batch safety: (a) POST /api/admin/codes/batches/{id}/
   revoke — revokes every unused code of a batch (for the "generated but
   lost the CSV" case); (b) batch list shows generated_at, downloaded
   (bool set when CSV fetched), counts; (c) docs: runbook section
   "lost CSV → revoke batch → regenerate".
6. Document (docs/deploy.md): rate limiters are instance-local by design;
   Railway must run EXACTLY 1 instance until they move to a shared store;
   add a startup WARN log if a RAILWAY_REPLICA-style env suggests >1.

./mvnw verify green; docs/api.md + deploy.md updated.
```

**GATE G**: prod profile with missing JWT_SECRET refuses to boot (show the
error) · verification + lockout tests green · batch revoke works via
curl · predeploy grep fails on REPLACE-ME.

## Prompt H — Robustness & developer experience (audit #6, #8–11, #22–23)

```
Read CLAUDE.md.
1. React ErrorBoundary at the router level AND around each lazy page
   group: branded error card (logo, "Une erreur est survenue", reload
   button, error digest in dev only). Test: a component that throws
   renders the boundary, not a white page.
2. Real 404 page (branded, link back to /app or /) for unknown routes in
   both the app and public sections — no silent redirect to /.
3. Backend: server.shutdown=graceful + 20s grace; Hikari sized
   explicitly (max 10, min 2, leak detection 60s in dev); document in
   deploy.md why max=10 fits Railway postgres connection limits.
4. nginx.conf (container stack): gzip on for text/*, application/json,
   application/javascript; immutable 1y cache headers for /assets/*
   (hashed filenames), no-cache for index.html.
5. Compose: separate named volumes for dev and e2e postgres; e2e uses its
   own project name in scripts so `--profile e2e down -v` can never
   destroy dev data. Update prod-parity/e2e scripts accordingly.
6. Test-friendliness: RATE_LIMITS_ENABLED=false env flag honored by all
   limiters, on in dev/e2e compose only — never allowed in prod (the
   Prompt G fail-fast check must reject it).

./mvnw verify + npm run lint && build; e2e still green.
```

**GATE H**: throwing component → branded boundary (demonstrate) · unknown
URL → 404 page · `down -v` on e2e leaves dev data intact · rate-limit
flag off in prod check.

## Prompt I — Test depth (audit #12–15)

```
Read CLAUDE.md.
1. Playwright admin journey: login as admin → create question (2 props,
   rich explanation) → publish → generate 5 codes → download CSV parses →
   disable a student → student login fails → re-enable.
2. Frontend coverage: vitest --coverage with a 60% line threshold on
   src/lib + src/auth + src/components (raise later); CI enforces it.
3. CI job "config-sanity": greps vercel.json/dist for placeholders, runs
   the Dockerfile build, boots the image with a prod-like env against a
   service postgres, curls /actuator/health until UP (this puts
   prod-parity in CI, not just the local script). The env must satisfy
   Prompt G's fail-fast checks (generated 48-byte JWT_SECRET, MAIL_MODE
   =smtp with dummy host, non-localhost CORS origin) — and add one
   negative case: boot with JWT_SECRET unset and assert the container
   exits non-zero (proves the fail-fast works in the real image).
4. Light load smoke (k6 or autocannon, in CI as manual-trigger job):
   50 concurrent users hitting login + stats overview + session play for
   60s; assert p95 < 500ms and 0 errors at 1 instance. Document results.

All green locally, then in Actions.
```

**GATE I**: both e2e journeys green in CI · coverage gate enforced ·
config-sanity job green · load numbers recorded in docs.

## Prompt J — Ops & repo hygiene (audit #16–21, #24)

```
Read CLAUDE.md.
1. CI: wire NVD_API_KEY secret into the weekly -Psecurity job (skip
   gracefully with a WARN if secret absent — never permanently red).
2. Add .github/dependabot.yml: weekly, maven + npm + github-actions
   ecosystems, grouped minor/patch updates.
3. docs/runbooks.md: DB backup/restore on Railway (scheduled pg_dump to
   R2 via a cron service or Railway backups, restore steps, and
   "bad migration" recovery: restore + flyway repair walkthrough);
   include the lost-CSV/revoke-batch runbook from Prompt G.
4. Request correlation: servlet filter generates/propagates
   X-Request-Id, added to MDC so every log line carries it and error
   responses include it ("référence" shown on the frontend error toast
   so a student can report it). Test asserts header on responses.
5. Replace backend/mvnw with the real Maven wrapper (mvn wrapper:wrapper),
   commit the jar/properties, remove the apache-maven directory hack; CI
   must use ./mvnw explicitly.
6. Add LICENSE (proprietary — all rights reserved, [your name/company]),
   CHANGELOG.md (Keep-a-Changelog, entries per phase so far), minimal
   CONTRIBUTING.md (setup + run tests + commit convention).
7. Commit docs/continuation-prompts.md if still uncommitted.

./mvnw verify green with the new wrapper on a machine with no system
Maven (simulate: PATH without mvn).
```

**GATE J**: fresh clone builds with ./mvnw alone · dependabot config valid
· request id visible in logs and error responses · runbooks reviewed ·
LICENSE/CHANGELOG present.
