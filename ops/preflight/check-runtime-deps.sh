#!/usr/bin/env bash
set -euo pipefail

missing=0

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] 缺少命令: $1" >&2
    missing=1
  fi
}

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "[ERROR] 缺少环境变量: $name" >&2
    missing=1
  fi
}

check_http() {
  local url="$1"
  local label="$2"
  if ! curl -fsS "$url" >/dev/null; then
    echo "[ERROR] $label 不可达: $url" >&2
    missing=1
  else
    echo "[OK] $label 可达: $url"
  fi
}

check_url_shape() {
  local url="$1"
  local label="$2"
  if [[ "$url" =~ ^https?://[^[:space:]]+$ ]]; then
    echo "[OK] $label 格式合法: $url"
  else
    echo "[ERROR] $label 地址格式非法: $url" >&2
    missing=1
  fi
}

check_tcp() {
  local host="$1"
  local port="$2"
  local label="$3"
  local probe_cmd="cat < /dev/null > /dev/tcp/${host}/${port}"
  if command -v timeout >/dev/null 2>&1; then
    if timeout 3 bash -c "$probe_cmd" 2>/dev/null; then
      echo "[OK] $label TCP 可达: ${host}:${port}"
    else
      echo "[ERROR] $label TCP 不可达: ${host}:${port}" >&2
      missing=1
    fi
    return
  fi

  if bash -c "$probe_cmd" 2>/dev/null; then
    echo "[OK] $label TCP 可达: ${host}:${port}"
  else
    echo "[ERROR] $label TCP 不可达: ${host}:${port}" >&2
    missing=1
  fi
}

require_cmd curl
require_var SPRING_DATASOURCE_URL
require_var AUBB_JWT_SECRET

if [[ "${AUBB_MINIO_ENABLED:-false}" == "true" ]]; then
  require_var AUBB_MINIO_ENDPOINT
  require_var AUBB_MINIO_ACCESS_KEY
  require_var AUBB_MINIO_SECRET_KEY
  require_var AUBB_MINIO_BUCKET
  check_http "${AUBB_MINIO_ENDPOINT%/}/minio/health/live" "MinIO"
fi

if [[ "${AUBB_GO_JUDGE_ENABLED:-false}" == "true" ]]; then
  require_var AUBB_GO_JUDGE_BASE_URL
  check_http "${AUBB_GO_JUDGE_BASE_URL%/}/version" "go-judge"
fi

if [[ "${AUBB_REDIS_ENABLED:-false}" == "true" ]]; then
  require_var AUBB_REDIS_HOST
  require_var AUBB_REDIS_PORT
  require_var AUBB_REDIS_NAMESPACE
  check_tcp "${AUBB_REDIS_HOST}" "${AUBB_REDIS_PORT}" "Redis"
fi

if [[ "${AUBB_JUDGE_QUEUE_ENABLED:-false}" == "true" ]]; then
  require_var SPRING_RABBITMQ_HOST
  require_var AUBB_JUDGE_QUEUE_NAME
  require_var AUBB_JUDGE_QUEUE_DLQ_NAME
fi

if [[ -n "${AUBB_OTLP_TRACING_ENDPOINT:-}" ]]; then
  check_url_shape "$AUBB_OTLP_TRACING_ENDPOINT" "OTLP tracing endpoint"
fi

if (( missing > 0 )); then
  echo "[FAIL] 运行时依赖预检失败" >&2
  exit 1
fi

echo "[OK] 运行时依赖预检通过"
