#!/usr/bin/env sh
#
# Dump the production database, verify the dump, upload it to R2, verify the upload.
#
# Every step is checked because the failure this exists to prevent is not "the job
# errored" — that is visible. It is the job reporting success while producing a valid
# gzip of nothing, which is what a naive `pg_dump | gzip | aws s3 cp` does when pg_dump
# dies halfway: gzip still exits 0, the object still lands, and the first anyone knows
# is the day someone tries to restore it.
#
# Required environment:
#   DATABASE_URL           postgres://… for the database being backed up
#   R2_ENDPOINT            https://<account>.r2.cloudflarestorage.com
#   R2_BACKUP_BUCKET       a bucket used for NOTHING else
#   AWS_ACCESS_KEY_ID      token scoped to that bucket
#   AWS_SECRET_ACCESS_KEY
#
# Optional:
#   BACKUP_PREFIX          key prefix inside the bucket (default: db)
#   RETENTION_DAYS         delete backups older than this. UNSET MEANS NEVER DELETE —
#                          see the pruning section for why that is the default.
#   MIN_DUMP_BYTES         reject a dump smaller than this (default: 2048)
#
set -eu

# Never `set -x`, and never echo the environment: DATABASE_URL contains the database
# password and AWS_SECRET_ACCESS_KEY is a live credential.

fail() {
    echo "BACKUP FAILED: $1" >&2
    exit 1
}

for var in DATABASE_URL R2_ENDPOINT R2_BACKUP_BUCKET AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
    eval "value=\${$var:-}"
    [ -n "$value" ] || fail "$var is not set"
done

PREFIX="${BACKUP_PREFIX:-db}"
MIN_BYTES="${MIN_DUMP_BYTES:-2048}"
# R2 ignores the region but the SDK insists on one being present.
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-auto}"
S3="aws s3 --endpoint-url ${R2_ENDPOINT}"
S3API="aws s3api --endpoint-url ${R2_ENDPOINT}"

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="vetspace-${STAMP}.sql.gz"
WORK=$(mktemp -d)
# shellcheck disable=SC2064  # WORK must expand now, not at trap time.
trap "rm -rf '$WORK'" EXIT
DUMP="${WORK}/dump.sql"

echo "[1/5] pg_dump"
# To a file, NOT through a pipe, so the exit status is pg_dump's own.
# --no-owner/--no-acl: the restore target is a fresh database with a different role name,
# and ownership statements would fail on every single restore.
pg_dump "$DATABASE_URL" --no-owner --no-acl --format=plain > "$DUMP" \
    || fail "pg_dump exited non-zero"

echo "[2/5] verify dump contents"
# A dump of a live schema always ends with this marker. Its absence means the dump was
# truncated — the exact failure a pipeline would have hidden.
tail -5 "$DUMP" | grep -q "PostgreSQL database dump complete" \
    || fail "dump is truncated (no completion marker)"
# Flyway's own table is the cheapest proof that real schema came through rather than an
# empty-but-well-formed dump of the wrong database.
grep -q "flyway_schema_history" "$DUMP" \
    || fail "dump contains no flyway_schema_history — wrong database?"
TABLES=$(grep -c "^CREATE TABLE" "$DUMP" || true)
[ "$TABLES" -ge 10 ] || fail "dump defines only ${TABLES} tables, expected at least 10"

gzip -9 "$DUMP" || fail "gzip failed"
DUMP="${DUMP}.gz"
gzip -t "$DUMP" || fail "gzip archive does not verify"

SIZE=$(wc -c < "$DUMP" | tr -d ' ')
[ "$SIZE" -ge "$MIN_BYTES" ] || fail "compressed dump is only ${SIZE} bytes"
echo "      ${TABLES} tables, ${SIZE} bytes compressed"

echo "[3/5] upload"
KEY="${PREFIX}/${FILE}"
$S3 cp "$DUMP" "s3://${R2_BACKUP_BUCKET}/${KEY}" >/dev/null \
    || fail "upload to s3://${R2_BACKUP_BUCKET}/${KEY} failed"

echo "[4/5] verify upload"
# Read the object's size back from R2 rather than trusting the exit status of the copy:
# a truncated upload is the other way this job can lie about having worked.
REMOTE_SIZE=$($S3API head-object --bucket "$R2_BACKUP_BUCKET" --key "$KEY" \
    --query 'ContentLength' --output text 2>/dev/null) \
    || fail "uploaded object is not readable back"
[ "$REMOTE_SIZE" = "$SIZE" ] \
    || fail "uploaded size ${REMOTE_SIZE} != local size ${SIZE}"
echo "      s3://${R2_BACKUP_BUCKET}/${KEY} (${REMOTE_SIZE} bytes)"

echo "[5/5] retention"
# Pruning is OFF unless RETENTION_DAYS is set, and that default is deliberate: a backup
# job that deletes by default is one typo in one environment variable away from deleting
# the thing it exists to protect. Opting in is a decision someone makes once, on purpose.
if [ -n "${RETENTION_DAYS:-}" ]; then
    # Epoch arithmetic, not `date -d "-N days"`: this image is Alpine, whose busybox date
    # rejects GNU relative offsets outright. With `set -e` that failed the whole job —
    # after a successful upload — so enabling retention would have turned a working
    # backup into a red one.
    CUTOFF=$(date -u -d "@$(( $(date -u +%s) - RETENTION_DAYS * 86400 ))" +%Y%m%dT%H%M%SZ) \
        || fail "could not compute the retention cutoff"
    echo "      deleting backups older than ${CUTOFF}"
    $S3API list-objects-v2 --bucket "$R2_BACKUP_BUCKET" --prefix "${PREFIX}/" \
        --query 'Contents[].Key' --output text 2>/dev/null | tr '\t' '\n' | while read -r old; do
        [ -n "$old" ] || continue
        # The timestamp is in the key itself, so pruning needs no clock but the one that
        # wrote it — no dependence on R2's LastModified or on this container's timezone.
        old_stamp=$(echo "$old" | sed -n 's/.*vetspace-\([0-9TZ]*\)\.sql\.gz/\1/p')
        [ -n "$old_stamp" ] || continue
        if [ "$old_stamp" \< "$CUTOFF" ]; then
            echo "      pruning ${old}"
            $S3API delete-object --bucket "$R2_BACKUP_BUCKET" --key "$old" >/dev/null || true
        fi
    done
else
    echo "      RETENTION_DAYS unset — keeping everything"
fi

echo "--- most recent backups ---"
$S3 ls "s3://${R2_BACKUP_BUCKET}/${PREFIX}/" | tail -5

echo "BACKUP OK ${KEY}"
