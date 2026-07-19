# VetSpace API

## Overview

The backend currently exposes health checks, the authentication surface
(registration, login, refresh, logout, password reset), the authenticated
user's own profile, the student-facing catalog reads, and the admin
content-management API (schools, modules, courses, source exams, mindmaps,
packs, media upload).

## Error shape

Every error response (validation, conflict, auth failure, rate limit,
unhandled exception) has the same shape and never includes a stack trace:

```json
{
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "timestamp": "2026-07-16T00:00:00Z"
}
```

## Endpoints

### Health
- `GET /api/ping`
  - Returns `{"status":"ok"}`
- `GET /actuator/health`
  - Returns application health status

### Auth

All auth endpoints are rate-limited per client IP (see below). None require
an `Authorization` header.

#### `POST /api/auth/register`
Creates a `STUDENT`/`ACTIVE` account. Does **not** log the user in — no
tokens are returned.

Request body:
```json
{
  "lastName": "Doe",
  "firstName": "Jane",
  "username": "jdoe",
  "email": "jane@example.com",
  "password": "at-least-10-characters",
  "schoolId": "<uuid>",
  "studyYear": 3,
  "recaptchaToken": "<token from the widget>"
}
```
- `password` must be at least 10 characters (400 otherwise).
- `recaptchaToken` is verified server-side against `RECAPTCHA_SECRET`
  unless `RECAPTCHA_ENABLED=false` (set in `dev`/tests).
- Duplicate `email` (case-insensitive) or `username` → `409 Conflict`.
- Unknown `schoolId` → `400 Bad Request`.

Response: `201 Created`
```json
{ "id": "<uuid>", "email": "jane@example.com", "username": "jdoe", "role": "STUDENT", "status": "ACTIVE" }
```

#### `POST /api/auth/login`
Body: `{ "email": "...", "password": "..." }`

- Wrong password, unknown email, or a `DISABLED` account all return the
  same generic `401` (`"Invalid email or password"`) — the client can't
  tell which case it hit.
- On success: `200` with the access token in the body, and a refresh
  token in an httpOnly cookie.

Response body:
```json
{ "accessToken": "<JWT>", "expiresInSeconds": 900 }
```
`Set-Cookie: refresh_token=<value>; Path=/api/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`

Send the access token as `Authorization: Bearer <token>` on subsequent
requests. It expires after 15 minutes.

#### `POST /api/auth/refresh`
No body — reads the `refresh_token` cookie. Rotates it: the old token is
revoked and a new one issued (new cookie, same shape as login's), along
with a fresh access token.

- Presenting an unknown, expired, or **already-rotated** token returns
  `401`. Reusing an already-rotated token additionally revokes every token
  descended from that same login (rotation "family"), signing that whole
  session lineage out even if the attacker also has a copy of the latest
  token.

#### `POST /api/auth/logout`
No body — reads the `refresh_token` cookie, revokes it, and clears the
cookie. `200` regardless of whether a cookie was present.

#### `POST /api/auth/forgot-password`
Body: `{ "email": "..." }`

Always returns `200`, whether or not the email matches an account (no
user enumeration). If it matches, an email is sent (via the configured
mail sender — see `MAIL_MODE` below) with a link:
`${FRONTEND_URL}/reset-password?token=<token>`. The token is single-use
and expires after 30 minutes.

#### `POST /api/auth/reset-password`
Body: `{ "token": "...", "newPassword": "..." }`

- `newPassword` must be at least 10 characters (400 otherwise).
- Invalid, expired, or already-used token → `400 Bad Request`.
- On success: password is updated and **every** refresh token belonging
  to the user is revoked (all devices/sessions are signed out).

### `GET /api/users/me`
Requires `Authorization: Bearer <accessToken>`; `401` without one.

```json
{
  "id": "<uuid>",
  "email": "jane@example.com",
  "username": "jdoe",
  "lastName": "Doe",
  "firstName": "Jane",
  "role": "STUDENT",
  "status": "ACTIVE",
  "schoolId": "<uuid>",
  "studyYear": 3,
  "photoUrl": null,
  "theme": { "primary": null, "secondary": null, "tertiary": null },
  "activeSubscriptions": [
    { "packName": "Pack 3e année", "endsAt": "2027-06-30T00:00:00Z" }
  ]
}
```
`activeSubscriptions` only includes subscriptions currently within their
`starts_at`/`ends_at` window.

### Catalog (student-facing reads)

- `GET /api/schools` — **public** (the signup form needs it). Returns
  `[{id, name, slug}]`, sorted by name, unpaged.
- `GET /api/modules?studyYear=N` — authenticated. For **students**:
  published modules of **their own school** only. For **ADMIN/TEACHER**:
  all modules of that study year across schools. Ordered by position.
- `GET /api/modules/{id}/courses` — authenticated. For students: published
  courses only, and the module itself must be published and belong to the
  student's school — otherwise `404` (indistinguishable from nonexistent).
  ADMIN/TEACHER see all courses of any module.
- `GET /api/source-exams` — authenticated. Session-builder helper: the
  caller's school's source exams (staff: all), newest year first.

### Admin API

All endpoints under `/api/admin/**` require `ROLE_ADMIN` or `ROLE_TEACHER`
via method-level `@PreAuthorize` — **except packs, which are ADMIN
only**. A `STUDENT` token gets `403` on every one of them.

List endpoints are paginated: `?page=0&size=20`, size clamped to **50**
max, response shape
`{content, page, size, totalElements, totalPages}`.

Standard CRUD (all six resources follow the same pattern):

| Resource | Base path | Roles |
|---|---|---|
| Schools | `/api/admin/schools` | ADMIN, TEACHER |
| Modules | `/api/admin/modules` | ADMIN, TEACHER |
| Courses | `/api/admin/courses` | ADMIN, TEACHER |
| Source exams | `/api/admin/source-exams` | ADMIN, TEACHER |
| Mindmaps | `/api/admin/mindmaps` | ADMIN, TEACHER |
| Packs | `/api/admin/packs` | **ADMIN only** |

- `POST /` → `201` with the created DTO
- `GET /` → paged list; `GET /{id}` → single DTO (`404` if unknown)
- `PUT /{id}` → full update; `DELETE /{id}` → `204`
  (deletes blocked by FK protection — e.g. a pack with subscriptions —
  return `409`)

Extra operations:

- `PATCH /api/admin/modules/{id}/publish` and
  `PATCH /api/admin/courses/{id}/publish` and
  `PATCH /api/admin/mindmaps/{id}/publish` — body `{"published": true|false}`.
- `PATCH /api/admin/modules/reorder` and
  `PATCH /api/admin/courses/reorder` — body `{"orderedIds": [id, id, …]}`.
  Positions are rewritten to match list order (1-based). Duplicate ids →
  `400`; any unknown id → `404`; nothing is partially applied.

Notes:
- On create, `position` is assigned automatically (appended at the end of
  the module's school+studyYear group / course's module); use `reorder` to
  change it.
- Module/course/mindmap requests take `published` directly, so create can
  publish immediately.

#### Questions (`/api/admin/questions`, ADMIN + TEACHER)

Standard CRUD plus publish toggle, same conventions as the other admin
resources, with nested propositions managed as part of the question:

- `POST /` — body:
  ```json
  {
    "courseId": "<uuid>",
    "statement": "…",
    "statementImages": ["<media url>"],
    "sourceExamId": null,
    "difficulty": "EASY|MEDIUM|HARD",
    "published": false,
    "propositions": [
      { "letter": "A", "text": "…", "isTrue": true,
        "explanationHtml": "<p>…</p>", "explanationImages": [] },
      { "letter": "B", "text": "…", "isTrue": false }
    ]
  }
  ```
  Proposition rules (also enforced on update and import): **2–5**
  propositions, letters **A–E unique**, **at least one true and at least
  one false**. Violations → `400`.
- `explanationHtml` is sanitized **server-side on save** (jsoup allowlist:
  `p, br, b, strong, i, em, u, span[style: color only], ul, ol, li, h3,
  h4, img[src from our media domain only, alt], a[href https only,
  rel=noopener]`). Scripts, event handlers, foreign images, and non-color
  styles are stripped before the content ever reaches the database.
- `PUT /{id}` replaces the whole proposition set.
- `PATCH /{id}/publish` — `{"published": true|false}`.
- `GET /` — filterable, paginated list:
  `?courseId=&moduleId=&sourceExamId=&difficulty=&published=&q=&page=&size=`
  where `q` is a case-insensitive free-text search on the statement.
- The admin DTO includes `isTrue` and explanations; it is never served to
  students.

#### `POST /api/admin/questions/import` (ADMIN, TEACHER)

Bulk import. Body: a JSON **array** of question objects (same shape as
create). Validation is **all-or-nothing**: every row is checked first
(unknown course/source exam, blank statement, proposition rules) and any
failure returns `400` with the standard error shape **plus** a per-row
detail list — nothing is persisted, not even valid rows:

```json
{
  "error": "Bad Request",
  "message": "Import failed validation",
  "timestamp": "…",
  "errors": [
    { "row": 1, "field": "courseId", "message": "unknown course" },
    { "row": 1, "field": "propositions", "message": "at least one proposition must be false" }
  ]
}
```

Success: `201` `{ "imported": N, "questionIds": [ … ] }`.

#### `GET /api/questions/count` (authenticated)

Session-builder helper: how many questions match the filters.
`?courseIds=<uuid>&courseIds=<uuid>&moduleId=&sourceExamId=&difficulty=`
(all optional). Returns `{ "count": N }`.

- **Students** count only published questions in published courses of
  published modules **of their own school**. Subscription gating is not
  yet applied (comes with the session engine).
- ADMIN/TEACHER count everything matching the filters.

Students never receive proposition truth values or explanations outside
the correction flow — the play DTO structurally has no such fields (this
is covered by a serialization test).

#### `POST /api/admin/media` (ADMIN, TEACHER)

The **only** way images enter the system.

- `multipart/form-data`, field name `file`.
- Max **5MB** → larger uploads get `413`.
- Accepted types: JPEG, PNG, WebP, GIF — verified by **magic bytes**; the
  filename and declared Content-Type are ignored. A PDF renamed to
  `.png` → `400`.
- Stored in R2/minio under `media/{uuid}.{ext}` (extension derived from
  the sniffed type, never from the filename).

Response: `201` `{ "url": "<MEDIA_PUBLIC_BASE_URL>/media/<uuid>.<ext>" }`

### Public marketing

#### `GET /api/public/stats` — **public** (landing page)
No auth. Aggregate totals for the marketing counters:
`{questions, examens, mindmaps}` — published questions, exam sources, and
published mindmaps. Result is memoized server-side for 60s.

### Packs & activation codes

#### `GET /api/packs` — **public** (pricing page)
`?schoolId=&studyYear=` (both optional). Active, unexpired packs only:
`[{id, schoolId, studyYear, name, academicYear, priceDa, expiresAt}]`.
`studyYear: null` = résidanat-style pack (any year).

#### `POST /api/admin/codes` (ADMIN only) → `201`
Body `{"packId": "<uuid>", "count": 1–500, "maxUses": 1}` (`maxUses`
optional, default 1). Generates crypto-random 16-character codes from an
unambiguous alphabet (no `0/O`, `1/I/L`), displayed
`XXXX-XXXX-XXXX-XXXX`.

**Plaintext is returned exactly once** in this response
(`{packId, count, codes: [...], csvToken}`); the database stores only
SHA-256 hashes, and codes are never written to logs.

#### `GET /api/admin/codes/csv/{csvToken}` (ADMIN only)
One-shot CSV download of that generation batch. The token works **once**
(then `404`) and expires after 15 minutes.

#### `GET /api/admin/codes` (ADMIN only)
`?packId=&page=&size=` — paged list with computed
`status: ACTIVE | EXHAUSTED | REVOKED | EXPIRED` (expired = pack inactive
or past its end date), `usedCount`/`maxUses`, timestamps. Hashes and
plaintext are never returned.

#### `POST /api/admin/codes/{id}/revoke` (ADMIN only)
Marks the code revoked; redemption immediately refuses it.

#### `GET /api/admin/subscriptions?email=` (ADMIN only)
Audit: every subscription of the account with that email (pack, window,
originating code id).

#### `POST /api/codes/redeem` (authenticated)
Body `{"code": "XXXX-XXXX-XXXX-XXXX"}` (dashes/case optional). Valid =
known hash + not revoked + pack active and unexpired + `used_count <
max_uses`. The increment is **atomic** (`UPDATE … WHERE used_count <
max_uses`), so concurrent redemptions of a code's last use have exactly
one winner. Success creates a subscription with `ends_at =
pack.expires_at` (fixed date — "jusqu'à la fin de l'année
universitaire") and returns `{subscriptionId, packName, startsAt,
endsAt}`.

Every failure mode returns the same generic `400 "Invalid activation
code"`. Rate limit: **5 attempts/hour per user AND per IP** (both buckets
count every attempt) → `429`.

### Subscription gate

Session creation/play (and repeat), `GET /api/questions/count`, and the
student mindmap endpoints require an **active subscription matching the
student's school + study year** (a pack with `studyYear: null` matches any
year). ADMIN/TEACHER are exempt. Failure:

```json
{ "error": "SUBSCRIPTION_REQUIRED", "message": "…", "timestamp": "…" }
```
(HTTP 403 — the frontend routes on the `error` code.)

**Demo mode**: an unsubscribed student may create **one** session ever,
restricted to courses flagged `freePreview` (admin course field) and
capped at `DEMO_QUESTION_LIMIT` questions (default 10). That demo session
stays playable/answerable; a second session, paid-content filters, or
repeat all return `SUBSCRIPTION_REQUIRED`.

### Mindmaps (student-facing, subscription-gated)
- `GET /api/mindmaps?courseId=` — published mindmaps of a
  published/own-school course (staff: everything).
- `GET /api/mindmaps/{id}` — same visibility rules; invisible = `404`.

### Sessions (authenticated, owner-only)

The server is the single source of truth for correctness: answers are
evaluated server-side, and **play payloads never contain proposition truth
values or explanations** — correction data flows only through the
per-question answer/consult responses.

Every session endpoint checks ownership: another user's session id
behaves exactly like a nonexistent one (`404`, never `403`).

#### `POST /api/sessions` → `201`
```json
{
  "title": "optional",
  "sessionType": "ENTRAINEMENT|EXAMEN",
  "filters": {
    "moduleIds": [], "courseIds": [], "sourceExamIds": [],
    "difficulty": "EASY|MEDIUM|HARD", "onlyUnseen": true
  },
  "questionCount": 20
}
```
Selects **random** matching questions (student visibility rules: published
question/course/module, own school), capped at `questionCount`; `400` if
nothing matches. `onlyUnseen` excludes questions from any of the user's
previous sessions. Filters are stored (jsonb) for `repeat SAME_FILTERS`.
Missing title → auto `"<Module> (dd/MM/yyyy HH:mm)"` (first module of the
filter, else "Session").

#### `GET /api/sessions`
My sessions, paginated (`page`, `size` ≤ 50), sorted **favorites first,
then most recent**. Each row: `questionCount`, `answeredCount`,
`correctCount`, `percentCorrectSoFar` (over answered), `totalSeconds`,
`favorite`, `rating`, `status`, `score`, timestamps.

#### `GET /api/sessions/{id}/play`
Questions in session order with propositions (**play DTO**: id, letter,
text, position — nothing else), per-question `state`
(`UNANSWERED|ANSWERED|CONSULTED`), `isCorrect` (own result, set after
answering), `secondsSpent`, and my `selectedPropositionIds`.

#### `PUT /api/sessions/{id}/questions/{qid}/answer`
Body `{"selectedPropositionIds": [...], "secondsSpent": N}`. Upsert
(re-answering replaces the previous selection). The server evaluates an
**exact-set match** — every true proposition selected and nothing else;
partial selection is wrong. Sets state `ANSWERED` + `isCorrect`,
accumulates seconds (per-question and session-wide). Returns the
correction **for that question only**: per-proposition `isTrue`,
`explanationHtml`, `explanationImages`, plus your selection. `409` if the
session is already submitted; `400` if a selected proposition doesn't
belong to the question.

#### `POST /api/sessions/{id}/questions/{qid}/consult`
Marks the question `CONSULTED` (viewed the correction without answering;
an already-`ANSWERED` question keeps its state) and returns the same
correction shape.

#### `POST /api/sessions/{id}/submit`
Computes `score` = correct / **all** questions × 100 (unanswered count as
wrong), sets status `SUBMITTED`. Further answer/consult/submit → `409`.

#### `PATCH /api/sessions/{id}`
Partial update: `{"title"?, "favorite"?, "rating"? (1–5)}` — only provided
fields change.

#### `POST /api/sessions/{id}/repeat` → `201`
Body `{"mode": "ALL" | "WRONG_ONLY" | "UNANSWERED_ONLY" | "SAME_FILTERS"}`.
Creates a **new ACTIVE session**: `ALL` = same questions; `WRONG_ONLY` =
questions answered incorrectly; `UNANSWERED_ONLY` = never-answered ones
(including consulted); `SAME_FILTERS` = re-runs the stored filters for a
fresh random selection of the same size. `400` if the resulting set is
empty.

#### `POST /api/sessions/{id}/reset`
Wipes the same session in place: answers deleted, all states back to
`UNANSWERED`, per-question and total timers zeroed, score/submitted_at
cleared, status back to `ACTIVE`, started_at now. Returns the fresh play
payload.

#### `DELETE /api/sessions/{id}` → `204`
Removes the session and (via DB cascade) its question rows and answers.

### Stats (authenticated, own data only)

- `GET /api/stats/sessions?type=ENTRAINEMENT|EXAMEN` — per session:
  title, `totalSeconds`, `avgSecondsPerQuestion` (total time / total
  questions), `totalQuestions`, `juste`, `fausse`, `consulte`,
  `precisionPercent` (juste / answered × 100), most recent first.
- `GET /api/stats/sessions/{id}/by-course` — the same metrics grouped by
  course within one session (`404` for a foreign session).
- `GET /api/stats/weekly` — the last 7 days (UTC buckets, zero-filled,
  oldest first): `{date, juste, fausse, consultees}` per day. A question's
  bucket is its **last** answer/consult time.
- `GET /api/stats/overview` — dashboard payload: `bank` totals
  (published questions / source exams / published mindmaps — school-scoped
  for students, global for staff), `lastSession` (same shape as the
  session stats rows, `null` if none) and `activeSubscriptions`.

### Labels (authenticated, own only — foreign ids read as 404)

- `POST /api/labels` `{name, color}` (color must be `#rrggbb`) → `201`;
  `GET /api/labels` (with `questionCount` per label); `PUT /{id}`;
  `DELETE /{id}` (detaches all questions via DB cascade).
- `POST /api/labels/{id}/questions/{questionId}` — attach (idempotent);
  `DELETE` the same path — detach.
- `GET /api/labels/{id}/questions` — the labelled questions as **play
  DTOs** (never truth/explanations).
- Session filters (and `GET /api/questions/count`) accept `labelIds`:
  build a session from labelled questions. Label ids are matched only
  against the caller's own labels.

### Notes (authenticated, own only — foreign ids read as 404)

`POST /api/notes` / `GET /api/notes?questionId=&courseId=` /
`GET|PUT|DELETE /api/notes/{id}`. Body:
`{title, contentHtml, questionId?, courseId?}` — `contentHtml` passes
through the same server-side sanitizer as question explanations.

### Signals (question error reports)

- `POST /api/signals` `{questionId, message}` → `201`. Rate limit **10 per
  day per user** → `429`.
- `GET /api/signals` — my reports with status and the admin's reply.
- `GET /api/admin/signals?status=OPEN|RESOLVED|REJECTED` (ADMIN/TEACHER,
  paged) and `POST /api/admin/signals/{id}/resolve` /
  `POST /api/admin/signals/{id}/reject` with `{reply}`.

### Notifications

- `POST /api/admin/notifications` (ADMIN/TEACHER)
  `{kind: UPDATE|QUESTIONS|INFO, title, body, schoolId?, studyYear?}` —
  null targeting fields mean "everyone".
- `GET /api/admin/notifications` (ADMIN/TEACHER) — broadcast history,
  newest first: `[{id, kind, title, body, schoolId?, schoolName?,
  studyYear?, createdAt}]` (null school/year = everyone).
- `GET /api/notifications` — the caller's targeted feed (own school/year +
  broadcasts; staff see everything), newest first, each with `read`;
  soft-deleted ones are excluded.
- `GET /api/notifications/unread-count` → `{count}`.
- `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.
- `DELETE /api/notifications/{id}` — soft delete (per-user; the
  notification itself is untouched). Out-of-target ids → `404`.

### Support

`POST /api/support` `{subject, body}` → `201`. Stores the message and
emails `SUPPORT_INBOX` with **Reply-To set to the student's email**. Rate
limit **5 per day per user** → `429`.

`GET /api/admin/support?page=&size=` (ADMIN/TEACHER) — read-only inbox,
paged, newest first: `{content: [{id, userEmail, username, fullName,
subject, body, createdAt}], …}`. Replies happen out-of-band by email.

### Admin console (overview & subscribers)

- `GET /api/admin/overview` (ADMIN/TEACHER) → `{students, questions,
  sessionsToday, activeSubscriptions, openSignals, latestRegistrations:
  [{id, username, email, fullName, schoolName?, studyYear?, createdAt}]}`
  (8 most recent student registrations).
- `GET /api/admin/users?query=&page=&size=` (ADMIN/TEACHER) — student
  search (blank query = all), paged, newest first: `{content: [{id,
  username, email, fullName, role, status, schoolName?, studyYear?,
  activeSubscriptions, createdAt}], …}`.
- `PATCH /api/admin/users/{id}/status` **(ADMIN only)** `{status:
  ACTIVE|DISABLED}` — enable/disable a student; disabling revokes their
  live refresh tokens so access ends immediately (a `DISABLED` account
  cannot log in). An admin **cannot** change their own status → `409`.

### Profile (authenticated, self only)

- `PATCH /api/users/me` — partial update: `lastName`, `firstName`,
  `username` (conflict → `409`), `schoolId`, `studyYear`, and
  `themePrimary/Secondary/Tertiary` (each validated as `#rrggbb`).
  Returns the updated `/me` payload.
- `POST /api/users/me/photo` — multipart `file`, max **2MB**, JPEG/PNG/
  WebP only (magic-byte verified, GIF not accepted for profiles), stored
  on R2/minio under a random `profile/{uuid}.{ext}` key. Returns
  `{photoUrl}`.
- `POST /api/users/me/password` `{currentPassword, newPassword}` — wrong
  current password → `400`; success revokes every refresh token (other
  devices are signed out).
- `DELETE /api/users/me` `{password}` — **anonymizing deletion**: email/
  username are randomized (`deleted-…`), names replaced, status
  `DISABLED`, password hash randomized, photo and theme cleared; notes and
  labels are deleted outright and all tokens revoked. Sessions and answers
  are kept for anonymized aggregates. Login with the old credentials fails
  with the standard generic `401`.

### Swagger UI
- `GET /swagger-ui/index.html` (enabled in the `dev` profile)
- `GET /v3/api-docs` (enabled in the `dev` profile)

## Rate limiting

Per client IP (via `X-Forwarded-For` if present, else the socket address),
in-memory (single-instance):
- `POST /api/auth/login` and `POST /api/auth/register`: 10 requests/minute
  each.
- `POST /api/auth/forgot-password`: 5 requests/hour.
- `POST /api/codes/redeem`: 5/hour per user **and** per IP.
- `POST /api/signals`: 10/day per user. `POST /api/support`: 5/day per
  user.

Exceeding a limit returns `429 Too Many Requests` with the standard error
shape.

## Security headers & limits

Every response carries: `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`,
and `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`.

- CORS: origins from `CORS_ALLOWED_ORIGINS` (no wildcard), credentials
  allowed, methods `GET/POST/PUT/PATCH/DELETE/OPTIONS`, request headers
  restricted to `Authorization, Content-Type`.
- Request bodies: non-multipart requests are capped at **2MB** (`413`
  otherwise); multipart media uploads allow up to **5MB** (content images;
  profile photos additionally capped at 2MB in code).
- Swagger UI / OpenAPI (`/swagger-ui/**`, `/v3/api-docs/**`) are served and
  permitted **only under the `dev` profile**.

Dependency vulnerability scanning: `./mvnw -Psecurity verify` runs OWASP
dependency-check, failing on any dependency with CVSS ≥ 7 (justified
false-positives live in `dependency-check-suppressions.xml`).

## Dev data seed

On startup with the `dev` profile, if the `users` table is empty and
`ADMIN_EMAIL`/`ADMIN_PASSWORD` are set: seeds one school ("ENSV Alger"),
one placeholder module per study year 1–5 under it, and one `ADMIN`
account. No-op if the database already has any user.

## Environment variables

The service reads configuration from environment variables and `.env`
files. The main keys are:

- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET` — signs access tokens (any length; hashed internally to a
  valid HMAC-256 key)
- `FRONTEND_URL` — base URL used to build the password-reset link
- `CORS_ALLOWED_ORIGINS`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- `MAIL_MODE` — `log` (default; logs instead of sending, no SMTP server
  needed) or `smtp` (sends via spring-mail)
- `MEDIA_ENDPOINT`, `MEDIA_BUCKET`, `MEDIA_ACCESS_KEY`, `MEDIA_SECRET_KEY`, `MEDIA_PUBLIC_BASE_URL`
- `RECAPTCHA_SECRET`
- `RECAPTCHA_ENABLED` — `true` by default; set to `false` to skip
  verification entirely (already `false` in the `dev` profile)
- `DEMO_QUESTION_LIMIT` — max questions in the single demo session an
  unsubscribed student may create (default 10)
- `SUPPORT_INBOX` — destination address for support messages (default
  `support@vetspace.local`)
- `ADMIN_EMAIL`, `ADMIN_PASSWORD` — used only by the `dev` seeder

## Local development

1. Start infrastructure services:
   - `docker compose up -d`
2. Run the backend:
   - `cd backend`
   - `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
3. Test the endpoints:
   - `curl http://localhost:8080/api/ping`
   - `curl http://localhost:8080/actuator/health`
