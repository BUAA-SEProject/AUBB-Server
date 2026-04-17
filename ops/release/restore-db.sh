#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "用法: $0 <backup.sql.gz>" >&2
  exit 1
fi

if [[ "${AUBB_RESTORE_CONFIRM:-}" != "I_UNDERSTAND" ]]; then
  echo "[ERROR] 恢复数据库前请设置 AUBB_RESTORE_CONFIRM=I_UNDERSTAND" >&2
  exit 1
fi

if command -v docker >/dev/null 2>&1 && [[ -f deploy/compose.yaml ]] && [[ -f deploy/.env.production ]]; then
  gunzip -c "$BACKUP_FILE" | docker compose -f deploy/compose.yaml --env-file deploy/.env.production exec -T app \
    sh -c 'PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql --dbname="$SPRING_DATASOURCE_URL" --username="$SPRING_DATASOURCE_USERNAME"'
else
  : "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
  : "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
  : "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"
  gunzip -c "$BACKUP_FILE" | PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
    --dbname="$SPRING_DATASOURCE_URL" --username="$SPRING_DATASOURCE_USERNAME"
fi

echo "[OK] 数据库恢复完成: $BACKUP_FILE"
