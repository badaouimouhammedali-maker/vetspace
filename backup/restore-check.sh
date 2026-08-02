#!/usr/bin/env sh
#
# Restore the most recent backup into a throwaway database and report what came back.
#
# A backup you have never restored is a hypothesis. This is the experiment, and it lives
# next to the thing it tests so the two cannot drift apart: same image, same aws-cli,
# same pg version.
#
# Read-only with respect to the backup bucket and to whatever database produced it. The
# ONLY thing it writes is a uniquely-named scratch database on the server you point
# SCRATCH_ADMIN_URL at, which it drops again on the way out.
#
# Required environment:
#   R2_ENDPOINT, R2_BACKUP_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
#   SCRATCH_ADMIN_URL   postgres://…/postgres on a server that is NOT production
#
# Optional:
#   BACKUP_PREFIX       default: db
#   BACKUP_KEY          restore this specific key instead of the newest
#
set -eu

fail() {
    echo "RESTORE CHECK FAILED: $1" >&2
    exit 1
}

for var in R2_ENDPOINT R2_BACKUP_BUCKET AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY SCRATCH_ADMIN_URL; do
    eval "value=\${$var:-}"
    [ -n "$value" ] || fail "$var is not set"
done

PREFIX="${BACKUP_PREFIX:-db}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-auto}"
S3="aws s3 --endpoint-url ${R2_ENDPOINT}"

WORK=$(mktemp -d)
# A name nothing else could plausibly own, so the DROP at the end can never be aimed at
# something real.
SCRATCH="restore_check_$(date -u +%Y%m%d%H%M%S)_$$"
cleanup() {
    psql "$SCRATCH_ADMIN_URL" -q -c "DROP DATABASE IF EXISTS ${SCRATCH}" >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

echo "[1/4] find the backup to restore"
if [ -n "${BACKUP_KEY:-}" ]; then
    KEY="$BACKUP_KEY"
else
    # Keys are timestamped, so a plain sort is chronological — no reliance on the
    # bucket's LastModified, which a re-upload would disturb.
    NEWEST=$($S3 ls "s3://${R2_BACKUP_BUCKET}/${PREFIX}/" | awk '{print $4}' | sort | tail -1)
    [ -n "$NEWEST" ] || fail "no backups found under ${PREFIX}/"
    KEY="${PREFIX}/${NEWEST}"
fi
echo "      ${KEY}"

echo "[2/4] download and decompress"
$S3 cp "s3://${R2_BACKUP_BUCKET}/${KEY}" "${WORK}/dump.sql.gz" >/dev/null \
    || fail "download failed"
gzip -t "${WORK}/dump.sql.gz" || fail "downloaded archive does not verify"
gunzip "${WORK}/dump.sql.gz" || fail "gunzip failed"

echo "[3/4] restore into ${SCRATCH}"
psql "$SCRATCH_ADMIN_URL" -q -c "CREATE DATABASE ${SCRATCH}" >/dev/null \
    || fail "could not create the scratch database"
# The scratch URL points at the maintenance database; swap in the scratch name.
SCRATCH_URL=$(echo "$SCRATCH_ADMIN_URL" | sed "s|/[^/?]*\(?.*\)\{0,1\}$|/${SCRATCH}|")
# ON_ERROR_STOP: psql otherwise carries on past a failed statement and exits 0, which
# would make a half-restored database look like a successful one.
psql "$SCRATCH_URL" -q -v ON_ERROR_STOP=1 -f "${WORK}/dump.sql" >/dev/null \
    || fail "restore reported errors"

echo "[4/4] what came back"
psql "$SCRATCH_URL" -q -c "
-- By installed_rank, not max(version): version is varchar, so max() compares as text and
-- reports '9' for a database at V11. Misreading the schema version is not a cosmetic
-- problem during a recovery — it is the number you decide what to do next from.
select 'flyway version' as item,
       (select version from flyway_schema_history order by installed_rank desc limit 1) as value
union all select 'schools',      count(*)::text from schools
union all select 'users',        count(*)::text from users
union all select 'modules',      count(*)::text from modules
union all select 'courses',      count(*)::text from courses
union all select 'questions',    count(*)::text from questions
union all select 'propositions', count(*)::text from propositions
union all select 'sessions',     count(*)::text from sessions
union all select 'subscriptions', count(*)::text from subscriptions
union all select 'resources',    count(*)::text from resources
order by 1;" || fail "the restored database does not answer queries"

FAILED=$(psql "$SCRATCH_URL" -tAc \
    "select count(*) from flyway_schema_history where success = false") \
    || fail "cannot read flyway_schema_history"
[ "$FAILED" = "0" ] || fail "${FAILED} failed migration(s) recorded in the restored database"

echo "RESTORE CHECK OK — ${KEY} restores cleanly"
