import { apiRequest } from './apiClient';
import {
  ServiceRegistrationRequest,
  ServiceResponse,
  ServiceStatusUpdateRequest,
  ServiceUpdateRequest,
} from '../types/services';

export const gatewayService = {
  getAllServices: async (): Promise<ServiceResponse[]> => {
    return apiRequest<ServiceResponse[]>('/api/v1/services', {
      method: 'GET',
    });
  },

  getServiceById: async (id: string): Promise<ServiceResponse> => {
    return apiRequest<ServiceResponse>(`/api/v1/services/${id}`, {
      method: 'GET',
    });
  },

  registerService: async (data: ServiceRegistrationRequest): Promise<ServiceResponse> => {
    return apiRequest<ServiceResponse>('/api/v1/services', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  updateService: async (id: string, data: ServiceUpdateRequest): Promise<ServiceResponse> => {
    return apiRequest<ServiceResponse>(`/api/v1/services/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  toggleServiceStatus: async (id: string, enabled: boolean): Promise<ServiceResponse> => {
    const payload: ServiceStatusUpdateRequest = { enabled };
    return apiRequest<ServiceResponse>(`/api/v1/services/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  deleteService: async (id: string): Promise<void> => {
    await apiRequest<void>(`/api/v1/services/${id}`, {
      method: 'DELETE',
    });
  },
};
