# Runbooks

Things you do when something has gone wrong, written to be followed under pressure.
Each one states the symptom, the fix, and what to check afterwards.

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
API cannot take the backups with it.

Create a new service in the same project, no HTTP port, with this start command:

```bash
#!/usr/bin/env bash
set -euo pipefail

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="vetspace-${STAMP}.sql.gz"

# --no-owner/--no-acl: the restore target is a fresh database with a different role
# name, and ownership statements would fail every single time.
pg_dump "$DATABASE_URL" --no-owner --no-acl --format=plain \
  | gzip -9 > "/tmp/${FILE}"

# R2 is S3-compatible; awscli works against it with an endpoint override.
aws s3 cp "/tmp/${FILE}" "s3://${R2_BACKUP_BUCKET}/db/${FILE}" \
  --endpoint-url "${R2_ENDPOINT}"

echo "uploaded ${FILE} ($(du -h "/tmp/${FILE}" | cut -f1))"
rm -f "/tmp/${FILE}"
```

Service variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | reference the Postgres service's own variable |
| `R2_ENDPOINT` | `https://<account-id>.r2.cloudflarestorage.com` |
| `R2_BACKUP_BUCKET` | a bucket **separate** from the media bucket |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | an R2 token scoped to that bucket only |
| `AWS_DEFAULT_REGION` | `auto` |

Schedule: Railway service → *Settings* → *Cron Schedule* → `0 2 * * *` (02:00 UTC daily).

Two things worth doing once, now rather than later:

- **Scope the R2 token to the backup bucket.** A token that can also reach the media
  bucket means one leaked credential loses the backups and the uploads together.
- **Turn on a bucket lifecycle rule** (R2 → bucket → *Settings* → *Object lifecycle*) to
  expire objects after 30 days, or the bucket grows forever and the bill with it.

### 1.3 Check it is actually running

```bash
aws s3 ls "s3://${R2_BACKUP_BUCKET}/db/" --endpoint-url "${R2_ENDPOINT}" | tail -5
```

A dump much smaller than yesterday's is the signal to care about — it usually means
`pg_dump` failed partway and the pipe still produced a valid gzip of nothing.

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

```bash
# Removes only rows with success = false. Does not touch data or schema.
cd backend && ./mvnw flyway:repair -Dflyway.url="$PROD_DATABASE_URL" \
  -Dflyway.user="$PGUSER" -Dflyway.password="$PGPASSWORD"
```

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
