# Contributing

VetSpace is proprietary (see [LICENSE](LICENSE)); this describes how to work on it if
you have been given access.

The rules that actually govern changes live in [CLAUDE.md](CLAUDE.md) — domain model,
security requirements, and the definition of done. Read that first. This file is the
mechanics.

## Setup

Prerequisites: Docker, JDK 21, Node 20. **Maven is not required** — `backend/mvnw`
fetches the pinned version on first use.

Full steps are in the [README](README.md#quick-start-local-dev). The short version:

```bash
cp .env.example .env          # fill JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
docker compose up -d          # postgres + minio + mailpit
(cd backend  && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev)
(cd frontend && npm ci && npm run dev)
```

Never commit secrets. `.env` is gitignored; `.env.example` holds dummy values only.

## Running the tests

Run what CI runs, before pushing:

```bash
(cd backend  && ./mvnw verify)                  # Testcontainers + JaCoCo >=70% on services
(cd frontend && npm run lint)                   # eslint, zero warnings
(cd frontend && npm run test:coverage)          # Vitest + >=60% lines on lib/auth/components
(cd frontend && npm run build)                  # tsc -b + vite build
bash scripts/e2e.sh                             # Playwright: student + admin journeys
```

Two notes that save time:

- `npx tsc --noEmit` **checks nothing**. `tsconfig.json` is a solution file
  (`"files": []` plus project references), so it exits 0 without looking at your code.
  Use `npx tsc -b`, which is what `npm run build` runs.
- The e2e stack needs ports 8080 and 8088. If a dev backend already holds 8080, set
  `E2E_API_PORT=18080`.

Optional, slower:

```bash
bash scripts/config-sanity.sh    # builds the prod image, boots it, asserts it refuses unsafe config
```

## Making a change

1. **Small steps.** Compile and run the relevant tests after each one. Never leave the
   project non-compiling.
2. **Ambiguity → ask.** Do not invent requirements.
3. **Schema changes are new Flyway migrations.** Never edit one that has been applied —
   see [runbooks.md](docs/runbooks.md#3-bad-migration) for what that costs to undo.
4. **API changes update controller, service, tests and `docs/api.md`** together.
5. **Prove the test fails without the fix.** A regression test that passes either way
   documents nothing. Comment *why* the behaviour matters, not what the code does.

## Commit convention

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <imperative summary>

<body: why, not what — the diff already says what>
```

Types in use here: `feat`, `fix`, `test`, `docs`, `ci`, `build`, `perf`, `chore`.

```
fix(session): stop the player double-submitting on a slow network
test(e2e): admin journey — and the two bugs it found
```

One commit per completed phase. The body is where the reasoning goes: what failure the
change prevents, what you rejected and why, what remains unverified. A reader six months
from now has the diff and needs the reasoning.

## Pull requests

CI must be green: backend, frontend, config-sanity and e2e. Dependabot opens grouped
minor/patch updates weekly — those still need a green pipeline before merging.
