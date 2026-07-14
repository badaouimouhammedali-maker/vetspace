# VetSpace — Project Rules

## What this is
QCM study platform for veterinary students (French UI). Monorepo:
- /backend  → Spring Boot 3, Java 21, Maven, PostgreSQL, Flyway
- /frontend → React 18, Vite, TypeScript, Tailwind CSS

## Domain model (summary — full schema in docs/schema.md)
School → StudyYear → Module → Course → Question → Propositions(A–E, each
true/false + rich HTML explanation + images). Students create Sessions by
filtering questions; answers are per-proposition; scoring is server-side.
Access is gated by Subscriptions created from ActivationCodes (packs per
school × study year). Extras: labels, notes, signals, notifications,
mindmaps, support messages.

## Non-negotiable working rules
1. Small steps. Compile + run relevant tests after every step. Never leave
   the project non-compiling.
2. Never invent requirements. Ambiguity → stop and ask.
3. Never commit secrets. Env vars only; .env gitignored; .env.example with
   dummy values.
4. Every API change updates controller + service + tests + docs/api.md.
5. Schema changes only via new Flyway migrations. Never edit an applied one.

## Security rules (every task)
- Bean Validation on all input; DTOs at every boundary; never expose
  entities.
- Session/question DTOs served during an ACTIVE session must not contain
  proposition truth values or explanations. Correction data is returned
  only by the dedicated correction endpoint, only for answered/consulted
  questions or submitted sessions.
- Admin-authored rich HTML (explanations, notes) is sanitized SERVER-SIDE
  on save (jsoup allowlist: p, br, b, strong, i, em, u, span[style with
  color only], ul, ol, li, h3, h4, img[src from our media domain only,
  alt], a[href https only, rel=noopener]) AND client-side on render
  (DOMPurify).
- Passwords BCrypt(12). JWT access 15 min in memory; refresh 7 days in
  httpOnly Secure cookie, hashed in DB, rotated on use, family revoked on
  reuse.
- Method-level @PreAuthorize everywhere; default deny. Ownership checks on
  every user-owned resource (sessions, notes, labels...); foreign IDs → 404.
- Parameterized queries only. Rate limiting (Bucket4j) on auth, code
  redemption, signals, support. Generic auth error messages.
- Global @RestControllerAdvice error shape {"error","message","timestamp"};
  no stack traces to clients. CORS from env allowlist, no wildcard.
- Media uploads: admin-only for content images; profile photos max 2MB,
  jpeg/png/webp, content-type verified by magic bytes, stored on R2 with
  random keys, served via public R2 URL — never from the API filesystem.
- Never log passwords, tokens, or activation codes.

## Definition of done
- ./mvnw verify green; npm run build && npm run lint green (once each side
  exists); new behavior tested; docs/api.md current.