# Edvexa Distributed API Gateway and Resilience Management Platform

A high-performance, fault-tolerant distributed API gateway and resilience management platform engineered with Spring Boot 3, Java 21, Redis, PostgreSQL, Resilience4j, and a modern React/TypeScript monitoring dashboard.

---

## Architectural Highlights

### Core Gateway Capabilities (Milestones 1 to 8)
1. Dynamic Service Registry: Dynamic downstream route registration with prefix-matching, target base URL routing, and health verification.
2. Authentication & Authorization:
   - Administrative JWT RBAC (ROLE_ADMIN, ROLE_USER) with token expiration and signature validation.
   - Client API Key authentication via `X-API-Key` headers, storing SHA-256 hashes with key prefix identification.
3. Distributed Sliding-Window Rate Limiting: Concurrency-safe per-client request throttling powered by Redis Lua scripts, returning `X-RateLimit-*` headers and HTTP 429 Retry-After responses.
4. Resilience & Fault Tolerance:
   - Configurable per-service connection and response timeouts.
   - Bounded exponential backoff retries exclusively for transient errors (HTTP 502, 503, 504, IOException).
   - Resilience4j Circuit Breakers with state transitions (`CLOSED`, `OPEN`, `HALF_OPEN`) preventing cascading system failure.
5. Write-Operation Idempotency: Client-scoped idempotency keys (`Idempotency-Key` header) with hash fingerprints, database unique constraints, atomic locking, response replay, and 409 Conflict detection.
6. Distributed Observability & Audit Logs: Correlation ID generation and header propagation (`X-Correlation-ID`), comprehensive request tracking, Actuator metric counters, latency percentile calculations (p50, p90, p95, p99), and paginated audit logs.

### Administrative Monitoring Dashboard (Milestone 9)
A responsive, accessible single-page administrative dashboard built in `frontend/` using React 18, Vite 6, TypeScript, and Vitest.
- Overview: Live KPIs (total requests, success rate, failures, average latency), SVG latency distribution charts, outcome breakdown, circuit breaker summary, and recent diagnostic failure logs.
- Service Registry: Full CRUD lifecycle management for downstream microservices, path prefix configuration, timeout overrides, and instant enable/disable toggling.
- API Key Management: Client credential creation, prefix-based inventory, revocations, and a security-critical one-time raw secret reveal modal.
- Request Logs Explorer: Paginated audit logs with search by exact Correlation ID, filtering by service/client/outcome, full trace modal with diagnostic details, and complete secret redaction.
- Resilience & Circuit Breakers: Real-time visual status cards of Resilience4j state machines, call buffer ratios, failure rates, rejected call metrics, and timeout parameters.

---

## Repository Structure

```
api-gateway/
├── frontend/                         # Milestone 9: React/TypeScript Dashboard
│   ├── src/
│   │   ├── components/               # Layout, common UI widgets, SVG charts
│   │   ├── context/                  # AuthContext with session-based JWT storage
│   │   ├── pages/                    # Overview, Services, API Keys, Logs, Resilience, Login
│   │   ├── services/                 # Centralized API client & HTTP service adapters
│   │   ├── styles/                   # Modern design system and CSS tokens
│   │   ├── test/                     # Vitest + React Testing Library test suites
│   │   └── types/                    # Strict TypeScript DTO contracts
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── src/main/java/com/edvexa/gateway/ # Spring Boot Backend
│   ├── audit/                        # Request audit logging & database entities
│   ├── config/                       # Resilience, Redis, and Gateway filter beans
│   ├── controller/                   # Admin observability, auth, registry, API key APIs
│   ├── dto/                          # Backend data transfer objects
│   ├── idempotency/                  # Idempotency filter, repository, and models
│   ├── ratelimit/                    # Redis rate limiting service and Lua scripts
│   ├── routing/                      # Dynamic HTTP reverse proxy routing
│   ├── security/                     # JWT filters, API key services, RBAC handlers
│   └── simulator/                    # Downstream service simulators for testing
├── src/main/resources/
│   ├── db/migration/                 # Flyway schema migrations (V1, V2, V3)
│   └── application.yml               # Gateway configuration (skip-worktree protected)
└── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 21 (JDK 21)
- Maven 3.9+
- Node.js 18+ and npm 9+
- PostgreSQL 15+ (listening on port 5432)
- Redis 7+ (listening on port 6379)

---

### Backend Setup & Execution

1. Configure environment variables in `.env` (using `.env.example` as a template):
   ```bash
   cp .env.example .env
   ```

2. Run database migrations and start the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   The backend gateway starts on `http://localhost:8080`.

3. Execute backend automated tests (120 tests):
   ```bash
   mvn test
   ```

---

### Frontend Dashboard Setup & Execution

1. Navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Run the development server:
   ```bash
   npm run dev
   ```
   The dashboard runs at `http://localhost:5173`. API calls to `/api`, `/gateway`, and `/actuator` are automatically proxied to `http://localhost:8080`.

4. Execute frontend automated test suites (16 tests across 6 files):
   ```bash
   npm test
   ```

5. Build for production:
   ```bash
   npm run build
   ```
   Generates optimized assets in `frontend/dist/`.

---

## Security and Credential Safeguards
- Zero credential leaks: Secrets and raw API keys are never exposed in audit logs, responses, or client-side storage.
- One-time reveal: Generated API keys are shown exactly once upon creation and cannot be retrieved afterwards.
- Local configuration protection: `src/main/resources/application.yml` contains local credentials and is managed with `git update-index --skip-worktree` to prevent unintentional commits.
