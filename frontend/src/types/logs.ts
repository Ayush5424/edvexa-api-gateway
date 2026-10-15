export interface GatewayRequestLog {
  id: number;
  correlationId: string;
  clientId: string | null;
  serviceId: string | null;
  httpMethod: string;
  requestPath: string;
  statusCode: number;
  latencyMs: number;
  retryCount: number;
  circuitBreakerState: string | null;
  outcomeCategory: string;
  errorMessage: string | null;
  timestamp: string;
}

export interface RecentErrorDto {
  id: number;
  correlationId: string;
  clientId: string | null;
  serviceId: string | null;
  httpMethod: string;
  requestPath: string;
  statusCode: number;
  latencyMs: number;
  retryCount: number;
  circuitBreakerState: string | null;
  outcomeCategory: string;
  errorMessage: string | null;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
