#!/usr/bin/env python3
"""End-to-end smoke test against the docker compose stack.

Exercises every functional requirement:
  1. submit            -> 201
  2. idempotency       -> duplicate returns 200 with the ORIGINAL event body
  3. out-of-order      -> earlier-timestamped event accepted later; listing chronological
  4. balance math      -> CREDITs minus DEBITs
  5. validation        -> missing field / zero / negative amount / unknown type -> 400
  6. degradation       -> Account Service down: 202 QUEUED; restart: replayed to APPLIED
  7. final balance     -> correct after replay

Stdlib only (urllib/json/subprocess) — no pip install needed.
Uses a unique account per run, so it can be re-run against a live stack.

Usage:
    docker compose up -d --build
    ./smoke_test.py
    docker compose down
"""

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

GATEWAY = "http://localhost:8080"
RUN_ID = str(int(time.time()))
ACCOUNT = f"acc-smoke-{RUN_ID}"


def fail(message):
    print(f"SMOKE FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def ok(label):
    print(f"OK: {label}")


def request(method, path, payload=None):
    """Return (status_code, parsed_json_or_None). Never raises on HTTP errors."""
    req = urllib.request.Request(
        GATEWAY + path,
        method=method,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read()
            return resp.status, json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        body = e.read()
        try:
            return e.code, json.loads(body) if body else None
        except json.JSONDecodeError:
            return e.code, None


def post_event(event_id, event_type, amount, timestamp, **overrides):
    payload = {
        "eventId": event_id,
        "accountId": ACCOUNT,
        "type": event_type,
        "amount": amount,
        "currency": "EUR",
        "eventTimestamp": timestamp,
    }
    payload.update(overrides)
    payload = {k: v for k, v in payload.items() if v is not None}
    return request("POST", "/api/v1/events", payload)


def expect(expected_status, actual_status, label):
    if actual_status != expected_status:
        fail(f"{label}: expected HTTP {expected_status}, got {actual_status}")
    ok(f"{label} -> {actual_status}")


def get_balance():
    # Right after replay the circuit breaker may still be half-open and
    # reject the proxied query with 503 — return None so the caller retries.
    status, body = request("GET", f"/api/v1/accounts/{ACCOUNT}/balance")
    return float(body["balance"]) if status == 200 else None


def expect_balance(expected, label, attempts=15):
    actual = None
    for _ in range(attempts):
        actual = get_balance()
        if actual is not None and abs(actual - expected) < 1e-9:
            ok(f"{label}: balance {actual:g}")
            return
        time.sleep(2)
    fail(f"{label}: expected balance {expected}, got {actual}")


def compose(action):
    subprocess.run(["docker", "compose", action, "account-service"], check=True)


def main():
    print("== waiting for stack ==")
    for attempt in range(60):
        try:
            urllib.request.urlopen(GATEWAY + "/health", timeout=2)
            break
        except (urllib.error.URLError, OSError):
            time.sleep(2)
    else:
        fail("gateway never became healthy")

    print("== submit ==")
    status, _ = post_event("evt-%s-1" % RUN_ID, "CREDIT", 10, "2026-07-02T10:00:00Z")
    expect(201, status, "new CREDIT event")

    print("== idempotency ==")
    status, body = post_event("evt-%s-1" % RUN_ID, "CREDIT", 10, "2026-07-02T10:00:00Z")
    expect(200, status, "duplicate submission")
    if body.get("eventId") != "evt-%s-1" % RUN_ID:
        fail(f"duplicate did not return original event: {body}")
    ok("duplicate returns original event body")
    expect_balance(10, "balance unchanged by duplicate", attempts=1)

    print("== out-of-order ==")
    status, _ = post_event("evt-%s-0" % RUN_ID, "CREDIT", 10, "2026-07-01T10:00:00Z")
    expect(201, status, "earlier-timestamped event arriving later")
    status, events = request("GET", f"/api/v1/events?account={ACCOUNT}")
    timestamps = [e["eventTimestamp"] for e in events]
    if timestamps != sorted(timestamps):
        fail(f"listing not chronological: {timestamps}")
    if events[0]["eventId"] != "evt-%s-0" % RUN_ID:
        fail(f"expected earlier event first, got {events[0]['eventId']}")
    ok("listing is chronological by eventTimestamp")

    print("== balance math (CREDIT - DEBIT) ==")
    status, _ = post_event("evt-%s-2" % RUN_ID, "DEBIT", 4, "2026-07-02T12:00:00Z")
    expect(201, status, "DEBIT event")
    expect_balance(16, "10 + 10 - 4")

    print("== validation ==")
    status, _ = post_event("evt-%s-v1" % RUN_ID, "CREDIT", 10,
                           "2026-07-01T10:00:00Z", accountId=None)
    expect(400, status, "missing accountId")
    status, _ = post_event("evt-%s-v2" % RUN_ID, "CREDIT", 0, "2026-07-01T10:00:00Z")
    expect(400, status, "zero amount")
    status, _ = post_event("evt-%s-v3" % RUN_ID, "DEBIT", -5, "2026-07-01T10:00:00Z")
    expect(400, status, "negative amount")
    status, _ = post_event("evt-%s-v4" % RUN_ID, "TRANSFER", 10, "2026-07-01T10:00:00Z")
    expect(400, status, "unknown event type")
    expect_balance(16, "balance untouched by rejected events", attempts=1)

    print("== kill account service -> queued ==")
    compose("stop")
    status, body = post_event("evt-%s-3" % RUN_ID, "CREDIT", 10, "2026-07-03T10:00:00Z")
    expect(202, status, "event while Account Service down")
    if body.get("status") != "QUEUED":
        fail(f"expected QUEUED status, got {body}")
    ok("response body status QUEUED")

    print("== restart -> replayed ==")
    compose("start")
    for _ in range(30):
        status, body = request("GET", f"/api/v1/events/evt-{RUN_ID}-3")
        if status == 200 and body.get("status") == "APPLIED":
            break
        time.sleep(2)
    else:
        fail(f"queued event never replayed: {body}")
    ok("queued event replayed to APPLIED")

    print("== final balance ==")
    expect_balance(26, "10 + 10 - 4 + 10 after replay")

    print("SMOKE PASS")


if __name__ == "__main__":
    main()
