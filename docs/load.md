# Load smoke

A light, repeatable load check over the three endpoints a student actually hammers:
`POST /api/auth/login`, `GET /api/stats/overview`, `GET /api/sessions/{id}/play`.

It is a **smoke test, not a benchmark** — enough to catch a read path that has become
accidentally quadratic, or a connection pool that starves under concurrency. It does not
model exam-week traffic.

## Running it

Manual only, in CI: **Actions → Load smoke → Run workflow** (`vus` and `duration` are
inputs, defaulting to 50 and `60s`).

Locally, against the seeded e2e stack:

```bash
E2E_API_PORT=18080 docker compose -f scripts/e2e.compose.yml up -d --build

# On the stack's own network — see "Measuring from outside Docker" below.
NET=$(docker inspect vetspace-e2e-backend-1 \
  -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
docker run --rm --network "$NET" -v "$PWD/load:/scripts" \
  -e BASE_URL=http://backend:8080 -e VUS=50 -e DURATION=60s \
  grafana/k6 run /scripts/smoke.js
```

## Thresholds

| Metric | Threshold | Why |
|---|---|---|
| `http_req_duration{kind:read}` p95 | < 500 ms | The headline requirement. |
| `http_req_failed` | rate == 0 | No errors at one instance. |
| `checks` | rate == 1 | Every response was actually correct, not merely non-5xx. |
| `login_duration` p95 | < 15 s | A "did it fall over" guard, not a latency target — see below. |

## Results

Reference run — 2026-07-21, one backend container (8 CPUs available), one Postgres,
50 VUs for 60s, k6 on the stack's Docker network.

| Metric | Value |
|---|---|
| Requests | 222,875 |
| Throughput | ~3,350 req/s |
| **Failed requests** | **0** (0.00%) |
| **Checks passed** | **222,834 / 222,834** |
| **Read p95** | **29.9 ms** |
| Read p90 / median | 24.6 ms / 10.7 ms |
| Login p95 | 3.02 s (burst — see below) |
| Login median | 1.78 s |

Both stated criteria are met: **p95 well under 500 ms on the read paths, and zero errors
at one instance.**

## Why login is measured but not held to 500 ms

Passwords are BCrypt(12). One uncontended login costs **~320 ms**, essentially all of it
hashing — that is the control working, not a bug.

Fifty simultaneous logins is therefore ~50 × 320 ms of unavoidable CPU, and the queue
drains in roughly `(VUs / cores) × 320 ms`:

- 8 cores → ~2.0 s expected, 3.02 s observed at p95
- 2 cores (a small CI runner) → ~8 s expected

Same code, same correctness, four times the p95. A tight threshold would therefore fail
on runner size rather than on a regression, and a CI check that goes red for reasons
unrelated to the change is one people learn to ignore. So login is measured, reported,
and asserted for *correctness*, with a loose ceiling that still catches it degrading
without bound.

The two honest ways to make login itself fast are to lower the BCrypt cost — which
weakens password storage and is not on the table — or to add instances. Login throughput
is CPU-bound; it scales horizontally, not vertically.

Each VU authenticates once and then loops the reads, which also matches how the app is
used: a student logs in once per session and reads many times.

## Measuring from outside Docker

The first local run reported **9 failed requests out of 123,023** (0.007%) with nothing
in the backend logs and `http_req_duration min=0s` — requests that failed before being
sent. It was the measurement path, not the application: k6 in a container reaching the
backend through Docker Desktop's host proxy drops connections at a few thousand req/s.

Re-run on the stack's own Docker network, the same test produced **0 failures out of
214,753**. Both CI and the documented local command therefore keep traffic inside the
Docker network. If you see a tiny non-zero failure rate with a clean server log, check
the topology before the code.

## What this does not cover

- Write paths under load (answering, submitting) — reads only.
- Sustained or ramping load; this is a flat 60 s burst.
- More than one backend instance.
- Media upload, which goes to R2 rather than the API.
