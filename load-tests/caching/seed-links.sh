#!/usr/bin/env bash
set -euo pipefail

SCRAPPER_BASE_URL="${SCRAPPER_BASE_URL:-http://localhost:8081}"
CHAT_START="${CHAT_START:-1}"
CHAT_COUNT="${CHAT_COUNT:-1000}"
LINKS_PER_CHAT="${LINKS_PER_CHAT:-100}"
TAG="${TAG:-load}"

for ((chat_id = CHAT_START; chat_id < CHAT_START + CHAT_COUNT; chat_id++)); do
  curl -fsS -X POST "${SCRAPPER_BASE_URL}/tg-chat/${chat_id}" >/dev/null || true
  for ((link_no = 1; link_no <= LINKS_PER_CHAT; link_no++)); do
    url="https://load.example/chat-${chat_id}/link-${link_no}"
    payload=$(printf '{"link":"%s","tags":["%s"],"filters":[]}' "${url}" "${TAG}")
    curl -fsS -X POST "${SCRAPPER_BASE_URL}/links" \
      -H "Content-Type: application/json" \
      -H "Tg-Chat-Id: ${chat_id}" \
      -d "${payload}" >/dev/null || true
  done
done
