# DocFlow

DocFlow is a deployable document-analysis workspace backed by an asynchronous Spring Boot API. Its browser dashboard manages projects, submits text documents, shows completed results, and creates project-scoped API keys.

## What it does

- Users register and log in (JWT authentication)
- Users create projects to group their work
- Projects generate scoped API keys for programmatic job access
- Projects submit text-analysis jobs that a background worker processes
- Jobs have controlled status transitions and full lifecycle tracking
- Results include word, character, line, sentence, paragraph, and reading-time metrics

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Build | Maven |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT (jjwt) |
| Migrations | Flyway |
| Containers | Docker Compose |
| Validation | Bean Validation (jakarta) |

## Project Structure

```
src/main/java/com/docflow/
├── DocFlowApplication.java
├── audit/           # BaseEntity with timestamps
├── auth/            # Registration, login, JWT, Spring Security config
├── project/         # Project CRUD
├── apikey/          # API key generation and revocation
├── job/             # Job submission and lifecycle
└── common/          # Global exception handler, shared exceptions
```

## Running locally with Docker Compose

**Prerequisites:** Docker & Docker Compose installed.

```bash
# 1. Clone the repository
git clone https://github.com/enlorik/doc-flow.git
cd doc-flow

# 2. Start all services (PostgreSQL + app) — the image is built automatically
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

## Running without Docker (requires local PostgreSQL)

```bash
# 1. Create the database
createdb docflow

# 2. Configure credentials (or export env vars)
# DOCFLOW_JWT_SECRET is required — generate a random 32+ character string for local use
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/docflow
export SPRING_DATASOURCE_USERNAME=docflow
export SPRING_DATASOURCE_PASSWORD=docflow
export DOCFLOW_JWT_SECRET=your-256-bit-secret-key-here

# 3. Run
mvn spring-boot:run
```

## API Overview

### Auth

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT |

### Projects

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/projects` | Create a project |
| GET | `/api/projects` | List your projects |
| GET | `/api/projects/{id}` | Get project details |

### API Keys

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/projects/{id}/keys` | Generate an API key (raw key shown once) |
| GET | `/api/projects/{id}/keys` | List API keys |
| DELETE | `/api/projects/{id}/keys/{keyId}` | Revoke an API key |

### Jobs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/projects/{id}/jobs` | Submit a job |
| GET | `/api/projects/{id}/jobs` | List jobs |
| GET | `/api/projects/{id}/jobs/{jobId}` | Get job details |
| POST | `/api/projects/{id}/jobs/{jobId}/cancel` | Cancel a queued/running job |

## Example Usage

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password123"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password123"}' | jq -r .token)

# Create a project
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"My Project","description":"Test project"}'

# Submit a job (replace PROJECT_ID)
curl -X POST http://localhost:8080/api/projects/PROJECT_ID/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"type":"TEXT_ANALYZE","inputJson":"{\"text\":\"A real document to analyze.\"}","maxAttempts":3}'
```

An API key can replace the Bearer token for job endpoints in its own project:

```bash
curl http://localhost:8080/api/projects/PROJECT_ID/jobs \
  -H "X-API-Key: df_your_key_here"
```

## Job Statuses

| Status | Description |
|--------|-------------|
| `QUEUED` | Submitted, waiting to be picked up |
| `RUNNING` | Currently being processed |
| `SUCCEEDED` | Completed successfully |
| `FAILED` | Failed, may be retried |
| `RETRY_SCHEDULED` | Scheduled for retry |
| `DEAD_LETTER` | Exhausted max attempts |
| `CANCELLED` | Cancelled by user |

## Configuration

Key environment variables:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/docflow` | DB connection URL |
| `SPRING_DATASOURCE_USERNAME` | `docflow` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `docflow` | DB password |
| `DOCFLOW_JWT_SECRET` | dev default | JWT signing secret (min 256-bit) |
| `DOCFLOW_JWT_EXPIRATION_MS` | `86400000` | JWT expiry (ms), default 24h |

## Running Tests

```bash
mvn test
```

Tests use an H2 in-memory database (Flyway disabled for tests).

## Design notes

**Why stateless credentials instead of sessions** — The browser attaches a Bearer token explicitly, while integrations can send a project-scoped API key in `X-API-Key`. The server validates either credential without a session store or sticky routing. The tradeoff for JWTs is revocability: a stolen token is valid until expiry, so the mitigation is short TTLs. API keys can be revoked immediately from the dashboard.

**Why tenant access requires two checks** — Every operation first calls `loadAndVerifyOwnership` to confirm the caller owns the project in the URL. For jobs and API keys, a second check then confirms the resource actually belongs to that project (e.g. `job.project.id == projectId`). The first check prevents cross-tenant project access; the second prevents IDOR within a tenant's own projects — both are required, and omitting either reopens a vulnerability.

**Why the DB constraint is the real idempotency guarantee** — There's a fast-path check (look up the idempotency key before inserting), but two concurrent requests can both pass that check before either commits. The `UNIQUE(project_id, idempotency_key)` constraint is what actually serialises them at the database level. The intended recovery: one insert wins, the other catches `DataIntegrityViolationException` and reads the winner's row — though the precise boundary depends on flush timing; `save()` rather than `saveAndFlush()` means the exception may surface during commit rather than inside the try/catch, so the read-back path should be verified under the actual transaction configuration.

**Why CSRF is explicitly disabled** — CSRF exploits browsers automatically attaching cookies to cross-origin requests. Bearer tokens require explicit attachment by client code, so the attack surface doesn't exist. Disabling it here is correct; a session-cookie-based app would need it enabled. The reasoning should be explicit, not accidental.

**Why UUID primary keys** — Sequential IDs expose enumeration: a user could walk `project/1`, `project/2`, and so on. UUIDs eliminate that, are safe to expose in URLs, and merge cleanly across environments. The cost is larger indexes and no natural ordering, so `created_at` handles anything that needs to be sorted.
