export interface LatencyMetricsDto {
  avgMs: number;
  minMs: number;
  maxMs: number;
  p50Ms: number;
  p90Ms: number;
  p95Ms: number;
  p99Ms: number;
}

export interface GatewayMetricsDto {
  totalRequests: number;
  successCount: number;
  failureCount: number;
  clientErrorCount: number;
  serverErrorCount: number;
  retryCount: number;
  timeoutCount: number;
  circuitBreakerOpenCount: number;
  rateLimitedCount: number;
  latency: LatencyMetricsDto;
  requestsByService: Record<string, number>;
  requestsByClient: Record<string, number>;
  requestsByOutcome: Record<string, number>;
}

export interface ServiceHealthDto {
  serviceId: string;
  name: string;
  pathPrefix: string;
  baseUrl: string;
  enabled: boolean;
  circuitBreakerState: 'CLOSED' | 'OPEN' | 'HALF_OPEN' | string;
  failureRate: number;
  slowCallRate: number;
  numberOfBufferedCalls: number;
  numberOfFailedCalls: number;
  numberOfSuccessfulCalls: number;
  numberOfNotPermittedCalls: number;
  connectionTimeoutMs: number;
  responseTimeoutMs: number;
  status: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'DISABLED';
}
