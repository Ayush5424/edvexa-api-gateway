import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { ServiceRegistryPage } from '../pages/ServiceRegistryPage';
import { gatewayService } from '../services/gatewayService';
import { ServiceResponse } from '../types/services';

vi.mock('../services/gatewayService', () => ({
  gatewayService: {
    getAllServices: vi.fn(),
    getServiceById: vi.fn(),
    registerService: vi.fn(),
    updateService: vi.fn(),
    toggleServiceStatus: vi.fn(),
    deleteService: vi.fn()
  }
}));

describe('ServiceRegistryPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockServices: ServiceResponse[] = [
    {
      id: 'srv-1',
      name: 'Order Processing Service',
      baseUrl: 'http://localhost:8081',
      pathPrefix: '/api/v1/orders',
      connectionTimeoutMs: 2000,
      responseTimeoutMs: 5000,
      enabled: true,
      createdAt: '2026-10-15T10:00:00Z',
      updatedAt: '2026-10-15T10:00:00Z'
    },
    {
      id: 'srv-2',
      name: 'Payment Gateway',
      baseUrl: 'http://localhost:8082',
      pathPrefix: '/api/v1/payments',
      connectionTimeoutMs: 1500,
      responseTimeoutMs: 3000,
      enabled: false,
      createdAt: '2026-10-15T10:00:00Z',
      updatedAt: '2026-10-15T10:00:00Z'
    }
  ];

  it('renders registered services table with status and actions', async () => {
    vi.mocked(gatewayService.getAllServices).mockResolvedValue(mockServices);

    render(
      <BrowserRouter>
        <ServiceRegistryPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Registered Services')).toBeInTheDocument();
      expect(screen.getByText('Order Processing Service')).toBeInTheDocument();
      expect(screen.getByText('Payment Gateway')).toBeInTheDocument();
      expect(screen.getByText('http://localhost:8081')).toBeInTheDocument();
    });
  });

  it('opens registration modal when Register New Service button is clicked', async () => {
    vi.mocked(gatewayService.getAllServices).mockResolvedValue(mockServices);

    render(
      <BrowserRouter>
        <ServiceRegistryPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Order Processing Service')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /register new service/i }));

    expect(screen.getByText('Register Downstream Service')).toBeInTheDocument();
    expect(screen.getByLabelText('Service Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Path Prefix')).toBeInTheDocument();
  });

  it('toggles service active status when toggle button is clicked', async () => {
    vi.mocked(gatewayService.getAllServices).mockResolvedValue(mockServices);
    vi.mocked(gatewayService.toggleServiceStatus).mockResolvedValue({
      ...mockServices[0],
      enabled: false
    });

    render(
      <BrowserRouter>
        <ServiceRegistryPage />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Order Processing Service')).toBeInTheDocument();
    });

    const disableButtons = screen.getAllByTitle('Disable service');
    expect(disableButtons.length).toBeGreaterThan(0);
    fireEvent.click(disableButtons[0]);

    await waitFor(() => {
      expect(gatewayService.toggleServiceStatus).toHaveBeenCalledWith('srv-1', false);
    });
  });
});
