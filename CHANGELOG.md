# Changelog

All notable changes to VetSpace are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Nothing has been released publicly yet, so everything to date sits under
[Unreleased]. The first tagged version will be cut at the first production deploy.

## [Unreleased]

### Added

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

### Added

- **Mail** — `MAIL_MODE=brevo-api`: delivery through Brevo's HTTPS API (port 443) instead
  of SMTP, for platforms that block outbound SMTP entirely — Railway's free tier
  black-holes ports 25/465/587, so a perfectly correct `smtp-relay.brevo.com:587`
  configuration can never connect from there. Same Brevo account and verified sender;
  needs only `BREVO_API_KEY`. Failures surface as `MailException`, so the non-fatal
  registration/reset handling applies unchanged, and the prod validator refuses to boot
  in this mode without the key.

### Fixed

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
