import { apiRequest } from './apiClient';
import { GatewayMetricsDto, ServiceHealthDto } from '../types/metrics';
import { GatewayRequestLog, PageResponse, RecentErrorDto } from '../types/logs';

export interface AuditLogFilters {
  serviceId?: string;
  clientId?: string;
  outcome?: string;
  page?: number;
  size?: number;
}

export const observabilityService = {
  getMetrics: async (): Promise<GatewayMetricsDto> => {
    return apiRequest<GatewayMetricsDto>('/api/v1/admin/metrics', {
      method: 'GET',
    });
  },

  getServiceHealth: async (): Promise<ServiceHealthDto[]> => {
    return apiRequest<ServiceHealthDto[]>('/api/v1/admin/services/health', {
      method: 'GET',
    });
  },

  getSingleServiceHealth: async (serviceId: string): Promise<ServiceHealthDto> => {
    return apiRequest<ServiceHealthDto>(`/api/v1/admin/services/${serviceId}/health`, {
      method: 'GET',
    });
  },

  getAuditLogs: async (filters: AuditLogFilters = {}): Promise<PageResponse<GatewayRequestLog>> => {
    const params = new URLSearchParams();
    if (filters.serviceId) params.set('serviceId', filters.serviceId);
    if (filters.clientId) params.set('clientId', filters.clientId);
    if (filters.outcome) params.set('outcome', filters.outcome);
    if (filters.page !== undefined) params.set('page', String(filters.page));
    if (filters.size !== undefined) params.set('size', String(filters.size));

    const queryString = params.toString() ? `?${params.toString()}` : '';
    return apiRequest<PageResponse<GatewayRequestLog>>(`/api/v1/admin/audit/logs${queryString}`, {
      method: 'GET',
    });
  },

  getRecentErrors: async (page = 0, size = 20): Promise<PageResponse<RecentErrorDto>> => {
    return apiRequest<PageResponse<RecentErrorDto>>(
      `/api/v1/admin/audit/errors?page=${page}&size=${size}`,
      {
        method: 'GET',
      }
    );
  },

  getLogsByCorrelationId: async (correlationId: string): Promise<GatewayRequestLog[]> => {
    return apiRequest<GatewayRequestLog[]>(
      `/api/v1/admin/audit/correlation/${encodeURIComponent(correlationId)}`,
      {
        method: 'GET',
      }
    );
  },
};
