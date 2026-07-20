# Deploying VetSpace

Target topology — the browser talks to **one origin** (Vercel), which proxies
`/api` through to Railway. That keeps the refresh cookie first-party, which
several settings below depend on; §3 explains why it matters.

```
                    ┌─ /            static SPA
browser ──HTTPS──▶ Vercel
                    └─ /api/*  ──▶  Railway (Spring Boot API) ──▶ Railway Postgres

browser ──images (GET)──────────▶  Cloudflare R2 (public bucket)
```

Nothing in this guide puts a credential in the repository. Every secret is set in
a platform dashboard; `.env` stays gitignored and `.env.example` holds only dummy
values.

---

## 1. Every environment variable and where it goes

**Legend** — 🔒 secret (never commit, never log) · ⚙️ config · 🤖 injected by the platform.

### Railway — the API service

| Variable | | Value / example | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ⚙️ | `prod` | Activates `application-prod.yml`. **Required** — without it you get dev defaults. |
| `PORT` | 🤖 | *(Railway sets it)* | Port to bind. Read by `application-prod.yml`; do not set by hand. |
| `DB_URL` | ⚙️ | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | JDBC URL — see §2 for why this is not `DATABASE_URL`. |
| `DB_USER` | ⚙️ | `${{Postgres.PGUSER}}` | Database user. |
| `DB_PASSWORD` | 🔒 | `${{Postgres.PGPASSWORD}}` | Database password. |
| `JWT_SECRET` | 🔒 | `openssl rand -base64 48` | Signs access tokens. Rotating it logs everyone out. |
| `FRONTEND_URL` | ⚙️ | `https://vetspace.vercel.app` | Base for password-reset links in emails. |
| `CORS_ALLOWED_ORIGINS` | ⚙️ | `https://vetspace.vercel.app` | Exact SPA origin(s), comma-separated. No wildcard, no trailing slash. |
| `MAIL_MODE` | ⚙️ | `smtp` | `log` prints instead of sending — **must be `smtp` in prod**. |
| `MAIL_HOST` / `MAIL_PORT` | ⚙️ | `smtp.resend.com` / `587` | SMTP server. |
| `MAIL_USERNAME` | ⚙️ | provider user | SMTP auth user. |
| `MAIL_PASSWORD` | 🔒 | provider key | SMTP auth password. |
| `MAIL_FROM` | ⚙️ | `noreply@vetspace.dz` | From address; must be a domain you verified with the provider. |
| `MEDIA_ENDPOINT` | ⚙️ | `https://<account-id>.r2.cloudflarestorage.com` | S3 API endpoint for uploads. |
| `MEDIA_BUCKET` | ⚙️ | `vetspace-media` | Bucket name. |
| `MEDIA_ACCESS_KEY` | 🔒 | R2 token id | Object-storage credential. |
| `MEDIA_SECRET_KEY` | 🔒 | R2 token secret | Object-storage credential. |
| `MEDIA_PUBLIC_BASE_URL` | ⚙️ | `https://media.vetspace.dz` | Public URL images are served from — the browser hits this, not the API. |
| `RECAPTCHA_SECRET` | 🔒 | Google server secret | Verifies captcha tokens. Pairs with the site key on Vercel. |
| `RECAPTCHA_ENABLED` | ⚙️ | `true` | Leave `true`. Only ever `false` for local/e2e. |
| `SUPPORT_INBOX` | ⚙️ | `support@vetspace.dz` | Where support messages are delivered. |
| `DEMO_QUESTION_LIMIT` | ⚙️ | `10` | Questions in the free demo session. |
| `ADMIN_EMAIL` | ⚙️ | `you@vetspace.dz` | First-admin bootstrap — see §5. |
| `ADMIN_PASSWORD` | 🔒 | ≥12 chars | First-admin bootstrap. Rotate in-app, then delete this variable. |
| `ADMIN_USERNAME` | ⚙️ | `admin` *(default)* | Only needed if `admin` is taken. |
| `SEED_ADMIN` | ⚙️ | `true` **once**, then delete | Runs the one-shot admin bootstrap. |
| `COOKIE_SAME_SITE` | ⚙️ | `Lax` | Correct for the proxied same-origin setup in §3. Use `None` only if the SPA calls Railway directly. |
| `COOKIE_SECURE` | ⚙️ | `true` | Always `true` in production. |

### Vercel — the SPA

Build-time only. Vite inlines them into the bundle at build, so **treat both as
public**, and changing either requires a redeploy.

| Variable | | Value | Purpose |
|---|---|---|---|
| `VITE_API_URL` | ⚙️ | *(leave empty)* | Empty = same-origin `/api`, which is what the Vercel proxy serves (§3). Set it to the API origin only if you drop the proxy. |
| `VITE_RECAPTCHA_SITE_KEY` | ⚙️ | Google **site** key | Public half of the captcha pair. Empty hides the widget. |

> Never put `JWT_SECRET`, `RECAPTCHA_SECRET` or any R2 key in a `VITE_*`
> variable — anything prefixed `VITE_` is shipped to every visitor in plain text.

### Not used in production

| Variable | Where it belongs |
|---|---|
| `DB_NAME` | `docker-compose.yml` only — the app never reads it. |
| `SERVER_PORT` | Local only; `application-prod.yml` reads `PORT` instead. |
| `E2E_SEED_CODE` | The e2e stack only. Seeds a known activation code — **never set it in prod.** |

---

## 2. Railway: Postgres, and the `DATABASE_URL` conversion

Railway's Postgres plugin publishes `DATABASE_URL` in libpq form:

```
postgresql://user:password@host:5432/railway
```

Spring Boot cannot use that directly: it needs a `jdbc:` scheme, and it wants the
credentials as separate properties rather than embedded in the URL. Rather than
parsing it, reference the individual variables Railway also publishes:

```
DB_URL       = jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USER      = ${{Postgres.PGUSER}}
DB_PASSWORD  = ${{Postgres.PGPASSWORD}}
```

`${{Postgres.*}}` is Railway's cross-service reference syntax — resolved at
deploy time, so a rotated database password follows automatically. If you named
the plugin something other than `Postgres`, use that name.

Use the **private** host (`PGHOST` inside the project network) — traffic stays
internal and free. Only if you connect from outside Railway do you need the
public proxy host, and then append `?sslmode=require`.

### Click-by-click

1. **New Project → Deploy from GitHub repo**, pick this repository.
2. **Settings → Root Directory**: `backend`. Railway detects the Dockerfile and
   builds it (multi-stage, runs as a non-root user, with a `HEALTHCHECK` on
   `/actuator/health`).
3. **+ New → Database → PostgreSQL** in the same project.
4. **Variables** on the API service: add everything from the Railway table in §1.
   Generate the JWT secret locally and paste it:
   ```bash
   openssl rand -base64 48
   ```
5. **Settings → Networking → Generate Domain**. Note the URL — it is
   the rewrite target in `frontend/vercel.json` (§3).
6. Deploy. Flyway migrates on boot; watch the deploy log for
   `Successfully applied N migrations`.

---

## 3. Why the SPA proxies `/api` — and what that does to the cookie

**The SPA and the API are served from one origin**, because `vercel.json`
rewrites `/api/*` through to Railway:

```json
{ "source": "/api/:path*", "destination": "https://<your-api>.up.railway.app/api/:path*" }
```

Replace `REPLACE-ME` in `frontend/vercel.json` with your Railway domain. Leave
`VITE_API_URL` **empty** on Vercel so the client calls same-origin `/api/...`.

### Why not call Railway directly

The obvious setup — SPA on `vercel.app`, API on `up.railway.app`, browser
talking straight to the API — makes every request cross-site, because those are
different registrable domains. The refresh cookie is then a third-party cookie,
which means:

- **Safari blocks it outright.** Login appears to work (the access token comes
  back in the response body), but the access token lives in memory only, so
  *every page reload* has to call refresh — and that call goes out without the
  cookie. Safari users would re-login on every reload, and again after 15
  minutes mid-session.
- Chrome is progressively restricting the same pattern.

Proxying through Vercel makes the cookie first-party, so it survives everywhere,
and CORS stops being involved at all. The cost is one extra edge hop per API
call, and API traffic counting against your Vercel bandwidth.

Because of that hop, content image uploads are capped at **4 MB** (profile
photos stay at 2 MB) to stay under the proxy's request-body limit.

### Cookie settings for this topology

Same-origin means `COOKIE_SAME_SITE=Lax` is correct and sufficient — set it on
Railway. `COOKIE_SECURE` stays `true`.

### Migrating to your own domain (recommended once you have one)

Point `vetspace.dz` at Vercel and `api.vetspace.dz` at Railway. They are then
the same site, so you can drop the proxy entirely and get the direct, faster
path with no third-party-cookie exposure:

1. Add both domains in the Vercel and Railway dashboards.
2. Set `VITE_API_URL=https://api.vetspace.dz` on Vercel.
3. Remove the `/api/:path*` rewrite from `frontend/vercel.json`.
4. On Railway: `CORS_ALLOWED_ORIGINS=https://vetspace.dz`,
   `FRONTEND_URL=https://vetspace.dz`, and keep `COOKIE_SAME_SITE=Lax` — a
   subdomain of the same registrable domain is same-site, so `Lax` still works.
5. Raise the 4 MB upload cap back toward the 5 MB container ceiling if you want,
   since the proxy body limit no longer applies.

`COOKIE_SAME_SITE` stays configurable precisely so this migration is a variable
change, not a code change.

## 4. Appendix: if you serve the API cross-domain anyway (`SameSite=None`)

If you deliberately skip the proxy and point the SPA straight at Railway, the
cookie must be `SameSite=None` and you inherit the Safari limitation above.

The SPA is on `vercel.app` and the API on `up.railway.app` — different
registrable domains, so **every** API call is cross-site. A `SameSite=Strict` or
`Lax` cookie is simply not attached to cross-site requests, so:

- login appears to succeed (the access token is returned in the body), then
- 15 minutes later the access token expires, the silent refresh call goes out
  **without** the refresh cookie, and the user is bounced to the login screen.

`SameSite=None` makes the browser send it, and browsers only accept `None`
together with `Secure` — which is why `application-prod.yml` sets both, and why
the cookie is still `httpOnly` and scoped to `/api/auth`. `withCredentials` is
already on in the API client, and CORS already returns
`Access-Control-Allow-Credentials: true` for the exact allowlisted origin.

**Known limitation.** `SameSite=None` cookies are third-party cookies, and
browsers are progressively restricting them (Safari ITP blocks them today;
Chrome is phasing them down). The durable fix is to put both on one registrable
domain — `vetspace.dz` for the SPA, `api.vetspace.dz` for the API — after which
you set `COOKIE_SAME_SITE=Lax` and the problem disappears permanently. Plan for
that once you own a domain; the split-domain setup below works now but is on
borrowed time in Safari.

---

## 5. Creating the first admin (once)

`SEED_ADMIN` runs an admin-only bootstrap — it creates **just** the admin
account, never sample content, and it is idempotent: once any admin exists it
does nothing.

1. Set on Railway: `SEED_ADMIN=true`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` (≥12
   characters — a shorter one is refused and no admin is created).
2. Redeploy. The log shows `SEED_ADMIN: created the first admin (<email>)`. The
   password is never logged.
3. Sign in at `/login`, open `/admin`, and **change the password** from the
   profile page.
4. **Delete `SEED_ADMIN`, `ADMIN_PASSWORD` and `ADMIN_EMAIL`** from Railway and
   redeploy. Leaving a plaintext admin password in the dashboard is the risk
   here; the bootstrap itself is inert once an admin exists.

---

## 6. Cloudflare R2

1. **R2 → Create bucket**, e.g. `vetspace-media`.
2. **Settings → Public access → Connect a custom domain** (`media.vetspace.dz`),
   or enable the `r2.dev` development URL. That public base is
   `MEDIA_PUBLIC_BASE_URL`. Content images are served straight from R2 — never
   proxied through the API.
3. **Manage R2 API Tokens → Create token**, permission *Object Read & Write*,
   scoped to this bucket. The token id/secret are `MEDIA_ACCESS_KEY` /
   `MEDIA_SECRET_KEY`; the S3 endpoint
   (`https://<account-id>.r2.cloudflarestorage.com`) is `MEDIA_ENDPOINT`.
4. **Bucket → Settings → CORS policy** — browsers only need to `GET` images from
   the SPA origin:
   ```json
   [
     {
       "AllowedOrigins": ["https://vetspace.vercel.app"],
       "AllowedMethods": ["GET", "HEAD"],
       "AllowedHeaders": ["*"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```
   Uploads go server-side through the API with the token above, so `PUT` does not
   belong in this policy.

---

## 7. Vercel

1. **Add New → Project**, import the repository.
2. **Root Directory**: `frontend`. The framework preset is Vite; `vercel.json`
   already pins the build command, output directory and the **SPA rewrite** that
   makes deep links like `/app/statistiques` serve `index.html` instead of 404.
3. **Environment Variables**: `VITE_API_URL` and `VITE_RECAPTCHA_SITE_KEY` for
   Production (and Preview, if you use preview deploys).
4. Deploy, then copy the deployment origin into Railway's
   `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` and redeploy the API.

> reCAPTCHA: register the site at google.com/recaptcha, add both the Vercel
> domain and any custom domain. The **site** key goes to Vercel, the **secret**
> key to Railway. They must be from the same pair or every register/login is
> rejected.

Preview deployments get a different origin each time and will fail CORS unless
you add them; simplest is to keep captcha and CORS aligned to production only.

---

## 8. Post-deploy checklist

Run against the live domains, in order. Each line is a thing that has actually
broken in this kind of setup.

- [ ] **Health** — `curl https://<api>/actuator/health` returns `{"status":"UP"}`
      with no `components` detail (prod hides internals).
- [ ] **Register + login on the live domain** — complete both in a real browser
      on the Vercel URL, not via curl; captcha only runs in the browser.
- [ ] **Cookie flags** — DevTools → Application → Cookies → `refresh_token`:
      `HttpOnly ✓`, `Secure ✓`, `SameSite=Lax`, `Path=/api/auth`, and the domain
      is your **Vercel** domain (first-party, thanks to the proxy).
- [ ] **Session survives refresh** — stay logged in ~16 minutes (or clear the
      in-memory access token and reload) and confirm you are *not* logged out.
      This is the check that catches a wrong `SameSite`. **Run it in Safari too** —
      Safari is where a third-party cookie setup fails and Chrome looks fine.
- [ ] **Redeem + session live** — redeem a real activation code, build a session,
      answer a question, submit, and see it in Statistiques.
- [ ] **Swagger and actuator are dead** — `/swagger-ui/index.html` and
      `/v3/api-docs` return 404/401, and `/actuator/env`, `/actuator/beans`,
      `/actuator/heapdump` are **not** reachable.
- [ ] **R2 images load** — open a question with an image and a mindmap; the image
      URL should be `MEDIA_PUBLIC_BASE_URL/...` with no CORS error in the console.
- [ ] **Admin password rotated**, and `SEED_ADMIN` / `ADMIN_PASSWORD` /
      `ADMIN_EMAIL` deleted from Railway.
- [ ] **No secrets in the bundle** — `curl -s https://<spa>/assets/index-*.js |
      grep -iE "secret|BEGIN PRIVATE"` finds nothing.
- [ ] **Error shape** — a bad request returns `{"error","message","timestamp"}`
      with no stack trace.

---

## 9. Local production-parity run

Verify the production image and the real production bundle before deploying —
this runs the same artifacts, with MinIO standing in for R2:

```bash
bash scripts/prod-parity.sh
```

It builds the backend image, runs it with `SPRING_PROFILES_ACTIVE=prod`, serves
the production `dist/`, and drives the full student journey against it. See
`scripts/prod-parity.sh` for the exact wiring.
