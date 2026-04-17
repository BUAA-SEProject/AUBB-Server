#!/usr/bin/env bash
set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
  echo "[ERROR] 需要 curl 来导出 OpenAPI 文档" >&2
  exit 1
fi

OPENAPI_URL="${AUBB_OPENAPI_URL:-http://localhost:8080/v3/api-docs}"
OUTPUT_PATH="${AUBB_OPENAPI_OUTPUT_PATH:-docs/openapi/aubb-openapi.json}"
TMP_FILE="$(mktemp)"

mkdir -p "$(dirname "$OUTPUT_PATH")"
curl -fsS "$OPENAPI_URL" > "$TMP_FILE"

if command -v jq >/dev/null 2>&1; then
  jq '.' "$TMP_FILE" > "$OUTPUT_PATH"
else
  mv "$TMP_FILE" "$OUTPUT_PATH"
  TMP_FILE=""
fi

if [[ -n "$TMP_FILE" && -f "$TMP_FILE" ]]; then
  rm -f "$TMP_FILE"
fi

echo "[OK] 已导出静态 OpenAPI: $OUTPUT_PATH"
