#!/usr/bin/env bash
# Gate 1, item 2 — restore a production dump into a scratch database and migrate it
# (docs/runbooks.md §2.2), then report exactly what V9 did to the real rows.
#
# Nothing here writes to production: it takes a read-only pg_dump and works on a
# throwaway container. DATABASE_URL never leaves your machine — paste the OUTPUT,
# not the variable.
#
# Usage:
#   export DATABASE_URL='postgresql://...'   # Railway → Postgres → Connect → Public URL
#   ./scripts/gate-restore-check.sh
#
# Requires docker. Everything else runs inside containers, so the client version
# matches the server version (a pg_dump older than the server refuses to run).

set -euo pipefail

: "${DATABASE_URL:?Set DATABASE_URL to the production connection string first}"

PG_IMAGE="${PG_IMAGE:-postgres:18}"
SCRATCH="gate-restore-pg"
DUMP="prod-dump-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
MIGRATION="$(cd "$(dirname "$0")/.." && pwd)/backend/src/main/resources/db/migration/V9__national_content.sql"

[ -f "$MIGRATION" ] || { echo "Cannot find V9 at $MIGRATION" >&2; exit 1; }

cleanup() { docker rm -f "$SCRATCH" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> 1/6  Dumping production (read-only)"
docker run --rm -e PGCONNECT_TIMEOUT=15 "$PG_IMAGE" \
  pg_dump --no-owner --no-acl "$DATABASE_URL" | gzip > "$DUMP"
echo "    wrote $DUMP ($(du -h "$DUMP" | cut -f1))"
echo "    sanity: $(gunzip -c "$DUMP" | grep -c 'COPY\|INSERT INTO') COPY/INSERT statements"

echo "==> 2/6  Starting scratch database ($PG_IMAGE)"
cleanup
docker run -d --name "$SCRATCH" -e POSTGRES_PASSWORD=scratch -e POSTGRES_DB=restore_check \
  "$PG_IMAGE" >/dev/null
until docker exec "$SCRATCH" pg_isready -U postgres -d restore_check >/dev/null 2>&1; do sleep 1; done

echo "==> 3/6  Restoring the dump"
gunzip -c "$DUMP" | docker exec -i "$SCRATCH" psql -U postgres -d restore_check -q >/dev/null
psql_s() { docker exec -i "$SCRATCH" psql -U postgres -d restore_check "$@"; }

echo
echo "=== Migration level the dump was taken at (decides runbook §3) ==="
psql_s -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

echo "=== BEFORE: row counts ==="
psql_s -c "
SELECT 'schools' t, count(*) FROM schools
UNION ALL SELECT 'modules', count(*) FROM modules
UNION ALL SELECT 'courses', count(*) FROM courses
UNION ALL SELECT 'source_exams', count(*) FROM source_exams
UNION ALL SELECT 'questions', count(*) FROM questions
UNION ALL SELECT 'packs', count(*) FROM packs
UNION ALL SELECT 'activation_codes', count(*) FROM activation_codes
UNION ALL SELECT 'subscriptions', count(*) FROM subscriptions
UNION ALL SELECT 'users', count(*) FROM users ORDER BY 1;"

echo "=== What the merge will actually touch (empty = pure column drop) ==="
psql_s -c "SELECT study_year, lower(trim(name)) AS name, count(*) AS copies
           FROM modules GROUP BY 1,2 HAVING count(*) > 1 ORDER BY 3 DESC;"
psql_s -c "SELECT lower(trim(label)) AS label, year, exam_type, count(*) AS copies
           FROM source_exams GROUP BY 1,2,3 HAVING count(*) > 1 ORDER BY 4 DESC;"
psql_s -c "SELECT study_year, academic_year, count(*) AS copies
           FROM packs GROUP BY 1,2 HAVING count(*) > 1 ORDER BY 3 DESC;"

echo "==> 4/6  Applying V9__national_content.sql"
if docker exec -i "$SCRATCH" psql -U postgres -d restore_check -q -v ON_ERROR_STOP=1 < "$MIGRATION"; then
  echo "    V9 applied cleanly"
else
  echo "    *** V9 FAILED — do not deploy. Output above is the reason. ***" >&2
  exit 1
fi

echo
echo "=== AFTER: row counts ==="
psql_s -c "
SELECT 'schools' t, count(*) FROM schools
UNION ALL SELECT 'modules', count(*) FROM modules
UNION ALL SELECT 'courses', count(*) FROM courses
UNION ALL SELECT 'source_exams', count(*) FROM source_exams
UNION ALL SELECT 'questions', count(*) FROM questions
UNION ALL SELECT 'packs', count(*) FROM packs
UNION ALL SELECT 'activation_codes', count(*) FROM activation_codes
UNION ALL SELECT 'subscriptions', count(*) FROM subscriptions
UNION ALL SELECT 'users', count(*) FROM users ORDER BY 1;"

echo "==> 5/6  Integrity — every number below MUST be 0"
psql_s -c "
SELECT
  (SELECT count(*) FROM courses c WHERE NOT EXISTS (SELECT 1 FROM modules m WHERE m.id=c.module_id)) AS orphan_courses,
  (SELECT count(*) FROM questions q WHERE q.source_exam_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM source_exams e WHERE e.id=q.source_exam_id))                      AS orphan_questions,
  (SELECT count(*) FROM subscriptions s WHERE NOT EXISTS (SELECT 1 FROM packs p WHERE p.id=s.pack_id)) AS orphan_subscriptions,
  (SELECT count(*) FROM activation_codes a WHERE NOT EXISTS (SELECT 1 FROM packs p WHERE p.id=a.pack_id)) AS orphan_codes,
  (SELECT count(*) FROM code_batches b WHERE NOT EXISTS (SELECT 1 FROM packs p WHERE p.id=b.pack_id))  AS orphan_batches;"

echo "==> 6/6  school_id survivors (expect exactly: notifications, users)"
psql_s -tAc "SELECT table_name FROM information_schema.columns
             WHERE column_name='school_id' AND table_schema='public' ORDER BY 1;"

echo
echo "Done. The scratch container is removed automatically; $DUMP is kept —"
echo "it contains production data, so delete it once you are finished."
