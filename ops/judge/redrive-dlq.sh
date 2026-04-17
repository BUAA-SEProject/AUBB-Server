#!/usr/bin/env bash
set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
  echo "[ERROR] 需要 curl 来回放 RabbitMQ DLQ" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[ERROR] 需要 jq 来解析 RabbitMQ 管理 API 响应" >&2
  exit 1
fi

MAIN_QUEUE="${AUBB_JUDGE_QUEUE_NAME:-aubb.judge.jobs}"
DLQ_QUEUE="${AUBB_JUDGE_QUEUE_DLQ_NAME:-aubb.judge.jobs.dlq}"
MANAGEMENT_API="${AUBB_RABBITMQ_MANAGEMENT_URL:-http://localhost:15672/api}"
RABBIT_USER="${SPRING_RABBITMQ_USERNAME:-${AUBB_RABBITMQ_USER:-guest}}"
RABBIT_PASSWORD="${SPRING_RABBITMQ_PASSWORD:-${AUBB_RABBITMQ_PASSWORD:-guest}}"
LIMIT="${AUBB_JUDGE_DLQ_REDRIVE_LIMIT:-50}"
count=0

while (( count < LIMIT )); do
  response="$(curl -fsS -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
    -H 'content-type: application/json' \
    -X POST \
    "${MANAGEMENT_API%/}/queues/%2F/${DLQ_QUEUE}/get" \
    -d '{"count":1,"ackmode":"ack_requeue_false","encoding":"auto","truncate":500000}')"

  if [[ "$(jq 'length' <<<"$response")" == "0" ]]; then
    break
  fi

  payload="$(jq -r '.[0].payload' <<<"$response")"
  properties="$(jq '.[0].properties // {}' <<<"$response")"
  publish_body="$(jq -n \
    --arg routing_key "$MAIN_QUEUE" \
    --arg payload "$payload" \
    --argjson properties "$properties" \
    '{properties:$properties,routing_key:$routing_key,payload:$payload,payload_encoding:"string"}')"

  curl -fsS -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
    -H 'content-type: application/json' \
    -X POST \
    "${MANAGEMENT_API%/}/exchanges/%2F/amq.default/publish" \
    -d "$publish_body" >/dev/null

  count=$((count + 1))
done

echo "[OK] 已从 ${DLQ_QUEUE} 回放 ${count} 条消息到 ${MAIN_QUEUE}"
