import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { ResiliencePage } from '../pages/ResiliencePage';
import { observabilityService } from '../services/observabilityService';
import { ServiceHealthDto } from '../types/metrics';

vi.mock('../services/observabilityService', () => ({
  observabilityService: {
    getServiceHealth: vi.fn(),
    getMetrics: vi.fn()
  }
}));

describe('ResiliencePage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockHealthData: ServiceHealthDto[] = [
    {
      serviceId: 'order-service',
      name: 'Order Service',
      pathPrefix: '/api/v1/orders',
      baseUrl: 'http://localhost:8081',
      enabled: true,
      circuitBreakerState: 'CLOSED',
      status: 'HEALTHY',
      failureRate: 2.5,
      slowCallRate: 1.0,
      numberOfBufferedCalls: 100,
      numberOfFailedCalls: 2,
      numberOfSuccessfulCalls: 97,
      numberOfNotPermittedCalls: 0,
      connectionTimeoutMs: 2000,
      responseTimeoutMs: 5000
    },
    {
      serviceId: 'payment-service',
      name: 'Payment Service',
      pathPrefix: '/api/v1/payments',
      baseUrl: 'http://localhost:8082',
      enabled: true,
      circuitBreakerState: 'OPEN',
      status: 'UNHEALTHY',
      failureRate: 75.0,
      slowCallRate: 20.0,
      numberOfBufferedCalls: 50,
      numberOfFailedCalls: 37,
      numberOfSuccessfulCalls: 3,
      numberOfNotPermittedCalls: 10,
      connectionTimeoutMs: 1500,
      responseTimeoutMs: 3000
    }
  ];

  it('renders circuit breaker states and health metrics', async () => {
    vi.mocked(observabilityService.getServiceHealth).mockResolvedValue(mockHealthData);

    render(
      <BrowserRouter>
        <ResiliencePage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Resilience & Circuit Breakers')).toBeInTheDocument();
      expect(screen.getByText('Order Service')).toBeInTheDocument();
      expect(screen.getByText('Payment Service')).toBeInTheDocument();
      expect(screen.getByText('CLOSED')).toBeInTheDocument();
      expect(screen.getByText('OPEN')).toBeInTheDocument();
      expect(screen.getByText('2.5%')).toBeInTheDocument();
      expect(screen.getByText('75.0%')).toBeInTheDocument();
    });
  });

  it('displays timeout and buffer metrics correctly', async () => {
    vi.mocked(observabilityService.getServiceHealth).mockResolvedValue(mockHealthData);

    render(
      <BrowserRouter>
        <ResiliencePage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Resp: 5000ms')).toBeInTheDocument();
      expect(screen.getByText('Resp: 3000ms')).toBeInTheDocument();
      expect(screen.getByText('Conn: 2000ms')).toBeInTheDocument();
      expect(screen.getByText('Conn: 1500ms')).toBeInTheDocument();
    });
  });
});
