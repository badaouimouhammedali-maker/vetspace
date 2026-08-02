# Changelog

All notable changes to VetSpace are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Nothing has been released publicly yet, so everything to date sits under
[Unreleased]. The first tagged version will be cut at the first production deploy.

## [Unreleased]

### Added

- **One active session per account.** A new login closes the account's other sessions —
  one subscription, one device. `MAX_CONCURRENT_SESSIONS` (default 1) loosens the policy
  without a code change; above 1 the least-recently-used session is evicted instead.
  Refresh tokens now record the device ("Chrome sur Windows", derived from the
  User-Agent), the origin IP and last use, and *why* they were revoked — without that
  last part a device evicted by this policy is indistinguishable from an attacker
  replaying a stolen token, and both were answered as reuse. The evicted device gets
  `401 SESSION_SUPERSEDED` and a screen that says so in words, distinct from an ordinary
  expiry, because it is the one case where the right next step may be to change your
  password. Both auth pages warn about the rule up front rather than letting it be
  discovered by being logged out. Eviction is **not** instant: the evicted device keeps
  working until its access token expires, up to 15 minutes. Admins get logins and
  distinct-IP counts over 7 days per student — the sharing signal — plus "Révoquer la
  session active", which ends sessions without disabling the account.

- **Per-course coverage.** Students could see how one session went but not how much of a
  course they had worked through. `/app/suivi` reports, per course of their year, how
  many published questions exist, how many they have seen, answered and got right, and
  sorts by weakest précision or least covered — the two questions a revision plan is made
  of. Everything is counted per distinct question across every session, so a question
  answered right in a later session corrects an earlier miss; that makes this précision
  deliberately different from the per-session score, and both the API docs and the UI say
  so. The dashboard gains a compact "3 cours à réviser" card.

- **Question import by name.** Import rows may identify their target as
  `{"courseName": "Parvovirose", "moduleName": "…", "studyYear": 3}` instead of a UUID,
  with `sourceExamLabel` likewise; the UUID form still works unchanged. An ambiguous name
  is an error naming every candidate, never a guess — "Ostéologie" exists in more than one
  year of a real catalogue and picking the first would silently fill the wrong course.
  Supplying both an id and a name for the same field is refused rather than resolved by
  precedence. Plus a dry run that reports what each row *would* become and writes nothing,
  and a downloadable sample file whose validity is asserted by a test.

- **Free study-resource library.** Study material — PDFs and images attached to a module
  — is now free to any logged-in student, while the QCM bank stays behind the
  subscription. Students get `/app/cours`: year selector defaulting to their own year,
  module accordion, title search, inline PDF preview with a download fallback, and the
  mindmap lightbox for images. Admins get `/admin/ressources`: drag-and-drop upload with
  progress, publish toggle, reorder, delete, and per-module + total byte counts so R2
  usage is visible where the uploading happens. Uploads are typed by **magic bytes**
  (a `.pdf` whose bytes are `MZ` is rejected and never reaches storage), stored under a
  random `resources/{uuid}.{ext}` key, and deleting a resource deletes the R2 object too.
  Multipart uploads (and only those) go straight to the API origin via
  `VITE_API_DIRECT_URL` instead of through the Vercel rewrite, which 502s on a 25 MB
  body — measured: 10 MB and 20 MB pass, 25 MB does not. They authenticate with the
  in-memory JWT and send no cookie (`withCredentials: false`), so the refresh cookie
  stays first-party and no SameSite question arises.

- **Mail** — `MAIL_MODE=brevo-api`: delivery through Brevo's HTTPS API (port 443) instead
  of SMTP, for platforms that block outbound SMTP entirely — Railway's free tier
  black-holes ports 25/465/587, so a perfectly correct `smtp-relay.brevo.com:587`
  configuration can never connect from there. Same Brevo account and verified sender;
  needs only `BREVO_API_KEY`. Failures surface as `MailException`, so the non-fatal
  registration/reset handling applies unchanged, and the prod validator refuses to boot
  in this mode without the key.
- **Operations** — `docs/runbooks.md`: scheduled `pg_dump` to R2 from a separate Railway
  cron service, restore (rehearsed on a scratch database first), bad-migration recovery
  split by failure mode, and the lost-CSV / leaked-batch procedure.
- **Observability** — `X-Request-Id` on every response, in the MDC so every log line
  carries it, in error bodies, and on the frontend error toast as "Référence", so a
  student can quote an id that leads straight to their request in the log.
- **Dependencies** — `dependabot.yml` for Maven, npm (frontend and e2e) and GitHub
  Actions, weekly, with minor and patch grouped per ecosystem.
- **Project** — `LICENSE` (proprietary), this changelog, and `CONTRIBUTING.md`.
- **CI** — `config-sanity` job: placeholder guard proven in both directions, the
  production image built and booted against a Postgres service, and a negative case
  asserting it refuses to start without `JWT_SECRET`.
- **CI** — manual `Load smoke` workflow (k6). Baseline recorded in `docs/load.md`:
  222,875 requests, 0 failures, read p95 29.9 ms at 50 concurrent users.
- **Testing** — Playwright admin journey: author and publish a question, issue and
  download activation codes, disable a student, prove they cannot log in, re-enable.
- **Testing** — frontend coverage gate at 60% lines over `lib`, `auth` and `components`
  (currently 63%), enforced in CI.
- **Design** — a design system: tokens, a UI kit (Button, Card, Badge, Modal, Toast,
  Skeletons, EmptyState, Toggle primitives), and a shell with one spacing rhythm.
- **Product** — public marketing landing page, route-based code splitting, email
  verification, per-account lockout after 5 failed logins, activation-code batches with
  batch revoke, and a single-instance startup warning.
- **Product** — admin console: overview, catalogue, question authoring with rich
  explanations, packs and codes, subscribers, moderation.
- **Product** — student platform: sessions, the player with server-side scoring,
  statistics, labels, notes, signals, notifications, mindmaps, support.

### Changed

- **Content is national.** Every school now sees the same modules, courses, questions,
  exams and packs; `school_id` was dropped from `modules`, `source_exams` and `packs`
  (V9), which merges what used to be per-school duplicates. Modules are unique on
  `(study_year, name)`, packs on `(study_year, academic_year)` with `NULLS NOT
  DISTINCT`. The subscription gate matches on **study year alone** — a student's école
  no longer takes part in access. "École" survives as a profile field on users (signup
  keeps it, required) plus notification targeting, and now earns its place through a
  "Répartition par école" breakdown on the admin overview. The school selectors are gone
  from the modules, sources and packs admin screens; the Écoles screen stays.

- **Notifications** — the topbar bell now opens a scrollable panel in place instead of
  navigating to a separate page: recent notifications, unread highlighting, per-item
  delete, and mark-all-read on open, without leaving the current screen. The full
  history remains at /app/notifications behind the panel's « voir tout l'historique »
  link; the sidebar entry is gone since the bell is always visible.

- **Build** — `backend/mvnw` is now the real Maven wrapper (`mvn wrapper:wrapper`),
  replacing a hand-rolled script that curled a tarball into a gitignored directory.
  Verified on a machine with no system Maven.
- **CI** — the weekly dependency scan skips with a warning when `NVD_API_KEY` is absent
  instead of failing. It had been red every week, and a permanently red check is one
  people mute.
- **CI** — the e2e job now asserts that *both* journeys ran, not merely that Playwright
  exited 0 — it exits 0 when it runs zero tests.
- **Security** — dependency upgrade to Spring Boot 3.5.16, AWS SDK 2.48.3,
  springdoc 2.8.17.

### Fixed

- After a successful registration the UI said "Vous pouvez maintenant vous connecter"
  and routed to login — where an unverified account is refused with
  EMAIL_NOT_VERIFIED. The register response now carries `emailVerified`; unverified
  accounts land on the "check your inbox" screen with the resend button at hand, while
  auto-verified ones (dev/e2e) keep the direct login redirect.
- Verification emails delivered successfully but every link inside pointed at
  `http://localhost:3000`: `FRONTEND_URL` was unset in production and fell back to the
  dev default. The prod validator now refuses to boot when it is unset, localhost, or
  plain http (the links carry one-time tokens).
- Registration and resend hung indefinitely in production when the SMTP server
  accepted the connection but never answered (typically STARTTLS against port 465, or a
  firewalled host): JavaMail's default timeouts are infinite. SMTP connect/read/write
  timeouts now default to 5 s (`MAIL_TIMEOUT_MS`), so a dead mail server turns into the
  `verificationEmailSent: false` + « renvoyer » path within seconds instead of a stuck
  spinner. The prod validator also now refuses to boot with `MAIL_HOST` unset (it
  silently fell back to `localhost` inside the container) or with SMTP auth and
  credentials configured incoherently; auth/STARTTLS became env-overridable
  (`MAIL_SMTP_AUTH`, `MAIL_STARTTLS`) with mailpit-friendly local defaults and
  provider-friendly prod defaults.
- An SMTP failure destroyed the registration it was supposed to confirm. `register()`
  was `@Transactional` with the verification send inside it, so a mail server outage
  rolled the account back and answered 500 — no account, no email, nothing to retry.
  The account now commits before the send is attempted, the failure is logged and
  reported as `verificationEmailSent: false` on the `201`, and the UI says so and sends
  the student to "renvoyer". Password reset gets the same treatment (without the flag,
  which would have made the response a user-enumeration oracle).
- Creating a question returned 201 but the UI reported an error: the response carried
  null audit timestamps, because `save` inside a transaction does not flush and
  `@CreationTimestamp` is generated at flush. An admin would retry and duplicate the
  question.
- Requests to unmapped paths returned 500 with a logged stack trace instead of 404. This
  is what made `/api/access/redeem` look broken during load-test development; the path
  simply does not exist.
- The dashboard scrolled horizontally at 375px: a grid item defaults to
  `min-width: auto`, so the last-session card refused to shrink below its content.
- The e2e smoke test was order-dependent and failed 2 runs in 3 once a second spec
  published a question into the shared pool.
- Production served a blank page: a `react ⇄ vendor` chunk cycle meant a class extending
  `React.PureComponent` evaluated before React existed.
- CI failed on a nanosecond/microsecond `Instant` mismatch — Linux keeps nanosecond
  precision, Postgres stores microseconds.
- The containerised stack died on startup because the dev profile hard-coded localhost
  over the injected database and mail hosts.

### Security

- Refuses to boot the production profile on unsafe configuration: missing or default
  `JWT_SECRET`, `MAIL_MODE=log`, a localhost CORS origin, or test-only switches left on.
- Predeploy guard blocks a build that still contains placeholders or local addresses.
- Hardening pass: security headers, request size limits, a CORS allowlist, Swagger
  gated to the dev profile, and a default-deny probe test.
- Server-side HTML sanitisation (jsoup allowlist) on admin-authored content, with
  DOMPurify on render.
- Vendored Maven removed and every password moved to environment references.

[Unreleased]: https://github.com/badaouimouhammedali-maker/vetspace/commits/main
