#!/usr/bin/env bash
set -euo pipefail

GW=http://localhost:8080
fail() { echo "SMOKE FAIL: $1" >&2; exit 1; }
expect_status() { # expected actual label
  [ "$1" = "$2" ] || fail "$3: expected HTTP $1, got $2"
  echo "OK: $3 -> $2"
}

post_event() { # eventId ts -> status code
  curl -s -o /tmp/smoke-body -w '%{http_code}' -X POST "$GW/api/v1/events" \
    -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$1\",\"accountId\":\"acc-smoke\",\"type\":\"CREDIT\",\"amount\":10,\"currency\":\"EUR\",\"eventTimestamp\":\"$2\"}"
}

echo "== waiting for stack =="
for i in $(seq 1 60); do
  curl -sf "$GW/health" >/dev/null && break
  [ "$i" = 60 ] && fail "gateway never became healthy"
  sleep 2
done

echo "== submit =="
expect_status 201 "$(post_event evt-s1 2026-07-02T10:00:00Z)" "new event"

echo "== duplicate =="
expect_status 200 "$(post_event evt-s1 2026-07-02T10:00:00Z)" "duplicate event"

echo "== out-of-order =="
expect_status 201 "$(post_event evt-s0 2026-07-01T10:00:00Z)" "earlier event arriving later"
first=$(curl -s "$GW/api/v1/events?account=acc-smoke" | grep -o '"eventId":"[^"]*"' | head -1)
[ "$first" = '"eventId":"evt-s0"' ] || fail "listing not chronological: first=$first"
echo "OK: chronological listing"

echo "== kill account service -> queued =="
docker compose stop account-service
expect_status 202 "$(post_event evt-s2 2026-07-03T10:00:00Z)" "event while down"

echo "== restart -> replayed =="
docker compose start account-service
for i in $(seq 1 30); do
  status=$(curl -s "$GW/api/v1/events/evt-s2" | grep -o '"status":"[^"]*"')
  [ "$status" = '"status":"APPLIED"' ] && break
  [ "$i" = 30 ] && fail "evt-s2 never replayed: $status"
  sleep 2
done
echo "OK: replayed to APPLIED"

echo "== balance =="
# Right after replay the circuit breaker may still be half-open and reject
# the first proxied query with 503 — retry briefly instead of dying on it.
balance=""
for i in $(seq 1 15); do
  body=$(curl -s "$GW/api/v1/accounts/acc-smoke/balance")
  balance=$(echo "$body" | grep -o '"balance":[0-9.]*' || echo "none ($body)")
  [[ "$balance" =~ ^\"balance\":30(\.0+)?$ ]] && break
  [ "$i" = 15 ] && fail "balance wrong: $balance"
  sleep 2
done
echo "OK: balance 30"

echo "SMOKE PASS"
