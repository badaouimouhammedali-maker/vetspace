# Scheduled database backup, deployed as its own Railway service.
#
# Separate from the API on purpose: a bad deploy, a bad migration or a compromised
# application credential must not be able to take the backups with it. This image has no
# HTTP port, no application code, and its R2 token is scoped to the backup bucket alone.
#
# MUST match the major version of the server you would restore INTO — production. This
# started as 17 on the reasoning that pg_dump can dump older servers but refuses newer
# ones, so a newer client is the safe direction. That is true for taking the dump and
# irrelevant to using it: pg_dump 17 emits `SET transaction_timeout`, a parameter that
# does not exist before 17, so every such dump fails on the first statement when restored
# into the 16 server it came from. restore-check.sh caught exactly that. A backup you
# cannot restore where you need it is not a backup.
#
# So: pinned to production's major version, and when Railway's Postgres is upgraded this
# line moves in the same change. The failure mode of forgetting is loud — pg_dump refuses
# a server newer than itself and the job exits non-zero — which is the right direction for
# a mistake to fail in.
ARG PG_MAJOR_VERSION=18
FROM postgres:${PG_MAJOR_VERSION}-alpine

# aws-cli from Alpine's signed package repository rather than a binary curled from the
# internet at build time — the thing that protects the backups should not have a supply
# chain of its own.
RUN apk add --no-cache aws-cli

COPY backup.sh restore-check.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore-check.sh

# Runs as an unprivileged user; nothing here needs root. The postgres image already
# defines this account.
USER postgres

# Railway runs the container on its cron schedule; the process exits when the backup is
# done, and a non-zero exit is what surfaces a failed backup in the dashboard.
ENTRYPOINT ["/usr/local/bin/backup.sh"]
