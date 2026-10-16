import { apiRequest } from './apiClient';
import { ApiKeyCreatedResponse, ApiKeyResponse, CreateApiKeyRequest } from '../types/apiKeys';

export const apiKeyService = {
  listApiKeys: async (): Promise<ApiKeyResponse[]> => {
    return apiRequest<ApiKeyResponse[]>('/api/v1/api-keys', {
      method: 'GET',
    });
  },

  getApiKey: async (id: string): Promise<ApiKeyResponse> => {
    return apiRequest<ApiKeyResponse>(`/api/v1/api-keys/${id}`, {
      method: 'GET',
    });
  },

  createApiKey: async (data: CreateApiKeyRequest): Promise<ApiKeyCreatedResponse> => {
    return apiRequest<ApiKeyCreatedResponse>('/api/v1/api-keys', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  revokeApiKey: async (id: string): Promise<ApiKeyResponse> => {
    return apiRequest<ApiKeyResponse>(`/api/v1/api-keys/${id}`, {
      method: 'DELETE',
    });
  },
};
