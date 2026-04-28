#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:18080}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/tmp/aubb-permission-e2e}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-180}"
ADMIN_USERNAME="${ADMIN_USERNAME:-U-SA1}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Password123}"

mkdir -p "${ARTIFACT_DIR}"

deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
until curl -fsS "${BASE_URL}/actuator/health/readiness" >/dev/null; do
  if (( SECONDS >= deadline )); then
    echo "等待服务就绪超时: ${BASE_URL}/actuator/health/readiness" >&2
    exit 1
  fi
  sleep 3
done

python3 "${ROOT_DIR}/scripts/api-tests/permission/e2e_permission_realrun.py" \
  --base-url "${BASE_URL}" \
  --out "${ARTIFACT_DIR}" \
  --admin-username "${ADMIN_USERNAME}" \
  --admin-password "${ADMIN_PASSWORD}"
