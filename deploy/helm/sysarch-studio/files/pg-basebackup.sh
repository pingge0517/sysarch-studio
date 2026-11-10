#!/bin/sh
# Weekly base backup for WAL replay (P2 T8): pg_basebackup (tar, compressed, with the WAL it needs) into
# BACKUP_DIR/base-<stamp>; base backups older than RETENTION_DAYS are removed. With the archived WAL (kept
# RETENTION_DAYS + 7 days next to the database), any point of the last 30 days can be restored (docs/runbook.md
# "时间点恢复"). The role needs REPLICATION and pg_hba.conf a "replication" line (the chart adds both).
#
#   BACKUP_DIR=/backups RETENTION_DAYS=30 sh pg-basebackup.sh
set -eu

BACKUP_DIR="${BACKUP_DIR:?set BACKUP_DIR}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TARGET="$BACKUP_DIR/base-$(date -u +%Y%m%dT%H%M%SZ)"

pg_basebackup --pgdata="$TARGET" --format=tar --gzip --checkpoint=fast --wal-method=fetch
echo "base backup: $TARGET"
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name 'base-*' -mtime +"$RETENTION_DAYS" -print -exec rm -rf {} +
