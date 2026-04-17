#!/usr/bin/env bash
set -euo pipefail

TARGET_BASE_URL="${AUBB_RELEASE_BASE_URL:-http://localhost:8080}"
SMOKE_USER="${AUBB_SMOKE_USERNAME:-}"
SMOKE_PASSWORD="${AUBB_SMOKE_PASSWORD:-}"

if [[ -z "$SMOKE_USER" || -z "$SMOKE_PASSWORD" ]]; then
  echo "[SKIP] 未提供 AUBB_SMOKE_USERNAME / AUBB_SMOKE_PASSWORD，跳过 API smoke"
  exit 0
fi

BASE_URL="${TARGET_BASE_URL%/}"
TOKEN_FILE="$(mktemp)"
COURSES_FILE="$(mktemp)"
trap 'rm -f "$TOKEN_FILE" "$COURSES_FILE"' EXIT

echo "[STEP] 登录 smoke 账号"
curl -fsS "${BASE_URL}/api/v1/auth/login" \
  -H 'content-type: application/json' \
  -d "{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASSWORD}\"}" >"$TOKEN_FILE"

ACCESS_TOKEN="$(python3 - <<'PY' "$TOKEN_FILE"
import json
import pathlib
import sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text())
print(payload["accessToken"])
PY
)"

AUTH_HEADER="Authorization: Bearer ${ACCESS_TOKEN}"

echo "[STEP] 检查当前用户"
curl -fsS "${BASE_URL}/api/v1/auth/me" -H "$AUTH_HEADER" >/dev/null

echo "[STEP] 检查我的课程与通知"
curl -fsS "${BASE_URL}/api/v1/me/courses" -H "$AUTH_HEADER" >"$COURSES_FILE"
curl -fsS "${BASE_URL}/api/v1/me/notifications/unread-count" -H "$AUTH_HEADER" >/dev/null
curl -fsS "${BASE_URL}/api/v1/me/assignments" -H "$AUTH_HEADER" >/dev/null

FIRST_CLASS_ID="$(python3 - <<'PY' "$COURSES_FILE"
import json
import pathlib
import sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text())
for course in payload:
    for class_item in course.get("classes", []):
        class_id = class_item.get("id")
        if class_id is not None:
            print(class_id)
            raise SystemExit(0)
raise SystemExit(1)
PY
)" || true

if [[ -n "${FIRST_CLASS_ID:-}" ]]; then
  echo "[STEP] 检查课程公告与资源列表"
  curl -fsS "${BASE_URL}/api/v1/me/course-classes/${FIRST_CLASS_ID}/announcements" -H "$AUTH_HEADER" >/dev/null
  curl -fsS "${BASE_URL}/api/v1/me/course-classes/${FIRST_CLASS_ID}/resources" -H "$AUTH_HEADER" >/dev/null
fi

echo "[OK] API smoke 完成"
