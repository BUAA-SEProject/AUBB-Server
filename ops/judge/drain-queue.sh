#!/usr/bin/env bash
set -euo pipefail

QUEUE_NAME="${1:-${AUBB_JUDGE_QUEUE_NAME:-aubb.judge.jobs}}"
MANAGEMENT_API="${AUBB_RABBITMQ_MANAGEMENT_URL:-http://localhost:15672/api}"
RABBIT_USER="${SPRING_RABBITMQ_USERNAME:-${AUBB_RABBITMQ_USER:-guest}}"
RABBIT_PASSWORD="${SPRING_RABBITMQ_PASSWORD:-${AUBB_RABBITMQ_PASSWORD:-guest}}"

if ! command -v curl >/dev/null 2>&1; then
  echo "[ERROR] 需要 curl 来清空 RabbitMQ 队列" >&2
  exit 1
fi

curl -fsS -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
  -H 'content-type: application/json' \
  -X DELETE \
  "${MANAGEMENT_API%/}/queues/%2F/${QUEUE_NAME}/contents" >/dev/null

echo "[OK] 已清空队列: ${QUEUE_NAME}"
