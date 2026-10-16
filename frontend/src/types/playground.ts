export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface KeyValuePair {
  id: string;
  key: string;
  value: string;
  enabled: boolean;
}

export interface PlaygroundRequestConfig {
  method: HttpMethod;
  serviceId: string;
  endpointPath: string;
  queryParams: KeyValuePair[];
  headers: KeyValuePair[];
  body: string;
  authType: 'api-key-inventory' | 'api-key-manual' | 'none';
  selectedApiKeyId?: string;
  apiKeySecret: string; // Stored strictly in memory during active session
}

export interface PlaygroundResponse {
  status: number;
  statusText: string;
  durationMs: number;
  sizeBytes: number;
  headers: Record<string, string>;
  body: string;
  isJson: boolean;
  correlationId?: string;
  rateLimitLimit?: string;
  rateLimitRemaining?: string;
  rateLimitReset?: string;
  retryAfter?: string;
  gatewayServiceId?: string;
  cached?: boolean;
}

export interface PlaygroundHistoryItem {
  id: string;
  timestamp: string;
  method: HttpMethod;
  serviceId: string;
  serviceName: string;
  pathPrefix: string;
  endpointPath: string;
  queryParams: { key: string; value: string }[];
  headers: { key: string; value: string }[];
  bodySnippet?: string;
  responseStatus?: number;
  durationMs?: number;
  correlationId?: string;
}
