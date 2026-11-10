#!/bin/sh
# Restores a dump of pg-backup.sh into a NEW, empty database (P2 T8 runbook "恢复"). It never overwrites an existing
# database: restore next to production, start a backend against it, check it, then switch (docs/runbook.md).
# Connection: PGHOST, PGPORT, PGUSER, PGPASSWORD of a role that may create databases.
#
#   sh pg-restore.sh /backups/sysarch-20261110T023000Z.dump sysarch_restored [owner]
set -eu

DUMP="${1:?usage: pg-restore.sh <dump> <new database> [owner]}"
TARGET="${2:?usage: pg-restore.sh <dump> <new database> [owner]}"
OWNER="${3:-${PGUSER:-sysarch}}"

if [ -f "$DUMP.sha256" ]; then
  ( cd "$(dirname "$DUMP")" && sha256sum -c "$(basename "$DUMP").sha256" )
else
  echo "warning: no $DUMP.sha256, the dump is not verified" >&2
fi
if [ "$(psql -d postgres -Atc "SELECT count(*) FROM pg_database WHERE datname = '$TARGET'")" != "0" ]; then
  echo "database $TARGET exists: restore into a new database" >&2
  exit 1
fi
psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$TARGET\" OWNER \"$OWNER\""
pg_restore --exit-on-error --no-owner --role="$OWNER" --dbname="$TARGET" "$DUMP"
psql -d "$TARGET" -Atc "SELECT 'restored: ' || (SELECT count(*) FROM project) || ' projects, ' || (SELECT count(*) FROM document) || ' documents, '
  || (SELECT count(*) FROM baseline) || ' baselines, ' || (SELECT count(*) FROM audit_log) || ' audit rows'"
