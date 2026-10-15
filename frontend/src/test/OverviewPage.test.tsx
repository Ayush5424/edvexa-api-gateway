import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { OverviewPage } from '../pages/OverviewPage';
import { observabilityService } from '../services/observabilityService';
import { GatewayMetricsDto, ServiceHealthDto } from '../types/metrics';
import { RecentErrorDto, PageResponse } from '../types/logs';

vi.mock('../services/observabilityService', () => ({
  observabilityService: {
    getMetrics: vi.fn(),
    getServiceHealth: vi.fn(),
    getRecentErrors: vi.fn()
  }
}));

describe('OverviewPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockMetrics: GatewayMetricsDto = {
    totalRequests: 15420,
    successCount: 14850,
    failureCount: 570,
    clientErrorCount: 320,
    serverErrorCount: 250,
    retryCount: 180,
    timeoutCount: 45,
    circuitBreakerOpenCount: 12,
    rateLimitedCount: 95,
    latency: {
      avgMs: 42.5,
      minMs: 5.0,
      maxMs: 850.0,
      p50Ms: 35.0,
      p90Ms: 88.0,
      p95Ms: 145.0,
      p99Ms: 310.0
    },
    requestsByService: {
      'order-service': 8500,
      'payment-service': 4200,
      'inventory-service': 2720
    },
    requestsByClient: {
      'mobile-app': 9200,
      'web-portal': 6220
    },
    requestsByOutcome: {
      'SUCCESS': 14850,
      'CLIENT_ERROR': 320,
      'SERVER_ERROR': 120,
      'RATE_LIMITED': 95,
      'CIRCUIT_BREAKER_OPEN': 12,
      'TIMEOUT': 23
    }
  };

  const mockHealth: ServiceHealthDto[] = [
    {
      serviceId: 'srv-order',
      name: 'Order Service',
      pathPrefix: '/api/v1/orders',
      baseUrl: 'http://localhost:8081',
      enabled: true,
      circuitBreakerState: 'CLOSED',
      failureRate: 1.2,
      slowCallRate: 0.5,
      numberOfBufferedCalls: 100,
      numberOfFailedCalls: 1,
      numberOfSuccessfulCalls: 99,
      numberOfNotPermittedCalls: 0,
      connectionTimeoutMs: 2000,
      responseTimeoutMs: 5000,
      status: 'HEALTHY'
    },
    {
      serviceId: 'srv-payment',
      name: 'Payment Service',
      pathPrefix: '/api/v1/payments',
      baseUrl: 'http://localhost:8082',
      enabled: true,
      circuitBreakerState: 'OPEN',
      failureRate: 65.0,
      slowCallRate: 15.0,
      numberOfBufferedCalls: 50,
      numberOfFailedCalls: 32,
      numberOfSuccessfulCalls: 13,
      numberOfNotPermittedCalls: 12,
      connectionTimeoutMs: 2000,
      responseTimeoutMs: 3000,
      status: 'UNHEALTHY'
    }
  ];

  const mockErrorsPage: PageResponse<RecentErrorDto> = {
    content: [
      {
        id: 1,
        correlationId: 'corr-err-001',
        serviceId: 'srv-payment',
        clientId: 'mobile-app',
        httpMethod: 'POST',
        requestPath: '/api/v1/payments/charge',
        statusCode: 503,
        latencyMs: 15,
        retryCount: 0,
        circuitBreakerState: 'OPEN',
        outcomeCategory: 'CIRCUIT_OPEN',
        errorMessage: 'Circuit breaker is OPEN for Payment Service',
        timestamp: '2026-10-15T18:00:00Z'
      }
    ],
    number: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false
  };

  it('renders loading indicator initially and displays KPIs once data loads', async () => {
    vi.mocked(observabilityService.getMetrics).mockResolvedValue(mockMetrics);
    vi.mocked(observabilityService.getServiceHealth).mockResolvedValue(mockHealth);
    vi.mocked(observabilityService.getRecentErrors).mockResolvedValue(mockErrorsPage);

    render(
      <BrowserRouter>
        <OverviewPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Total Requests')).toBeInTheDocument();
      expect(screen.getByText('15,420')).toBeInTheDocument();
      expect(screen.getByText(/96.3%/i)).toBeInTheDocument();
      expect(screen.getAllByText('42.5 ms').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays service circuit breaker status and recent errors', async () => {
    vi.mocked(observabilityService.getMetrics).mockResolvedValue(mockMetrics);
    vi.mocked(observabilityService.getServiceHealth).mockResolvedValue(mockHealth);
    vi.mocked(observabilityService.getRecentErrors).mockResolvedValue(mockErrorsPage);

    render(
      <BrowserRouter>
        <OverviewPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Order Service')).toBeInTheDocument();
      expect(screen.getByText('Payment Service')).toBeInTheDocument();
      expect(screen.getByText('Circuit breaker is OPEN for Payment Service')).toBeInTheDocument();
    });
  });
});
