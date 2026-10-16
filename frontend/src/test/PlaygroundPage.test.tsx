import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { PlaygroundPage } from '../pages/PlaygroundPage';
import { gatewayService } from '../services/gatewayService';
import { apiKeyService } from '../services/apiKeyService';
import { playgroundService } from '../services/playgroundService';
import { ServiceResponse } from '../types/services';
import { ApiKeyResponse } from '../types/apiKeys';

vi.mock('../services/gatewayService', () => ({
  gatewayService: {
    getAllServices: vi.fn(),
  },
}));

vi.mock('../services/apiKeyService', () => ({
  apiKeyService: {
    listApiKeys: vi.fn(),
  },
}));

vi.mock('../services/playgroundService', () => ({
  playgroundService: {
    buildGatewayUrl: vi.fn((prefix: string, path: string, params: { key: string; value: string; enabled: boolean }[]) => {
      const cleanPrefix = prefix ? (prefix.startsWith('/') ? prefix : `/${prefix}`) : '';
      const cleanPath = path ? (path.startsWith('/') ? path : `/${path}`) : '';
      const base = `/gateway${cleanPrefix}${cleanPath}`;
      const active = params.filter((p) => p.enabled && p.key);
      const query = active.length > 0 ? `?${active.map((p) => `${p.key}=${p.value}`).join('&')}` : '';
      return { path: base, queryString: query, fullGatewayPath: `${base}${query}` };
    }),
    executeGatewayRequest: vi.fn(),
    generateCurlCommand: vi.fn(() => 'curl -i -X GET "http://localhost:8080/gateway/test" -H "X-API-Key: ${GATEWAY_API_KEY}"'),
    historyStorage: {
      getHistory: vi.fn(() => []),
      saveHistoryItem: vi.fn((item) => ({
        ...item,
        id: 'mock_hist_1',
        timestamp: '2026-10-16T12:00:00Z',
      })),
      clearHistory: vi.fn(),
    },
  },
}));

describe('PlaygroundPage Component', () => {
  const mockServices: ServiceResponse[] = [
    {
      id: 'srv-1',
      name: 'Order Service',
      baseUrl: 'http://localhost:8081',
      pathPrefix: '/orders',
      connectionTimeoutMs: 2000,
      responseTimeoutMs: 5000,
      enabled: true,
      createdAt: '2026-10-15T10:00:00Z',
      updatedAt: '2026-10-15T10:00:00Z',
    },
    {
      id: 'srv-2',
      name: 'Test Service',
      baseUrl: 'http://localhost:8081',
      pathPrefix: '/test',
      connectionTimeoutMs: 1500,
      responseTimeoutMs: 3000,
      enabled: true,
      createdAt: '2026-10-15T10:00:00Z',
      updatedAt: '2026-10-15T10:00:00Z',
    },
  ];

  const mockApiKeys: ApiKeyResponse[] = [
    {
      id: 'key-1',
      clientName: 'Mobile Client',
      keyPrefix: 'edx_1234abcd',
      rateLimitPerMinute: 60,
      status: 'ACTIVE',
      createdAt: '2026-10-15T10:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(gatewayService.getAllServices).mockResolvedValue(mockServices);
    vi.mocked(apiKeyService.listApiKeys).mockResolvedValue(mockApiKeys);
    vi.mocked(playgroundService.historyStorage.getHistory).mockReturnValue([]);
  });

  it('renders Request Builder and loads registered services into selector', async () => {
    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('API Playground')).toBeInTheDocument();
      expect(screen.getByText('Request Builder')).toBeInTheDocument();
      expect(screen.getByText('Gateway Response')).toBeInTheDocument();
      expect(screen.getByText('Order Service (/orders)')).toBeInTheDocument();
      expect(screen.getByText('Test Service (/test)')).toBeInTheDocument();
    });
  });

  it('allows changing HTTP method and endpoint subpath', async () => {
    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('GET')).toBeInTheDocument();
    });

    const methodSelect = screen.getByDisplayValue('GET');
    fireEvent.change(methodSelect, { target: { value: 'POST' } });
    expect(screen.getByDisplayValue('POST')).toBeInTheDocument();

    const subpathInput = screen.getByPlaceholderText('/subpath (optional)');
    fireEvent.change(subpathInput, { target: { value: '/123' } });
    expect(subpathInput).toHaveValue('/123');
  });

  it('validates JSON request body and flags malformed syntax', async () => {
    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Request Builder')).toBeInTheDocument();
    });

    // Switch to Body tab
    const bodyTabButton = screen.getByRole('button', { name: /Body/i });
    fireEvent.click(bodyTabButton);

    const textarea = screen.getByPlaceholderText('{}');
    fireEvent.change(textarea, { target: { value: '{"badJson": invalid}' } });

    await waitFor(() => {
      expect(screen.getByText(/Expected/i)).toBeInTheDocument();
    });
  });

  it('executes gateway request and renders real response details and correlation ID', async () => {
    vi.mocked(playgroundService.executeGatewayRequest).mockResolvedValue({
      status: 200,
      statusText: 'OK',
      durationMs: 38,
      sizeBytes: 154,
      headers: {
        'content-type': 'application/json',
        'x-correlation-id': 'corr-play-test-001',
        'x-ratelimit-remaining': '59',
        'x-ratelimit-limit': '60',
      },
      body: '{\n  "status": "healthy"\n}',
      isJson: true,
      correlationId: 'corr-play-test-001',
      rateLimitLimit: '60',
      rateLimitRemaining: '59',
    });

    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Send/i })).toBeInTheDocument();
    });

    const sendButton = screen.getByRole('button', { name: /Send/i });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(screen.getByText('200 OK')).toBeInTheDocument();
      expect(screen.getByText('38 ms')).toBeInTheDocument();
      expect(screen.getByText(/Correlation: corr-play-test-001/i)).toBeInTheDocument();
      expect(screen.getByText(/healthy/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Trace in Logs/i })).toBeInTheDocument();
    });
  });

  it('renders HTTP error responses (e.g. 429 Too Many Requests) gracefully', async () => {
    vi.mocked(playgroundService.executeGatewayRequest).mockResolvedValue({
      status: 429,
      statusText: 'Too Many Requests',
      durationMs: 12,
      sizeBytes: 180,
      headers: {
        'content-type': 'application/json',
        'x-correlation-id': 'corr-rate-exceeded-99',
        'x-ratelimit-remaining': '0',
        'retry-after': '45',
      },
      body: '{\n  "error": "Rate limit exceeded"\n}',
      isJson: true,
      correlationId: 'corr-rate-exceeded-99',
      rateLimitRemaining: '0',
      retryAfter: '45',
    });

    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Send/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Send/i }));

    await waitFor(() => {
      expect(screen.getByText('429 Too Many Requests')).toBeInTheDocument();
      expect(screen.getByText('Retry-After: 45s')).toBeInTheDocument();
      expect(screen.getByText(/Rate limit exceeded/i)).toBeInTheDocument();
    });
  });

  it('copies cURL command to clipboard with safe placeholder', async () => {
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockImplementation(() => Promise.resolve()),
      },
    });

    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Copy cURL/i })).toBeInTheDocument();
    });

    const copyBtn = screen.getByRole('button', { name: /Copy cURL/i });
    fireEvent.click(copyBtn);

    expect(navigator.clipboard.writeText).toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByText(/cURL command copied to clipboard with safe \${GATEWAY_API_KEY} placeholder/i)).toBeInTheDocument();
    });
  });

  it('restores previous request configuration from history without storing raw secrets', async () => {
    vi.mocked(playgroundService.historyStorage.getHistory).mockReturnValue([
      {
        id: 'hist-1',
        timestamp: '2026-10-16T12:00:00Z',
        method: 'POST',
        serviceId: 'srv-1',
        serviceName: 'Order Service',
        pathPrefix: '/orders',
        endpointPath: '/create',
        queryParams: [{ key: 'dryRun', value: 'true' }],
        headers: [{ key: 'Idempotency-Key', value: 'key-123' }],
        bodySnippet: '{"amount": 50}',
        responseStatus: 201,
        durationMs: 45,
        correlationId: 'corr-saved-1',
      },
    ]);

    render(
      <BrowserRouter>
        <PlaygroundPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Recent Requests')).toBeInTheDocument();
      expect(screen.getByText(/gateway\/orders\/create/i)).toBeInTheDocument();
      expect(screen.getByText('201')).toBeInTheDocument();
    });

    const restoreButton = screen.getByRole('button', { name: /Restore/i });
    fireEvent.click(restoreButton);

    await waitFor(() => {
      expect(screen.getByDisplayValue('POST')).toBeInTheDocument();
      expect(screen.getByDisplayValue('/create')).toBeInTheDocument();
      expect(screen.getByText(/Restored POST configuration for Order Service/i)).toBeInTheDocument();
    });
  });
});
