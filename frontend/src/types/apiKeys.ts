export type ApiKeyStatus = 'ACTIVE' | 'REVOKED';

export interface ApiKeyResponse {
  id: string;
  clientName: string;
  keyPrefix: string;
  rateLimitPerMinute: number;
  status: ApiKeyStatus;
  createdAt: string;
  revokedAt?: string | null;
}

export interface ApiKeyCreatedResponse {
  id: string;
  clientName: string;
  apiKey: string;
  keyPrefix: string;
  rateLimitPerMinute: number;
  status: ApiKeyStatus;
  createdAt: string;
}

export interface CreateApiKeyRequest {
  clientName: string;
  rateLimitPerMinute: number;
}
