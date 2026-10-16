export interface ServiceResponse {
  id: string;
  name: string;
  baseUrl: string;
  pathPrefix: string;
  connectionTimeoutMs: number;
  responseTimeoutMs: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceRegistrationRequest {
  name: string;
  baseUrl: string;
  pathPrefix: string;
  connectionTimeoutMs?: number;
  responseTimeoutMs?: number;
  enabled?: boolean;
}

export interface ServiceUpdateRequest {
  name?: string;
  baseUrl?: string;
  pathPrefix?: string;
  connectionTimeoutMs?: number;
  responseTimeoutMs?: number;
  enabled?: boolean;
}

export interface ServiceStatusUpdateRequest {
  enabled: boolean;
}
