# Runbooks

Things you do when something has gone wrong, written to be followed under pressure.
Each one states the symptom, the fix, and what to check afterwards.

**What has and has not been exercised.** The API paths, response shapes, SQL columns and
the Flyway version below were checked against this codebase. The Railway cron service and
the R2 upload in §1.2 have **not** been run — there is no Railway project yet, so that
section is written from the documented behaviour of those services, not from a run.
Treat §1.2 and §2.3 as a first draft to walk through deliberately the first time, not as
steps already proven here. §2.2 (restore into a scratch database) is the cheap way to
find out, and is worth doing before you need it.

Related: [deploy.md](deploy.md) for the environment matrix, [api.md](api.md) for
endpoint contracts, [load.md](load.md) for performance baselines.

---

## Before anything else: find the request

Every response carries an `X-Request-Id`, every error body repeats it as `requestId`,
and the frontend shows it on error toasts as **Référence**. Every log line the request
produced is tagged with it.

```bash
# A student quoted "Référence : f2e35980e41d4b44b334d8aadbcebc94"
railway logs --service backend | grep f2e35980e41d4b44b334d8aadbcebc94
```

Lines with empty brackets `[]` are not part of any request — startup, scheduled work.

---

## 1. Database backup

### 1.1 What is already protected

Railway's Postgres plugin takes its own snapshots. Check what you actually have before
relying on it — **the retention window on the free and hobby tiers is short, and it is
not a substitute for backups you control.** Railway → your Postgres service → *Backups*.

The rule of thumb: a backup you have never restored is a hypothesis. Section 2 is the
test.

### 1.2 Scheduled `pg_dump` to R2

The independent copy. Runs as a separate Railway **cron service** so a mistake in the
API — a bad deploy, a bad migration, a leaked application credential — cannot take the
backups with it.

**It lives in this repository, at [`backup/`](../backup).** That is deliberate: the
previous version of this section was a shell snippet to paste into the Railway dashboard,
and the result was a runbook that described a system nobody had built. Code in the repo
is reviewable, versioned, and — see §1.4 — exercised by CI on every push, so it cannot
quietly stop matching this page.

| File | What it is |
|---|---|
| `backup/Dockerfile` | `postgres:16-alpine` + `aws-cli`, runs as a non-root user, no HTTP port |
| `backup/backup.sh` | dump → verify → upload → verify → optional prune |
| `backup/restore-check.sh` | restore the newest backup into a throwaway database and read rows back |
| `backup/railway.json` | tells Railway to build the Dockerfile and never restart the job |

#### Create the Railway service (once)

1. New service in the same project, from this repository, **Root Directory** `backup`.
2. No public networking — it serves nothing.
3. *Settings* → *Cron Schedule* → `0 2 * * *` (02:00 UTC daily).
4. Variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | reference the Postgres service's own variable |
| `R2_ENDPOINT` | `https://<account-id>.r2.cloudflarestorage.com` |
| `R2_BACKUP_BUCKET` | a bucket used for **nothing else** |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | an R2 token scoped to that bucket only |
| `RETENTION_DAYS` | *optional.* Unset means **never delete** — see below |

Two things worth doing once, now rather than later:

- **Scope the R2 token to the backup bucket.** A token that can also reach the media
  bucket means one leaked credential loses the backups and the uploads together.
- **Use a separate bucket.** Same reason, and it keeps "how much am I storing" answerable.

#### What the job refuses to call success

The failure this guards against is not an errored job — that is visible. It is a job
reporting success while producing a valid gzip of nothing, which is what a plain
`pg_dump | gzip | aws s3 cp` does when `pg_dump` dies halfway: gzip still exits 0, the
object still lands, and the first anyone knows is the day someone tries to restore it.

So the dump is written to a file rather than through a pipe (the exit status is
`pg_dump`'s own), and then it must carry the `PostgreSQL database dump complete` marker,
mention `flyway_schema_history`, define at least 10 tables, verify as a gzip archive, and
match its own byte count once read back out of R2. Any of those failing exits non-zero and
Railway shows the run as failed.

#### Retention

`RETENTION_DAYS` is **off unless you set it**. A backup job that deletes by default is one
typo in one environment variable away from deleting the thing it exists to protect;
opting in is a decision made once, on purpose. Pruning reads the timestamp out of the
object key, so it depends on no clock but the one that wrote it.

#### The Postgres version is load-bearing

`backup/Dockerfile` pins `postgres:16-alpine` to match production, and **when Railway's
Postgres is upgraded that line moves in the same change.**

It is tempting to run a newer client on the reasoning that `pg_dump` can dump older
servers but refuses newer ones. That is true for taking the dump and irrelevant to using
it: `pg_dump` 17 emits `SET transaction_timeout`, which does not exist before 17, so every
such dump fails on its first statement when restored into a 16 server. `restore-check.sh`
caught exactly that, which is the entire argument for §1.4. Forgetting to bump fails
loudly — `pg_dump` refuses a server newer than itself and the job exits non-zero.

### 1.3 Check it is actually running

```bash
docker run --rm \
  -e R2_ENDPOINT -e R2_BACKUP_BUCKET -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
  -e SCRATCH_ADMIN_URL="postgres://…/postgres" \
  --entrypoint /usr/local/bin/restore-check.sh <the backup image>
```

This downloads the newest backup, restores it into a uniquely-named scratch database,
prints the row counts and the Flyway version, asserts no migration is recorded as failed,
and drops the scratch database again. It writes nothing to the bucket and nothing to the
database it came from.

A dump much smaller than yesterday's is still worth noticing, but it is no longer the
primary signal — the job now refuses to upload one.

### 1.4 CI runs the whole thing on every push

The `backup` job in `.github/workflows/ci.yml` builds the image, applies the real
migrations to a Postgres service, takes a backup into a MinIO service, restores it into a
scratch database, and separately proves that retention deletes a stale object and keeps a
recent one. A green tick means the recovery path executed end to end.

A backup you have never restored is a hypothesis. This is the experiment, run continuously
rather than remembered.

---

## 2. Restore

> Practise this on a scratch database **before** you need it. The first time should not
> be during an incident.

### 2.1 Fetch and inspect

```bash
aws s3 cp "s3://${R2_BACKUP_BUCKET}/db/vetspace-20260721T020000Z.sql.gz" . \
  --endpoint-url "${R2_ENDPOINT}"

gunzip -c vetspace-20260721T020000Z.sql.gz | head -40   # sanity: real SQL, right date
gunzip -c vetspace-20260721T020000Z.sql.gz | grep -c "INSERT INTO\|COPY"
```

### 2.2 Restore into a scratch database first

```bash
createdb vetspace_restore_check
gunzip -c vetspace-*.sql.gz | psql vetspace_restore_check

psql vetspace_restore_check -c "
  select 'users', count(*) from users
  union all select 'questions', count(*) from questions
  union all select 'sessions', count(*) from sessions
  union all select 'subscriptions', count(*) from subscriptions;"

# Which migration was the dump taken at? This decides section 3.
psql vetspace_restore_check -c \
  "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"
```

### 2.2b Rehearse a migration against real data

Before deploying any migration that changes existing rows, run it against a restored
production dump rather than against production:

```bash
export DATABASE_URL='postgresql://...'    # Railway → Postgres → Connect → Public URL
./scripts/gate-restore-check.sh
```

It dumps production read-only, restores into a throwaway container, prints the row
counts and *what the migration will actually merge*, applies the migration, then
re-counts and asserts nothing was orphaned. It exits non-zero if the migration fails,
which is the answer to "is this safe to deploy". The dump it leaves behind contains
production data — delete it when you are done.

### 2.3 Restore into production

**Stop the API first.** Railway → backend service → *Settings* → *Suspend*. A running
app writing into a half-restored schema is how one incident becomes two.

```bash
# Keep the broken state — you may need to read data out of it later.
pg_dump "$PROD_DATABASE_URL" --no-owner --no-acl | gzip > pre-restore-$(date -u +%Y%m%dT%H%M%SZ).sql.gz

psql "$PROD_DATABASE_URL" -c "drop schema public cascade; create schema public;"
gunzip -c vetspace-20260721T020000Z.sql.gz | psql "$PROD_DATABASE_URL"
```

Then resume the backend and check:

```bash
curl -fsS https://<api-host>/actuator/health
curl -fsS https://<api-host>/api/public/stats     # non-zero counts = catalogue is there
```

**Everything written after the dump is gone.** Say so plainly to whoever needs to know —
sessions played that morning, codes redeemed, accounts registered.

---

## 3. Bad migration

**Symptom:** the backend will not start after a deploy. Logs show Flyway refusing to
proceed, or the app booting against a schema that no longer matches the code.

Flyway's failure modes look alike and are fixed differently, so identify which one you
have before typing anything:

```bash
psql "$PROD_DATABASE_URL" -c \
  "select installed_rank, version, description, success, checksum
     from flyway_schema_history order by installed_rank desc limit 10;"
```

### 3.1 `success = false` — the migration failed partway

Postgres runs DDL transactionally, so the schema change itself is rolled back, but the
history row stays behind marked failed, and Flyway refuses to continue past it.

Repair removes only rows with `success = false`. It touches no data and no schema.

The project depends on `flyway-core` as a library; there is **no `flyway-maven-plugin`**,
so `./mvnw flyway:repair` would pull an unpinned plugin from Maven Central with none of
this project's configuration — not what you want mid-incident. Use the CLI image,
pinned to the same Flyway version the application runs (`flyway-core` 11.7.2; check with
`./mvnw dependency:list -DincludeArtifactIds=flyway-core` if this drifts):

```bash
docker run --rm flyway/flyway:11.7.2 \
  -url="jdbc:postgresql://<host>:<port>/<db>" \
  -user="$PGUSER" -password="$PGPASSWORD" \
  repair
```

Running a repair with a Flyway version different from the one that wrote the history
table is a good way to turn one problem into two — hence the pin.

Then **fix the migration file** and redeploy. Repair clears the tombstone; it does not
make a broken migration work. Redeploying the same file just fails again.

If the migration was *not* fully transactional (it created an index concurrently, or ran
several statements where an early one committed), the database is now in a half-applied
state that repair cannot describe. Treat it as section 3.3.

### 3.2 Checksum mismatch — an applied migration was edited

```
Migration checksum mismatch for migration version 7
```

Someone changed `V7__email_verification.sql` after it had run somewhere. CLAUDE.md rule
5 exists to prevent exactly this.

The right fix is almost always **to revert the edit** — restore the file to what was
actually applied (`git log -p -- backend/src/main/resources/db/migration/V7__*.sql`) and
put the intended change in a new `V9__…` migration.

`flyway:repair` also realigns checksums to the files on disk, and that is the wrong tool
here unless you are certain the edit was cosmetic (a comment, whitespace) **and** that
every environment ran the identical SQL. Realigning a checksum over a real change leaves
databases that disagree about their own schema while Flyway reports everything as fine.

### 3.3 The migration applied and the result is wrong

Dropped a column, botched a backfill, migrated data into the wrong shape. Repair cannot
help — the SQL did what it said.

1. Suspend the backend (section 2.3) so nothing writes to a wrong schema.
2. Restore the last good dump (section 2).
3. Note the restored schema version from `flyway_schema_history`.
4. Fix the migration **as a new version**. Never edit the applied one; the restore
   brought back a database that already has it recorded.
5. Redeploy and verify.

### 3.4 Prevention that actually works

`docker compose -f scripts/e2e.compose.yml up --build` runs every migration from empty
on each CI e2e run, so *forward from scratch* is continuously proven. What that does not
prove is **forward from production's current state with production's data** — the case
that breaks. Before a migration that touches existing rows, run it against a restored
copy of the latest dump (section 2.2) and check the row counts.

---

## 4. Activation codes: lost CSV / leaked batch

Plaintext codes are shown **exactly once**, at generation. The database stores only
hashes, so nobody — including you — can read them back out. That is deliberate: a
database leak must not be a pile of working codes.

### 4.1 The CSV was never downloaded, or was lost

The codes are unrecoverable. Do not go looking; the hash is one-way.

1. **Admin → Packs & Codes → Batches.** Find the batch by pack and timestamp. The
   listing shows `downloaded`, `activeCount`, `usedCount` and `revokedCount`, so you can
   tell an un-downloaded batch from one that was distributed.
2. **Revoke the whole batch**, so the lost codes cannot be redeemed if they surface:

   ```bash
   curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
     https://<api-host>/api/admin/codes/batches/<batchId>/revoke
   # -> {"batchId":"…","revokedCount":50}
   ```

3. **Generate a replacement batch** of the same size and download the CSV **before
   closing the dialog**. There is no second chance at the plaintext.
4. Check `revokedCount` matches the batch size. If some were already redeemed, those
   students keep their subscriptions — revoking a code does not revoke access already
   granted (see 4.3).

### 4.2 A batch leaked

Same revoke, more urgency, and afterwards:

```sql
-- Who redeemed from the leaked batch, and when?
select u.email, s.starts_at, s.ends_at
from subscriptions s
join users u on u.id = s.user_id
join activation_codes c on c.id = s.activation_code_id
where c.batch_id = '<batchId>'
order by s.starts_at;
```

Redemptions clustered in minutes, or from schools that do not match the pack, are the
sign that it was shared rather than sold.

### 4.3 What revoking does and does not do

| | Effect |
|---|---|
| Unredeemed code | Cannot be redeemed. Attempts fail. |
| Already-redeemed code | **Subscription stays active.** Revoking does not claw back access. |

To end an already-granted subscription, act on the subscription — or disable the account
(**Admin → Abonnés → Désactiver**), which logs them out and blocks login immediately.

### 4.4 Selling codes without this happening again

- Download the CSV before closing the generation dialog. The warning in the UI is not
  decorative.
- Store CSVs somewhere with access control and a retention policy; they are bearer
  tokens for paid access.
- Generate per sales channel or per school rather than one large batch, so a leak is
  revocable without cutting off buyers who did nothing wrong.
- `maxUses = 1` unless a specific deal needs otherwise; one code per student keeps
  "who has access" answerable.
