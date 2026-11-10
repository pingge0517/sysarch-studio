#!/bin/sh
# Daily full backup of the SysArch database (P2 T8, spec §7.3: "PG 每日全量 + WAL 归档，保留 30 天").
# A custom-format pg_dump (restorable with pg_restore, also into a newer PostgreSQL) plus its SHA-256, then every
# dump older than RETENTION_DAYS is removed. Connection: the usual libpq variables PGHOST, PGPORT, PGDATABASE, PGUSER,
# PGPASSWORD (or ~/.pgpass); nothing secret on the command line.
#
#   BACKUP_DIR=/backups RETENTION_DAYS=30 sh pg-backup.sh
set -eu

BACKUP_DIR="${BACKUP_DIR:?set BACKUP_DIR}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
DATABASE="${PGDATABASE:-sysarch}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/$DATABASE-$STAMP.dump"

pg_dump --format=custom --compress=6 --no-owner --file="$FILE.partial" "$DATABASE"
mv "$FILE.partial" "$FILE"
( cd "$BACKUP_DIR" && sha256sum "$(basename "$FILE")" > "$(basename "$FILE").sha256" )
echo "backup: $FILE ($(wc -c < "$FILE") bytes)"

# Retention: dumps and checksums older than RETENTION_DAYS days.
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name "$DATABASE-*.dump" -o -name "$DATABASE-*.dump.sha256" \) -mtime +"$RETENTION_DAYS" -print -delete
