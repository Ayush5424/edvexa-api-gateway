import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { ApiKeysPage } from '../pages/ApiKeysPage';
import { apiKeyService } from '../services/apiKeyService';
import { ApiKeyResponse, ApiKeyCreatedResponse } from '../types/apiKeys';

vi.mock('../services/apiKeyService', () => ({
  apiKeyService: {
    listApiKeys: vi.fn(),
    createApiKey: vi.fn(),
    revokeApiKey: vi.fn()
  }
}));

describe('ApiKeysPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockApiKeys: ApiKeyResponse[] = [
    {
      id: 'key-1',
      clientName: 'mobile-app',
      keyPrefix: 'edvx_live_mob',
      rateLimitPerMinute: 100,
      status: 'ACTIVE',
      createdAt: '2026-10-15T09:00:00Z',
      revokedAt: null
    },
    {
      id: 'key-2',
      clientName: 'internal-batch',
      keyPrefix: 'edvx_live_bat',
      rateLimitPerMinute: 500,
      status: 'REVOKED',
      createdAt: '2026-10-14T12:00:00Z',
      revokedAt: '2026-10-15T08:00:00Z'
    }
  ];

  it('renders API keys table with prefix and client details', async () => {
    vi.mocked(apiKeyService.listApiKeys).mockResolvedValue(mockApiKeys);

    render(
      <BrowserRouter>
        <ApiKeysPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('API Client Credentials')).toBeInTheDocument();
      expect(screen.getByText('mobile-app')).toBeInTheDocument();
      expect(screen.getByText('internal-batch')).toBeInTheDocument();
      expect(screen.getByText(/edvx_live_mob/)).toBeInTheDocument();
    });
  });

  it('displays one-time raw secret reveal modal upon creating an API key', async () => {
    vi.mocked(apiKeyService.listApiKeys).mockResolvedValue(mockApiKeys);

    const createdResponse: ApiKeyCreatedResponse = {
      id: 'key-3',
      clientName: 'partner-portal',
      keyPrefix: 'edvx_live_prt',
      apiKey: 'edvx_live_prt_9f8e7d6c5b4a3210secretkey',
      rateLimitPerMinute: 60,
      status: 'ACTIVE',
      createdAt: '2026-10-15T19:00:00Z'
    };

    vi.mocked(apiKeyService.createApiKey).mockResolvedValue(createdResponse);

    render(
      <BrowserRouter>
        <ApiKeysPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('API Client Credentials')).toBeInTheDocument();
    });

    // Open create modal
    fireEvent.click(screen.getByRole('button', { name: /generate new api key/i }));
    expect(screen.getByText('Generate Client API Key')).toBeInTheDocument();

    // Fill form
    fireEvent.change(screen.getByLabelText(/client \/ application name/i), { target: { value: 'partner-portal' } });

    // Submit
    fireEvent.click(screen.getByRole('button', { name: /^generate key$/i }));

    // Verify one-time secret reveal modal is displayed with security warning
    await waitFor(() => {
      expect(screen.getByText('API Key Generated (One-Time Reveal)')).toBeInTheDocument();
      expect(screen.getByText(/Copy this API key now/i)).toBeInTheDocument();
      expect(screen.getByDisplayValue('edvx_live_prt_9f8e7d6c5b4a3210secretkey')).toBeInTheDocument();
    });
  });

  it('allows revoking an existing API key', async () => {
    vi.mocked(apiKeyService.listApiKeys).mockResolvedValue(mockApiKeys);
    vi.mocked(apiKeyService.revokeApiKey).mockResolvedValue({
      ...mockApiKeys[0],
      status: 'REVOKED'
    });

    render(
      <BrowserRouter>
        <ApiKeysPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('mobile-app')).toBeInTheDocument();
    });

    // Find and click revoke button on active key
    const revokeButtons = screen.getAllByTitle('Revoke API key');
    expect(revokeButtons.length).toBeGreaterThan(0);
    fireEvent.click(revokeButtons[0]);

    // Revoke confirmation modal should show
    expect(screen.getByText('Confirm Key Revocation')).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to revoke the API key for client/i)).toBeInTheDocument();

    // Confirm revocation
    fireEvent.click(screen.getByRole('button', { name: /^revoke api key$/i }));

    await waitFor(() => {
      expect(apiKeyService.revokeApiKey).toHaveBeenCalledWith('key-1');
    });
  });
});
