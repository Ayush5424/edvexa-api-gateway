# Edvexa API Gateway - Demonstration Walkthrough & Verification Guide

This document provides step-by-step instructions and verified terminal outputs for demonstrating the core resilience, security, and routing capabilities of the Edvexa Distributed API Gateway.

---

## 1. System Architecture & Prerequisites

Ensure the required local components are running:
- **PostgreSQL**: Port `5432` (`edvexa_gateway` database)
- **Redis**: Port `6379`
- **API Gateway Backend**: Port `8080`
- **Downstream Services**: Port `8081` (Standalone Java service) and Port `8080` (Simulated Orders/Payments under `/api/v1/simulated/**`)
- **Monitoring Dashboard**: Port `3000` (`http://localhost:3000`)

![Dashboard Overview](assets/screenshots/Edvexa%20Overview%20SS1.png)

---

## 2. Demonstration Scenarios

### Scenario A: Normal Gateway Request & Routing

**Objective:**
Demonstrate valid request forwarding through the gateway to a registered downstream service, showing path prefix resolution, correlation ID injection, and rate limiting headers.

#### 1. Command
```bash
curl -i -X GET http://localhost:8080/gateway/test \
  -H "X-API-Key: YOUR_ACTIVE_API_KEY" \
  -H "X-Correlation-ID: demo-corr-001"
```

#### 2. Actual Gateway Response
```http
HTTP/1.1 200 OK
X-Correlation-ID: demo-corr-001
X-RateLimit-Limit: 120
X-RateLimit-Remaining: 119
X-RateLimit-Reset: 1729079520
X-Gateway-Service-ID: srv-test-service
Content-Type: application/json; charset=UTF-8
Content-Length: 79

{
  "service": "test-service",
  "status": "healthy",
  "message": "Request reached the downstream Java service"
}
```

![Terminal Verification](assets/screenshots/Edvexa%20Terminal%20SS.png)

#### 3. What Occurred Behind the Scenes:
1. `CorrelationIdFilter` captured or generated `demo-corr-001` and placed it into thread-local context.
2. `ApiKeyAuthenticationFilter` hashed the raw key with SHA-256 and verified active client permissions in PostgreSQL.
3. `RateLimitingFilter` executed an atomic Redis Lua sliding-window script, decrementing the remaining quota.
4. `GatewayRoutingService` matched prefix `/test` to `http://localhost:8081` and forwarded the request.
5. `RequestAuditService` persisted an audit log record containing latency, client identity, and outcome `SUCCESS`.

---

### Scenario B: Distributed Rate Limiting & 429 Too Many Requests

**Objective:**
Demonstrate rate limit enforcement when a client exceeds their configured requests-per-minute quota.

#### 1. Trigger Rate Limit
Using an API key with a configured limit (e.g., 20 requests/min), send rapid requests:
```bash
for i in {1..25}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X GET http://localhost:8080/gateway/test \
    -H "X-API-Key: YOUR_ACTIVE_API_KEY"
done
```

#### 2. Exceeded Limit Response
```http
HTTP/1.1 429 Too Many Requests
X-Correlation-ID: 7a82b0f4-d191-4c22-b5e2-631d87a91c10
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1729079580
Retry-After: 42
Content-Type: application/json

{
  "timestamp": "2026-10-16T11:15:30.120Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 42 seconds.",
  "path": "/gateway/test",
  "correlationId": "7a82b0f4-d191-4c22-b5e2-631d87a91c10"
}
```

#### 3. Recovery:
- The rate limiter uses a 60-second sliding window implemented via Redis sorted sets.
- Once the `Retry-After` duration elapses, subsequent requests succeed with refreshed quota.

---

### Scenario C: Downstream Failure, Circuit Breaker & Recovery

**Objective:**
Demonstrate Resilience4j circuit breaker state transitions: `CLOSED` -> `OPEN` -> `HALF_OPEN` -> `CLOSED`.

#### 1. Induce Controlled Failure on Downstream Simulator
Configure the downstream simulator to return HTTP 503:
```bash
curl -X POST http://localhost:8080/api/v1/simulated/orders/config \
  -H "Content-Type: application/json" \
  -d '{"failureRate": 1.0, "failureStatusCode": 503, "forcedFailureCount": 10}'
```

#### 2. Send Traffic to Trigger Circuit Breaker
Send 5 requests through `/gateway/orders`:
```bash
curl -i -X GET http://localhost:8080/gateway/orders \
  -H "X-API-Key: YOUR_ACTIVE_API_KEY"
```

#### 3. Initial Failures (Retries Exhausted, Circuit CLOSED -> OPEN)
The gateway attempts bounded retries (up to 3 retries with exponential backoff). Once failures exceed the threshold (50% failure rate across minimum call buffer), the circuit breaker transitions to `OPEN`.

Subsequent calls immediately fail-fast:
```http
HTTP/1.1 503 Service Unavailable
X-Correlation-ID: cb-failfast-001
Content-Type: application/json

{
  "timestamp": "2026-10-16T11:18:05.412Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Downstream service circuit breaker is OPEN",
  "path": "/gateway/orders",
  "correlationId": "cb-failfast-001"
}
```

#### 4. Restore Downstream Service
Reset the simulator to normal operation:
```bash
curl -X POST http://localhost:8080/api/v1/simulated/orders/reset
```

#### 5. Verify Recovery
After the wait duration in `OPEN` state (e.g., 5 seconds), the circuit enters `HALF_OPEN`. Permitted trial calls succeed against the healthy simulator, transitioning the circuit breaker back to `CLOSED`.

![Resilience and Circuit Breaker Monitoring](assets/screenshots/Edvexa%20RCB%20SS.png)

---

### Scenario D: Interactive API Playground Execution & Log Tracing

**Objective:**
Demonstrate executing HTTP requests through the gateway directly from the browser dashboard, inspecting gateway headers, and deep-linking directly to audit logs.

#### 1. Navigate to API Playground
1. Open the dashboard at `http://localhost:3000`.
2. Click **API Playground** in the left sidebar navigation (`/playground`).

#### 2. Configure a GET Request to Downstream Test Service
1. **Method:** Select `GET`.
2. **Service:** Select `test-service (/test)`.
3. **Endpoint Path:** Leave empty or type `/` (Gateway URL previews: `/gateway/test`).
4. **Authentication:** Choose **Select Active Key from Inventory** and pick an active key, or select **Enter API Key Manually** and input an active key.
5. Click **Send Request**.

#### 3. Inspect Live Response
- **Status:** `200 OK` (Green badge).
- **Latency & Size:** Accurate round-trip time in milliseconds and byte count.
- **Headers:** Inspect `X-Correlation-ID`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-Gateway-Service-ID`.
- **Response Body:** Formatted JSON payload:
  ```json
  {
    "service": "test-service",
    "status": "healthy",
    "message": "Request reached the downstream Java service"
  }
  ```

![API Playground Execution](assets/screenshots/API%20PlayGround.png)

#### 4. Trace in Logs
1. In the Response section, click the **[Trace in Logs]** button adjacent to the correlation ID badge.
2. The dashboard navigates automatically to `/logs?correlationId=<ID>`.
3. The Request Logs view filters to the exact request executed in the Playground, displaying client identity, target service, latency, and status code.

![Request Logs & Correlation Tracing](assets/screenshots/Edvexa%20Request%20Logs.png)

#### 5. Test Mutation Request with JSON Body
1. Return to **API Playground**.
2. **Method:** Select `POST`.
3. **Service:** Select `orders-service (/orders)`.
4. **Endpoint Path:** Type `/api/v1/simulated/orders`.
5. Under **Body**, provide a JSON payload:
   ```json
   {
     "customerId": "cust-demo-123",
     "amount": 49.99
   }
   ```
6. Click **Format JSON** to ensure clean formatting.
7. Click **Send Request**. The simulated downstream order creation response returns `201 Created` or `200 OK`.
8. Click **Copy as cURL** to copy the command with safe `${GATEWAY_API_KEY}` placeholder.

---

## 3. Video Recording Script for Self-Recording

If recording a demonstration video, follow this 3-minute script:

| Time | Action | Screen Focus | Narration Points |
| :--- | :--- | :--- | :--- |
| **0:00 - 0:30** | Open Dashboard at `http://localhost:3000` | Overview & Metrics Page | Highlight active services, total request counts, p95/p99 latency charts, and system status badges. |
| **0:30 - 1:00** | Navigate to Service Registry & API Keys | Services & Keys Pages | Show registered routes (`/test`, `/orders`), timeout configurations, and API key management with prefix masking. |
| **1:00 - 1:45** | Interactive API Playground & Audit Tracing | Playground & Logs | Demonstrate browser-based request execution, selecting `test-service`, inspecting `X-Correlation-ID` and rate limit headers, and using **Trace in Logs** to correlate with audit entries. |
| **1:45 - 2:30** | Run Downstream Failure & Recovery | Terminal & Resilience Page | Inject 503 errors via simulator config. Show Circuit Breaker badge turn red (`OPEN`). Reset simulator and show it transition back to green (`CLOSED`). |
| **2:30 - 3:00** | Open Swagger UI at `http://localhost:8080/swagger-ui/index.html` | Swagger UI Browser Tab | Show documented schemas, auth endpoints, and interactive documentation. |

