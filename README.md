# Event Ledger

Two independently runnable Spring Boot microservices implementing an immutable
transaction-event ledger with derived account balances.

## Architecture

```
Client ──→ Event Gateway (8080, H2-A) ──REST──→ Account Service (8081, H2-B)
```

- **Event Gateway** (public, port 8080) — owns the immutable event ledger:
  receives transaction events, validates, enforces idempotency, stores event
  records, orchestrates the call to the Account Service, and queues events for
  replay when the Account Service is unavailable.
- **Account Service** (internal, port 8081) — owns derived account state:
  applies transactions, maintains balances, serves balance and account-detail
  queries. Called only by the Gateway.

Neither service reads the other's database; the only contract between them is
the Account Service REST API. The repo holds two fully independent Maven
projects — no parent pom, no shared module. The two small DTOs describing the
inter-service contract are intentionally duplicated per service to preserve
genuine build-time independence.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for the compose stack and smoke test)
- Python 3.8+ (for `smoke_test.py`, stdlib only — no pip install)

## Run

Compose (builds both images):

```bash
docker compose up --build
```

In compose, both services use file-based H2 inside their containers
(`SPRING_DATASOURCE_URL` override), so data survives `docker compose
stop`/`start` — which the smoke test relies on — but not `docker compose
down`, which recreates the containers. Local `mvn spring-boot:run` and tests
use in-memory H2.

Manual (two terminals, Account Service first):

```bash
cd account-service && mvn spring-boot:run
cd event-gateway   && mvn spring-boot:run
```

Gateway: http://localhost:8080 · Account Service: http://localhost:8081 ·
Health: `GET /health` on both.

## Tests

```bash
cd event-gateway   && mvn test
cd account-service && mvn test
```

End-to-end against the compose stack (submit → duplicate returns original →
out-of-order listing → CREDIT−DEBIT balance → validation 400s → kill account
container → queued → restart → replayed → final balance). Uses a unique
account per run, so it can be re-run against a live stack:

```bash
docker compose up -d --build
./smoke_test.py
docker compose down
```

The `-d` flag runs the stack **detached** (in the background), so the same
terminal is free to run the script. If you started the stack without `-d`
(`docker compose up --build` keeps streaming logs in the foreground), leave
it running and run `./smoke_test.py` from a **second terminal** — do not
Ctrl-C the compose terminal, that stops the services. The script drives
`docker compose stop/start account-service` itself to demo the outage path.

## Resiliency

Stack on every Gateway→Account call (outermost → innermost):
`@Retryable` (Spring Framework native) → Resilience4j circuit breaker →
timeout → HTTP call. All values live in `application.yml`, not code:

| Layer | Configuration |
|---|---|
| Timeout | connect 2s, read 3s |
| Circuit breaker | count-based window 10, opens at 50% failure, open 10s, 3 half-open probes |
| Retry | 3 total attempts, exponential backoff 200ms base ×2, jitter; retries timeouts/5xx only — never 4xx; an open circuit (`CallNotPermittedException`) aborts remaining retries |

Rationale: the timeout turns a slow dependency into a fast failure; the
breaker protects the Account Service from retry storms and gives clients
instant errors while open; retry absorbs transient blips; jitter prevents
synchronized retry herds. The retry burst (3 attempts, ~1.4s) is deliberately
small relative to the breaker window (10 calls) so a single request cannot
flip the circuit.

## Architecture decisions

The big "why this, not that" calls. Implementation-level mechanics behind them
are in **Design decisions** below.

| Decision | Chosen | Considered | Why this |
|---|---|---|---|
| Inter-service comms | Synchronous REST + durable DB replay queue | Kafka / RabbitMQ event bus | No broker to run: the `QUEUED`-events table plus the replay scheduler already deliver at-least-once + eventual consistency. A broker earns its operational weight only past the single-instance scale this targets. |
| Contract sharing | DTO duplicated per service, no parent pom / shared module | Shared `-contract` Maven module | Keeps builds and releases genuinely independent; the two DTOs are tiny, and WireMock stubs + `smoke_test.py` catch drift. Pact would add compile-time enforcement if this grew. |
| State model | Immutable ledger (Gateway) + derived balance (Account) | Single service storing a mutable balance | The event log is the source of truth; the balance is recomputable and replay-safe because SUM(credits)−SUM(debits) is order-independent. Cost: cross-service consistency is eventual, which is what the replay queue pays for. |
| Failure handling | `202`/`QUEUED` persist-then-replay | `503` fail-fast | The event is already durable on the Gateway; `503` would only invite a client retry into the idempotency path. See Design decisions for the full contract. |
| Idempotency | Insert-first, map PK collision to `200` | Check-then-insert | Check-then-insert races under concurrent duplicate delivery; insert-first is atomic at the DB. |
| Persistence | Embedded H2 | Postgres + Testcontainers | Zero-infrastructure to run and demo; idempotency and balances lean on standard unique constraints + ACID updates behind Spring Data JPA, so Postgres is a dependency + config swap. |
| Package structure | Layered | Hexagonal ports & adapters | JPA entities double as the domain model at this scale; the one volatile seam (`AccountServiceClient`) is already isolated behind a single class, so ports/adapters would add split with no payoff. |

## Design decisions

- **202, not 503, when the Account Service is down.** On resilience
  exhaustion the event is already durably persisted on the Gateway and marked
  `QUEUED`; the client gets `202 Accepted` with `"status": "QUEUED"`. A 503
  would invite a client retry that could only hit the idempotency path — 202
  states the actual contract: accepted, will be applied. A background
  scheduler replays `QUEUED` events through the *same* resilient client (no
  second code path); success flips them to `APPLIED`. Single-instance
  assumption: horizontal scaling would need `SELECT … FOR UPDATE SKIP LOCKED`
  or equivalent.
- **Insert-first idempotency.** `event_id` is the primary key; submission
  inserts first and maps the PK collision
  (`DataIntegrityViolationException`) to `200 OK` + the original event. No
  check-then-insert — that races under concurrent duplicate delivery. The
  entities implement `Persistable` so assigned IDs `persist()` instead of
  `merge()` (a merge would silently overwrite the duplicate instead of
  throwing). Duplicates are `200`, not `409`: at-least-once delivery makes
  them expected behavior.
- **Dual-side dedupe.** The apply request carries `eventId` and
  `TRANSACTION_RECORD.event_id` is UNIQUE, so a Gateway
  timeout-after-commit retry or replay lands as an idempotent no-op — a
  balance change can never double-apply.
- **Out-of-order tolerance costs nothing.** Balance is
  SUM(credits) − SUM(debits) — commutative, arrival-order independent.
  Chronological listing is `ORDER BY event_timestamp, event_id` at query time
  (the `event_id` tie-breaker keeps client-supplied timestamp collisions
  deterministic). This is also what makes naive per-event replay safe: replay
  order does not affect correctness.
- **Atomic balance updates.** The balance write is a single
  `UPDATE account SET balance = balance + :delta` — atomic at the DB, in one
  ACID transaction with the transaction-record insert. Load-modify-save would
  lose concurrent updates to the same account; a test drives 10 concurrent
  credits and asserts the exact sum.
- **H2 is not the production story.** Embedded H2 keeps the system
  zero-infrastructure; idempotency and balances rely on standard unique
  constraints and ACID updates, and the repository layer sits behind Spring
  Data JPA, so Postgres is a dependency + config swap.
- **Integration-test seam.** Gateway tests run against WireMock stubs
  mirroring the documented Account Service contract; `smoke_test.py`
  exercises the real end-to-end path. Trade-off of true project independence;
  Pact contract tests are the production-grade seam.
- **Layered, deliberately not hexagonal.** JPA entities double as the domain
  model; ports-and-adapters would add a per-table domain/entity/mapper split
  with no payoff at this scale. The seams hexagonal protects are covered
  cheaply: `AccountServiceClient` isolates all HTTP/resilience detail behind
  one class (reused verbatim by replay), and Spring Data JPA hides the store.

## Observability

- **Tracing:** Micrometer Tracing (Brave bridge), W3C `traceparent`
  propagation, sampling 1.0. No exporter configured — Jaeger/OTLP is the
  documented extension point.
- **Logging:** Boot native structured logging (ECS JSON) with
  `trace.id`/`span.id` injected automatically.
- **Metrics:** `/actuator/prometheus` — custom
  `gateway_events_received_total{type,outcome}` (outcomes: applied / queued /
  duplicate / rejected; the applied/queued increments are bound to the status
  transition's commit so metric and ledger cannot drift) and
  `gateway_replay_queue_depth` gauge, plus Resilience4j breaker-state and
  `http_server_requests` histograms for free.

### Trace demo

One trace across both services. Log lines carry flat `"traceId"`/`"spanId"`
fields; submit an event, take the `traceId` from any Gateway log line for
it, and grep — the Account Service's apply shows the same ID:

```bash
docker compose logs | grep <trace_id>
```

## Curl walkthrough

```bash
# 1. Submit → 201 Created, status APPLIED
curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-1","accountId":"acc-1","type":"CREDIT","amount":10,
  "currency":"EUR","eventTimestamp":"2026-07-02T10:00:00Z"}'

# 2. Same event again → 200 OK, original returned
curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-1","accountId":"acc-1","type":"CREDIT","amount":10,
  "currency":"EUR","eventTimestamp":"2026-07-02T10:00:00Z"}'

# 3. Earlier event arriving later → 201; listing stays chronological
curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-0","accountId":"acc-1","type":"CREDIT","amount":5,
  "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}'
curl -s 'localhost:8080/api/v1/events?account=acc-1'   # evt-0 listed first

# 4. Validation failure → 400 Problem Details, field messages aggregated
curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-bad"}'

# 5. Balance (proxied to Account Service) → 15
curl -s localhost:8080/api/v1/accounts/acc-1/balance

# 6. Kill the Account Service, submit → 202 Accepted, status QUEUED
docker compose stop account-service
curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-2","accountId":"acc-1","type":"CREDIT","amount":10,
  "currency":"EUR","eventTimestamp":"2026-07-03T10:00:00Z"}'

# 7. Balance while down → 503 Problem Details with traceId
curl -i localhost:8080/api/v1/accounts/acc-1/balance

# 8. Restart → replay flips evt-2 to APPLIED within ~5s
docker compose start account-service
sleep 10 && curl -s localhost:8080/api/v1/events/evt-2   # "status":"APPLIED"
curl -s localhost:8080/api/v1/accounts/acc-1/balance     # 25
```
