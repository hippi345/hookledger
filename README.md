# hookledger

Personal portfolio project: a small **Java 21 / Spring Boot 3** service that ingests signed finance webhooks, stores each money event once in **PostgreSQL**, and exposes a minimal ledger API plus a simple HTML list of stored events.

## What it does

- Verifies an **HMAC-SHA256** signature over the **raw** webhook body before JSON is parsed.
- Accepts money events of type `charge`, `refund`, or `payout` with `amount` in **minor units** and a 3-letter **ISO currency** code.
- Refunds reference a charge (`chargeId`); payouts reference the charges they settle (`chargeIds`).
- Persists events by id (duplicate ids do not create a second row).
- REST: ingest webhook, list events, get one event by id, per-currency balance, replay an event by id, settlement graph walk, and ledger utilities below.
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) health at `/actuator/health`.
- OpenAPI 3 description at `/openapi/v3/api-docs` (Swagger UI at `/openapi/swagger-ui.html`).

## Run locally

Requirements: Java 21, Maven, PostgreSQL.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/hookledger
export SPRING_DATASOURCE_USERNAME=hookledger
export SPRING_DATASOURCE_PASSWORD=hookledger
export HOOKLEDGER_WEBHOOK_SECRET='<your signing secret>'
mvn spring-boot:run
```

Create the database and role in Postgres before starting. Set `HOOKLEDGER_WEBHOOK_SECRET` to a value you generate; the application reads the signing secret **only** from this environment variable (not from config files or the repository).

Each webhook event `id` is stored at most once: resubmitting the same id returns the existing row and does not insert a duplicate.

Browse stored events at `http://localhost:8080/`.

## Webhook signature

| Item | Value |
|------|--------|
| Header | `X-Hookledger-Signature` |
| Algorithm | HMAC-SHA256 |
| Key | UTF-8 bytes of `HOOKLEDGER_WEBHOOK_SECRET` |
| Message | Raw HTTP request body bytes (exactly as received) |
| Header value | Lowercase **hex** encoding of the 32-byte digest (no `sha256=` prefix) |

Example (replace the secret with your own value from the environment):

```bash
BODY='{"id":"evt_001","type":"charge","amount":1500,"currency":"usd"}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$HOOKLEDGER_WEBHOOK_SECRET" | awk '{print $2}')
curl -sS -X POST http://localhost:8080/api/webhooks \
  -H 'Content-Type: application/json' \
  -H "X-Hookledger-Signature: $SIG" \
  -d "$BODY"
```

### API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/webhooks` | Ingest signed webhook (201 created, 200 if id already exists) |
| `GET` | `/api/events` | List events (JSON) |
| `GET` | `/api/events/{id}` | Get one stored event by id (404 if unknown) |
| `GET` | `/api/events/by-timestamp?at=` | Get one event by exact `receivedAt` (ISO-8601) |
| `GET` | `/api/balance/{currency}` | Balance in minor units for a 3-letter ISO currency |
| `GET` | `/api/charges/top?limit=` | Largest charges by amount |
| `GET` | `/api/payouts/{id}/charges` | Source charges for a payout (settlement graph walk) |
| `POST` | `/api/events/{id}/replay` | Return event and increment replay count (404 if unknown) |
| `POST` | `/api/events/replay/undo` | Undo the most recent replay (404 if stack empty) |

### Balance

Balance for a currency is **charges minus refunds** (amounts in minor units). **Payout events are recorded but do not change the balance** — only `charge` and `refund` affect the total.

### Algorithms in the ledger

| Idea | Where it is used |
|------|------------------|
| **Binary search** | `GET /api/events/by-timestamp` locates one row by exact `receivedAt` in the time-ordered event list. |
| **Priority queue** | `GET /api/charges/top` returns the highest charge amounts up to your `limit`. |
| **Regex** | Webhook ingest rejects event ids and currency codes that do not match the allowed patterns before save. |
| **Stack** | `POST /api/events/replay/undo` pops the last replay and restores the prior replay count. |
| **Bit flags** | Each stored event packs **signed**, **replayed**, and **duplicate** state; API responses expose `stateFlags` and booleans. |
| **Settlement graph** | Refunds point at a charge; payouts list settled charges. Ingest rejects cycles (including a refund referencing itself). `GET /api/payouts/{id}/charges` walks from the payout to those charges. |

## Tests

Integration tests use **Testcontainers** with a real PostgreSQL instance:

```bash
mvn test
```

Docker must be available to the test process.

## License

MIT — see [LICENSE](LICENSE).
