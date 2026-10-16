# Edvexa Distributed API Gateway and Resilience Platform

A high-performance, fault-tolerant distributed API Gateway and administrative resilience monitoring dashboard engineered with Spring Boot 3, Java 21, PostgreSQL, Redis, Resilience4j, React 18, and TypeScript.

---

## 1. Project Overview

The Edvexa API Gateway serves as a unified entry point for microservices, providing enterprise-grade reverse proxy routing, multi-tier authentication, distributed rate limiting, fault tolerance, write idempotency, and deep observability. It is accompanied by a modern React administrative dashboard for real-time traffic monitoring, service lifecycle control, API key provisioning, and circuit breaker inspection.

---

## 2. Main Features

- **Dynamic Reverse Proxy Routing:** Prefix-based service registration, longest-prefix matching, segment boundary validation, and dynamic target base URL URI reconstruction.
- **Multi-Tier Authentication & RBAC:**
  - Administrative JWT authentication with `ROLE_ADMIN` and `ROLE_USER` scopes.
  - Client API key authentication via `X-API-Key` headers using SHA-256 cryptographic hashes.
  - Secure first-admin bootstrap (`POST /api/v1/auth/bootstrap`) and controlled password recovery.
- **Distributed Sliding-Window Rate Limiting:** High-throughput rate enforcement backed by Redis and atomic Lua scripts, emitting standard `X-RateLimit-*` and `Retry-After` headers.
- **Resilience4j Fault Tolerance:**
  - Configurable per-service connection and response timeouts.
  - Bounded exponential backoff retries strictly for transient failures (`HTTP 502/503/504`, `IOException`).
  - Circuit Breakers (`CLOSED`, `OPEN`, `HALF_OPEN`) preventing cascading microservice failures.
- **Write-Operation Idempotency:** Client-supplied `Idempotency-Key` headers with request payload hashing, database locks, automatic response caching, and concurrent 409 Conflict detection.
- **Observability & Request Audit:**
  - End-to-end `X-Correlation-ID` header generation and propagation.
  - Paginated audit logging recording latency, outcome categories, circuit breaker states, and error diagnostics.
  - Actuator metrics with latency percentiles (p50, p90, p95, p99) and health checks.
- **Administrative Monitoring Dashboard:** Interactive React/TypeScript dashboard providing live metrics visualization, service CRUD operations, API key generation with one-time reveal, and log inspection.
- **Interactive API Playground:** Embedded developer console for testing gateway routes directly from the browser without terminal tools. Features dynamic service discovery, live URL preview, custom headers, query parameters, JSON syntax validation, secure client API key injection, real-time response analysis, cURL export, local history restoration, and direct correlation ID log tracing.

---

## 3. Architecture and Request Flow

```
[ Client / Browser ]
        │
        │ HTTP Requests (with X-API-Key or Bearer JWT)
        ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot API Gateway                         │
│                                                                        │
│  1. CorrelationIdFilter ─────────► Generates / Propagates Correlation  │
│  2. Security Filter Chain                                              │
│     ├── JwtAuthenticationFilter ──► Validates Admin JWT (HMAC-SHA256) │
│     └── ApiKeyAuthenticationFilter► Validates Client API Key (SHA-256) │
│  3. RateLimitingFilter ──────────► Redis Lua Atomic Sliding Window     │
│  4. GatewayRoutingService ───────► Service Resolution (Longest Prefix) │
│     ├── Idempotency Check ───────► Checks Database Cache / Locks       │
│     ├── CircuitBreakerService ───► Resilience4j State Machine          │
│     └── RetryPolicyService ──────► Bounded Exponential Retries         │
│  5. GatewayHttpClient ───────────► Dispatches Downstream HTTP Call     │
│  6. RequestAuditService ─────────► Asynchronously Persists Audit Log   │
└────────────────────────────────────────────────────────────────────────┘
        │                                        │
        ▼ Forwarded Request                      ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│  Downstream Test Service     │       │   PostgreSQL & Redis Cache   │
│  (Port 8081 / Simulated API) │       │   (Ports 5432 & 6379)        │
└──────────────────────────────┘       └──────────────────────────────┘
```

---

## 4. Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Flyway Migration, JJWT 0.12.6, Resilience4j 2.2.0, SpringDoc OpenAPI 2.6.0.
- **Persistence & Caching:** PostgreSQL 16 (Relational data, services, audit logs, API keys, idempotency records), Redis 7 (Distributed rate limiter sliding-window sets).
- **Frontend:** React 18, TypeScript 5.5, Vite 6, React Router 6, Lucide Icons, Vitest, React Testing Library.
- **Infrastructure & Testing:** Docker Compose, Maven 3.9+, JUnit 5, Mockito, AssertJ, H2 (In-memory test isolation).

---

## 5. Project Structure

```
api-gateway/
├── docker-compose.yml              # Local container infrastructure (Postgres & Redis)
├── .env.example                    # Safe environment template with placeholders
├── pom.xml                         # Maven dependencies & build lifecycle
├── docs/
│   ├── codezero.md                 # CodeZero evaluation and architecture findings
│   └── demo.md                     # Step-by-step demonstration walkthrough & script
├── frontend/                       # React / TypeScript Administrative Dashboard
│   ├── src/
│   │   ├── components/             # Layout, Header, Sidebar, MetricCard, Modal, StatusBadge
│   │   ├── context/                # AuthContext (JWT session state)
│   │   ├── pages/                  # Overview, Services, ApiKeys, RequestLogs, Resilience, Playground, Login
│   │   ├── services/               # API clients, auth, services, and playgroundService
│   │   ├── types/                  # TypeScript domain models and playground interfaces
│   │   └── test/                   # Vitest unit and integration test suites (including Playground)
│   ├── package.json
│   └── vite.config.ts              # Vite server & proxy configuration (Port 3000)
└── src/
    ├── main/
    │   ├── java/com/edvexa/gateway/
    │   │   ├── config/             # Redis, OpenAPI, and HTTP client configurations
    │   │   ├── idempotency/        # IdempotencyFilter, service, entity, repository
    │   │   ├── observability/      # CorrelationIdFilter, RequestAuditService, Metrics
    │   │   ├── ratelimit/          # RateLimitingFilter, RedisRateLimiterService
    │   │   ├── registry/           # ServiceRegistryController, ServiceEntity, repository
    │   │   ├── resilience/         # CircuitBreakerService, RetryPolicyService
    │   │   ├── routing/            # GatewayRoutingController, GatewayRoutingService
    │   │   ├── security/           # AuthController, AuthService, AdminBootstrapService, JWT, API Keys
    │   │   └── simulator/          # Downstream test simulator controllers & state
    │   └── resources/
    │       ├── db/migration/       # Flyway SQL migrations (V1 initial schema)
    │       └── scripts/            # Atomic Redis Lua sliding-window script
    └── test/                       # 141 automated unit & integration test suites
```

---

## 6. Prerequisites

Ensure the following tools are installed:
- **Java Development Kit (JDK):** Version 21 or higher (`java -version`)
- **Apache Maven:** Version 3.9+ (`mvn -v`)
- **Node.js & npm:** Node 18+ and npm 9+ (`node -v`, `npm -v`)
- **Docker & Docker Compose:** Docker 24+ and Compose v2+ (`docker compose version`)

---

## 7. Environment-Variable Configuration

Create a local `.env` file using the provided safe template:
```bash
cp .env.example .env
```

Key environment variables:
| Variable | Description | Safe Example / Default |
| :--- | :--- | :--- |
| `DB_HOST` | PostgreSQL hostname | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database name | `edvexa_gateway` |
| `DB_USER` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | Set securely in local `.env` |
| `REDIS_HOST` | Redis server hostname | `localhost` |
| `REDIS_PORT` | Redis server port | `6379` |
| `PORT` | Gateway server port | `8080` |
| `JWT_SECRET` | 256-bit secret key for signing admin JWTs | Required (32+ chars) |
| `GATEWAY_ADMIN_INITIAL_USERNAME` | Initial administrator username | `admin` |
| `GATEWAY_ADMIN_INITIAL_PASSWORD` | Initial administrator password on first boot | Set securely in local `.env` |
| `GATEWAY_BOOTSTRAP_SECRET` | Secret token to authorize first administrator creation | Optional / First-time setup |
| `GATEWAY_ADMIN_RESET_PASSWORD` | Emergency password reset value | Leave empty during normal operation |

---

## 8. Local Setup Instructions

### Step 1: Start Infrastructure (PostgreSQL & Redis)
```bash
docker compose up -d
```
Verify containers are healthy:
```bash
docker compose ps
```

### Step 2: Build & Start Gateway Backend
```bash
mvn clean compile
mvn spring-boot:run
```
The gateway will start on `http://localhost:8080`.

### Step 3: Start Frontend Dashboard
In a separate terminal:
```bash
cd frontend
npm install
npm run dev
```
The dashboard will open on `http://localhost:3000`.

---

## 9. Docker Compose Instructions

The included `docker-compose.yml` configures production-aligned PostgreSQL 16 and Redis 7 containers:
```bash
# Start background services
docker compose up -d

# Check service logs
docker compose logs -f

# Stop services without losing data
docker compose stop

# Destroy containers and preserve volumes
docker compose down

# Wipe persistent database and cache data (full reset)
docker compose down -v
```

---

## 10. Database and Redis Setup

- **Flyway Migrations:** Applied automatically on backend startup from `src/main/resources/db/migration/V1__initial_schema.sql`.
- **Schema Contents:** `services`, `api_keys`, `admin_users`, `gateway_request_logs`, and `idempotency_records`.
- **Redis Health:** Monitored via Spring Boot Actuator at `GET /actuator/health`.

---

## 11. Simulated Downstream Service Setup

The platform includes two downstream testing options:
1. **Built-in Simulators:** Accessible directly via the gateway:
   - Orders: `/api/v1/simulated/orders`
   - Payments: `/api/v1/simulated/payments`
   - Configure failure injection: `POST /api/v1/simulated/orders/config`
2. **Standalone Test Service (Port 8081):**
   Can be launched to verify out-of-process reverse proxy routing:
   ```bash
   GET http://localhost:8081/test
   ```

---

## 12. Frontend and Backend Startup Commands

| Component | Directory | Command | URL |
| :--- | :--- | :--- | :--- |
| **Backend API Gateway** | Root directory | `mvn spring-boot:run` | `http://localhost:8080` |
| **Frontend Dashboard** | `frontend/` | `npm run dev` | `http://localhost:3000` |
| **Actuator Health** | Root directory | `curl http://localhost:8080/actuator/health` | `http://localhost:8080/actuator/health` |
| **Swagger UI** | Browser | Navigate to Swagger URL | `http://localhost:8080/swagger-ui/index.html` |

---

## 13. API Endpoints and Swagger UI

Interactive OpenAPI 3.0 documentation is enabled:
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON Spec:** `http://localhost:8080/v3/api-docs`

### Key Endpoint Groups:
- **Authentication:** `POST /api/v1/auth/login`, `POST /api/v1/auth/bootstrap`, `GET /api/v1/auth/status`, `GET /api/v1/auth/me`
- **Service Registry:** `GET /api/v1/services`, `POST /api/v1/services`, `PUT /api/v1/services/{id}`, `DELETE /api/v1/services/{id}`
- **API Keys:** `GET /api/v1/api-keys`, `POST /api/v1/api-keys`, `DELETE /api/v1/api-keys/{id}`
- **Observability:** `GET /api/v1/admin/metrics`, `GET /api/v1/admin/health`, `GET /api/v1/admin/logs`, `GET /api/v1/admin/errors/recent`
- **Reverse Proxy Gateway:** `ANY /gateway/**` (Requires `X-API-Key`)

---

## 14. Authentication & Authorization Details

### Administrator Account Provisioning and Setup

To prevent hardcoded credentials from being committed to source control while ensuring project evaluators and new team members can seamlessly set up and access the platform, the gateway supports three configurable provisioning methods:

#### Method 1: Interactive First-Time Setup Wizard (Web UI)
1. Start the gateway backend and open the web dashboard at `http://localhost:3000`.
2. If no administrator account exists in the database, the login screen automatically detects this and transitions into the **First-Time Administrator Setup** wizard.
3. If `GATEWAY_BOOTSTRAP_SECRET` is defined in `.env`, enter the secret token.
4. Input your desired administrator username and a password (minimum 8 characters).
5. Click **Initialize & Sign In**. The backend securely hashes the password using BCrypt, persists the admin entity, returns a signed JWT token, and logs into the dashboard immediately.

#### Method 2: Automatic Startup Provisioning (Docker / Headless Environments)
Configure the initial administrator credentials in your local `.env` file before booting the service:
```bash
GATEWAY_ADMIN_INITIAL_USERNAME=admin
GATEWAY_ADMIN_INITIAL_PASSWORD=YourSecurePassword123!
```
On initial startup, if the `admin_users` table is empty, `AdminBootstrapService` will automatically provision this account.

#### Method 3: REST API Bootstrap
Automated scripts or CI pipelines can provision the initial administrator via an authenticated HTTP request:
```bash
curl -X POST http://localhost:8080/api/v1/auth/bootstrap \
  -H "Content-Type: application/json" \
  -d '{
    "bootstrapSecret": "your_bootstrap_secret_if_configured",
    "username": "admin",
    "password": "YourSecurePassword123!"
  }'
```

#### Administrator Password Recovery / Reset
If an administrator password needs to be changed or reset:
1. Set `GATEWAY_ADMIN_RESET_PASSWORD=YourNewSecurePassword123!` in `.env`.
2. Restart the gateway backend once.
3. The password for `GATEWAY_ADMIN_INITIAL_USERNAME` is updated using BCrypt.
4. Clear or comment out `GATEWAY_ADMIN_RESET_PASSWORD` after restart.

### Administrator JWT RBAC
1. Obtain token via `POST /api/v1/auth/login` with administrative credentials:
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "YourSecurePassword123!"}'
   ```
2. Provide token in subsequent calls: `Authorization: Bearer <token>`.
3. Validated on every administrative route (`/api/v1/services/**`, `/api/v1/api-keys/**`, `/api/v1/admin/**`).

### Client API Keys
1. Create key via Dashboard or `POST /api/v1/api-keys` (returns raw key once: `edx_<prefix>_<secret>`).
2. Pass raw key in header: `X-API-Key: edx_...`.
3. Keys are stored only as SHA-256 hashes in PostgreSQL; revoked keys return HTTP 401.

---

## 15. Rate Limiting

- **Algorithm:** Atomic sliding-window log using Redis sorted sets.
- **Headers Returned:**
  - `X-RateLimit-Limit`: Maximum requests permitted per minute.
  - `X-RateLimit-Remaining`: Remaining request quota.
  - `X-RateLimit-Reset`: Unix epoch timestamp when quota refreshes.
- **Exceeded Limit:** Returns `HTTP 429 Too Many Requests` with `Retry-After: <seconds>` header.

---

## 16. Retries, Timeouts, and Circuit Breakers

- **Connection & Response Timeouts:** Configured individually per registered service (defaults: 2000ms connection, 5000ms response).
- **Bounded Exponential Retries:** Retries up to 3 times on transient network failures and 5xx downstream errors (`initialInterval: 100ms`, `multiplier: 2.0`).
- **Resilience4j Circuit Breaker:**
  - `CLOSED`: Normal traffic.
  - `OPEN`: Failures exceed 50% across the sliding buffer (immediate 503 fallback).
  - `HALF_OPEN`: Trial calls permitted after wait duration to test downstream recovery.

---

## 17. Write-Operation Idempotency

- Supported on `POST`, `PUT`, `PATCH` operations via header: `Idempotency-Key: <unique-token>`.
- Generates a SHA-256 fingerprint of the request path, method, and body.
- If in-flight: Acquires a database lock; concurrent duplicate requests receive `409 Conflict`.
- Once completed: Replays cached status code, headers, and body with `X-Cache: HIT`.

---

## 18. Correlation IDs, Logging, Metrics & Health

- Every request is tagged with an `X-Correlation-ID` (validated UUID or generated).
- Audit logs record: `correlation_id`, `client_id`, `service_id`, `http_method`, `request_path`, `status_code`, `latency_ms`, `retry_count`, `circuit_breaker_state`, `outcome_category`.
- Actuator endpoint `GET /api/v1/admin/metrics` computes live throughput, failure counts, and latency percentiles (p50, p90, p95, p99).

---

## 19. Interactive API Playground

The integrated API Playground (`/playground`) empowers engineers and operators to test and validate gateway routes interactively:
- **True Gateway Traversal:** Requests are dispatched via the API Gateway reverse proxy (`/gateway/{prefix}/{subpath}`), exercising the full pipeline: correlation tracking, API key authentication, distributed rate limiting, circuit breaker state machine, downstream routing, and audit logging.
- **Request Builder:**
  - Method selector: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`.
  - Dynamic service picker: Populated from active registered services with real-time target URL preview.
  - Query parameter and custom header editors with toggle, add, and remove controls.
  - JSON body editor with real-time syntax error validation and indentation formatter.
- **Client Authentication:**
  - Choice of selecting an active provisioned key from inventory or entering a manual API key.
  - Key secrets are stored strictly in memory for the active browser session; keys are never stored in localStorage or history.
- **Real-time Response Inspector:**
  - Status code badge with standard HTTP categorization (2xx green, 3xx blue, 4xx yellow, 5xx red).
  - Round-trip latency measurement in milliseconds and response payload byte size.
  - Formatted JSON response viewer with one-click copy.
  - Headers inspector highlighting `X-Correlation-ID`, `X-RateLimit-*`, `X-Gateway-Service-ID`, and `X-Cache`.
  - One-click **[Trace in Logs]** button linking directly to the audit log filtered by that exact correlation ID.
- **Developer Utilities:**
  - **Copy as cURL:** Generates an equivalent terminal command with a safe `${GATEWAY_API_KEY}` placeholder.
  - **Recent History:** Persists non-sensitive request configurations in browser storage (method, route, params, headers, status) with one-click restoration.

---

## 20. Backend and Frontend Testing Commands

### Backend Test Suite (141 tests, 32 classes)
```bash
# Run all unit and integration tests
mvn test

# Run focused routing and resilience tests
mvn test "-Dtest=GatewayRoutingServiceTest,GatewayRoutingIntegrationTest,ResilienceIntegrationTest"

# Run security test suite
mvn test "-Dtest=JwtTokenProviderTest,AdminSecurityIntegrationTest,AdminBootstrapIntegrationTest,AdminPasswordResetTest,ApiKeyAuthenticationIntegrationTest"
```

### Frontend Test Suite (23 tests, 7 test files)
```bash
cd frontend
npm test -- --run
```

---

## 21. Demonstration Walkthrough

Refer to [docs/demo.md](file:///c:/Users/dell/Downloads/api-gateway/docs/demo.md) for detailed terminal commands and verification steps:
- **Scenario A:** Normal request routing with `X-API-Key` and `X-Correlation-ID`.
- **Scenario B:** Distributed rate limit triggering `HTTP 429` and `Retry-After`.
- **Scenario C:** Downstream fault injection, circuit breaker opening, and automatic recovery.
- **Recording Script:** Timed demonstration guide for video recording.

---

## 22. CodeZero Usage and Findings

An evaluation of CodeZero (Code0 by Frigga Cloud Labs: `https://frigga.cloud/codezero`) was performed in accordance with assignment requirements.
- Full details, architecture review, and access evaluation are documented in [docs/codezero.md](file:///c:/Users/dell/Downloads/api-gateway/docs/codezero.md).
- Because CodeZero requires an interactive VS Code GUI session and Frigga Cloud browser authentication, all gateway features and test suites were built and verified directly using local Java and Node.js toolchains.

---

## 23. Known Limitations and Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| **Backend fails on startup with `IllegalStateException: JWT signing secret is required`** | `JWT_SECRET` is not set | Configure `JWT_SECRET` in `.env` with at least 32 characters (256 bits). |
| **`HTTP 401 Unauthorized` on `/gateway/**`** | Missing or invalid API key | Pass a valid client API key in the `X-API-Key` header. |
| **`HTTP 409 Conflict` on `/api/v1/auth/bootstrap`** | An admin account already exists | Bootstrap is only permitted for the first administrator. Use `GATEWAY_ADMIN_RESET_PASSWORD` for recovery. |
| **Redis connection refused** | Redis container not running | Run `docker compose up -d redis`. |
| **Database migration error** | Outdated PostgreSQL schema | Run `docker compose down -v` and restart to allow Flyway to re-apply migrations cleanly. |
| **PowerShell `Invoke-WebRequest` hanging** | Legacy IE DOM parser in Windows PowerShell | Use `Invoke-WebRequest -UseBasicParsing` or native `curl.exe`. |
