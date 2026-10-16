import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { RequestLogsPage } from '../pages/RequestLogsPage';
import { observabilityService } from '../services/observabilityService';
import { PageResponse, GatewayRequestLog } from '../types/logs';

vi.mock('../services/observabilityService', () => ({
  observabilityService: {
    getAuditLogs: vi.fn(),
    getLogsByCorrelationId: vi.fn(),
    getRecentErrors: vi.fn(),
    getMetrics: vi.fn(),
    getServiceHealth: vi.fn()
  }
}));

describe('RequestLogsPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockLogsResponse: PageResponse<GatewayRequestLog> = {
    content: [
      {
        id: 1,
        correlationId: 'order-corr-uuid-1234',
        serviceId: 'order-service',
        clientId: 'mobile-client',
        httpMethod: 'POST',
        requestPath: '/api/v1/orders/checkout',
        statusCode: 201,
        latencyMs: 48,
        retryCount: 0,
        circuitBreakerState: 'CLOSED',
        outcomeCategory: 'SUCCESS',
        errorMessage: null,
        timestamp: '2026-10-15T18:30:00Z'
      },
      {
        id: 2,
        correlationId: 'payment-corr-uuid-5678',
        serviceId: 'payment-service',
        clientId: 'web-client',
        httpMethod: 'POST',
        requestPath: '/api/v1/payments/pay',
        statusCode: 429,
        latencyMs: 2,
        retryCount: 0,
        circuitBreakerState: 'CLOSED',
        outcomeCategory: 'RATE_LIMITED',
        errorMessage: 'Client rate limit exceeded',
        timestamp: '2026-10-15T18:31:00Z'
      }
    ],
    number: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
    empty: false
  };

  it('renders audit logs with correlation IDs, outcome, and status', async () => {
    vi.mocked(observabilityService.getAuditLogs).mockResolvedValue(mockLogsResponse);

    render(
      <BrowserRouter>
        <RequestLogsPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Request Audit Logs')).toBeInTheDocument();
      expect(screen.getByText(/order-corr-u/)).toBeInTheDocument();
      expect(screen.getByText('/api/v1/orders/checkout')).toBeInTheDocument();
      expect(screen.getByText('/api/v1/payments/pay')).toBeInTheDocument();
    });
  });

  it('opens log detail modal when view details button is clicked', async () => {
    vi.mocked(observabilityService.getAuditLogs).mockResolvedValue(mockLogsResponse);

    render(
      <BrowserRouter>
        <RequestLogsPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/order-corr-u/)).toBeInTheDocument();
    });

    const viewButtons = screen.getAllByTitle('View diagnostic details');
    expect(viewButtons.length).toBeGreaterThan(0);
    fireEvent.click(viewButtons[0]);

    expect(screen.getByText('Request Diagnostic Details')).toBeInTheDocument();
    expect(screen.getByText('order-corr-uuid-1234')).toBeInTheDocument();
    expect(screen.getByText('Total Gateway Latency')).toBeInTheDocument();
  });

  it('searches by correlation ID when search query is submitted', async () => {
    vi.mocked(observabilityService.getAuditLogs).mockResolvedValue(mockLogsResponse);
    vi.mocked(observabilityService.getLogsByCorrelationId).mockResolvedValue([
      mockLogsResponse.content[0]
    ]);

    render(
      <BrowserRouter>
        <RequestLogsPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/order-corr-u/)).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search by exact trace id/i);
    fireEvent.change(searchInput, { target: { value: 'order-corr-uuid-1234' } });

    // Submit form by clicking Filter button
    fireEvent.click(screen.getByRole('button', { name: /filter/i }));

    await waitFor(() => {
      expect(observabilityService.getLogsByCorrelationId).toHaveBeenCalledWith('order-corr-uuid-1234');
    });
  });
});
