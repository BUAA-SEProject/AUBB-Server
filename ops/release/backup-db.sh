#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP="${BACKUP_TIMESTAMP:-$(date +%Y%m%d-%H%M%S)}"
OUTPUT_DIR="${AUBB_BACKUP_DIR:-backups}"
OUTPUT_FILE="${OUTPUT_DIR}/aubb-${TIMESTAMP}.sql.gz"

mkdir -p "$OUTPUT_DIR"

if command -v docker >/dev/null 2>&1 && [[ -f deploy/compose.yaml ]] && [[ -f deploy/.env.production ]]; then
  docker compose -f deploy/compose.yaml --env-file deploy/.env.production exec -T app \
    sh -c 'PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_dump --clean --if-exists --no-owner --no-privileges \
      --dbname="$SPRING_DATASOURCE_URL" --username="$SPRING_DATASOURCE_USERNAME"' | gzip -c > "$OUTPUT_FILE"
else
  : "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
  : "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
  : "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"
  PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_dump --clean --if-exists --no-owner --no-privileges \
    --dbname="$SPRING_DATASOURCE_URL" --username="$SPRING_DATASOURCE_USERNAME" | gzip -c > "$OUTPUT_FILE"
fi

echo "[OK] 数据库备份已生成: $OUTPUT_FILE"
