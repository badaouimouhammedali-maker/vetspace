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
prod cookie HttpOnly/Secure/SameSite=None · foreign origins rejected ·
seed flag removed.
