#!/usr/bin/env bash
set -euo pipefail

ROLLBACK_IMAGE="${1:-${AUBB_ROLLBACK_IMAGE:-}}"
ENV_FILE="${AUBB_DEPLOY_ENV_FILE:-deploy/.env.production}"
COMPOSE_FILE="${AUBB_DEPLOY_COMPOSE_FILE:-deploy/compose.yaml}"
HEALTH_URL="${AUBB_HEALTHCHECK_URL:-http://localhost:${AUBB_APP_PORT:-8080}/actuator/health/readiness}"
BACKUP_ENV_FILE="${ENV_FILE}.rollback.$(date +%Y%m%d-%H%M%S).bak"

if [[ -z "$ROLLBACK_IMAGE" ]]; then
  echo "[ERROR] 用法: $0 <rollback-image-tag-or-name>" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] 缺少环境文件: $ENV_FILE" >&2
  exit 1
fi

cp "$ENV_FILE" "$BACKUP_ENV_FILE"
python3 - "$ENV_FILE" "$ROLLBACK_IMAGE" <<'PY'
from pathlib import Path
import sys

env_file = Path(sys.argv[1])
rollback_image = sys.argv[2]
lines = env_file.read_text(encoding="utf-8").splitlines()
updated = False
for index, line in enumerate(lines):
    if line.startswith("AUBB_APP_IMAGE="):
        lines[index] = f"AUBB_APP_IMAGE={rollback_image}"
        updated = True
        break
if not updated:
    lines.append(f"AUBB_APP_IMAGE={rollback_image}")
env_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

set -a
source "$ENV_FILE"
set +a

echo "[STEP] 运行回滚前预检"
bash ops/preflight/check-runtime-deps.sh

echo "[STEP] 拉取并启动回滚镜像: $ROLLBACK_IMAGE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" pull app judge-worker
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d app judge-worker

echo "[STEP] 等待 readiness 恢复"
for attempt in $(seq 1 24); do
  if curl -fsS "$HEALTH_URL" >/dev/null; then
    echo "[OK] 回滚完成，readiness 已恢复"
    echo "[INFO] 原环境文件备份: $BACKUP_ENV_FILE"
    exit 0
  fi
  sleep 5
done

echo "[ERROR] 回滚后 readiness 未恢复，请检查 app / judge-worker 日志" >&2
exit 1
