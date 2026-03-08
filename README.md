# DocFlow

DocFlow is a **Spring Boot backend** for asynchronous document-processing jobs. It provides a production-shaped foundation for managing users, projects, API keys, and job queues.

## What it does

- Users register and log in (JWT authentication)
- Users create projects to group their work
- Projects generate API keys for programmatic access
- Projects submit jobs that are queued for later processing
- Jobs have controlled status transitions and full lifecycle tracking

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

# 2. Build the application JAR
./mvnw clean package -DskipTests

# 3. Start all services (PostgreSQL + app)
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

## Running without Docker (requires local PostgreSQL)

```bash
# 1. Create the database
createdb docflow

# 2. Configure credentials (or export env vars)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/docflow
export SPRING_DATASOURCE_USERNAME=docflow
export SPRING_DATASOURCE_PASSWORD=docflow
export DOCFLOW_JWT_SECRET=your-256-bit-secret-key-here

# 3. Run
./mvnw spring-boot:run
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
  -d '{"type":"PDF_CONVERT","inputJson":"{\"url\":\"https://example.com\"}","maxAttempts":3}'
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
./mvnw test
```

Tests use an H2 in-memory database (Flyway disabled for tests).