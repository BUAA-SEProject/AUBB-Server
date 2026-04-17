#!/usr/bin/env bash
set -euo pipefail

TARGET_BASE_URL="${AUBB_RELEASE_BASE_URL:-http://localhost:8080}"
SMOKE_USER="${AUBB_SMOKE_USERNAME:-}"
SMOKE_PASSWORD="${AUBB_SMOKE_PASSWORD:-}"

echo "[STEP] 运行运行时预检"
bash ops/preflight/check-runtime-deps.sh

echo "[STEP] 检查应用 readiness"
curl -fsS "${TARGET_BASE_URL%/}/actuator/health/readiness" >/dev/null

echo "[STEP] 导出静态 OpenAPI 产物"
AUBB_OPENAPI_URL="${TARGET_BASE_URL%/}/v3/api-docs" bash ops/openapi/export-static.sh

if [[ -n "$SMOKE_USER" && -n "$SMOKE_PASSWORD" ]]; then
  echo "[STEP] 执行 API smoke"
  bash ops/release/smoke-api.sh
fi

echo "[OK] 发布演练完成"
