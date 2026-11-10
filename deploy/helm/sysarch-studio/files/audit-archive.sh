#!/bin/sh
# Audit retention (P2 T8, spec §7.2 "保留 365 天 … 只增不删"; user's decision "数据库禁改删 + 超期归档"): rows older
# than 365 days are exported to a CSV with its SHA-256 first, then removed in one transaction that sets
# sysarch.audit_archive = 'on', the only way the trigger of change set 016 lets a DELETE through (and only for rows
# past the retention). The delete is refused unless it removes exactly the rows exported.
#
#   ARCHIVE_DIR=/backups/audit sh audit-archive.sh
set -eu

ARCHIVE_DIR="${ARCHIVE_DIR:?set ARCHIVE_DIR}"
CUTOFF="$(psql -Atc "SELECT to_char(now() - INTERVAL '365 days', 'YYYY-MM-DD\"T\"HH24:MI:SSOF')")"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$ARCHIVE_DIR"
FILE="$ARCHIVE_DIR/audit_log-before-$STAMP.csv"

psql -v ON_ERROR_STOP=1 -c "\\copy (SELECT * FROM audit_log WHERE created_at < '$CUTOFF' ORDER BY id) TO '$FILE' WITH (FORMAT csv, HEADER true)"
EXPORTED="$(($(wc -l < "$FILE") - 1))"
if [ "$EXPORTED" -le 0 ]; then
  rm -f "$FILE"
  echo "audit archive: nothing older than $CUTOFF"
  exit 0
fi
( cd "$ARCHIVE_DIR" && sha256sum "$(basename "$FILE")" > "$(basename "$FILE").sha256" )

psql -v ON_ERROR_STOP=1 <<SQL
BEGIN;
SET LOCAL sysarch.audit_archive = 'on';
DO \$\$
DECLARE removed bigint;
BEGIN
  DELETE FROM audit_log WHERE created_at < '$CUTOFF';
  GET DIAGNOSTICS removed = ROW_COUNT;
  IF removed <> $EXPORTED THEN
    RAISE EXCEPTION 'exported % rows but would delete %: nothing deleted', $EXPORTED, removed;
  END IF;
END
\$\$;
COMMIT;
SQL
echo "audit archive: $EXPORTED rows before $CUTOFF -> $FILE"
