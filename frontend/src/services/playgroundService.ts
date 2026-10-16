import { KeyValuePair, PlaygroundHistoryItem, PlaygroundResponse } from '../types/playground';

const HISTORY_STORAGE_KEY = 'edvexa_playground_history';
const MAX_HISTORY_ITEMS = 25;

export const playgroundService = {
  /**
   * Constructs the destination gateway URL from the service prefix, subpath, and query parameters.
   */
  buildGatewayUrl: (
    servicePrefix: string,
    endpointPath: string,
    queryParams: KeyValuePair[] = []
  ): { path: string; queryString: string; fullGatewayPath: string } => {
    let cleanPrefix = servicePrefix ? servicePrefix.trim() : '';
    if (!cleanPrefix.startsWith('/')) {
      cleanPrefix = cleanPrefix ? `/${cleanPrefix}` : '';
    }
    // Remove trailing slash from prefix if subpath follows
    if (cleanPrefix.endsWith('/')) {
      cleanPrefix = cleanPrefix.slice(0, -1);
    }

    let cleanSubpath = endpointPath ? endpointPath.trim() : '';
    if (cleanSubpath && !cleanSubpath.startsWith('/')) {
      cleanSubpath = `/${cleanSubpath}`;
    }

    const path = `/gateway${cleanPrefix}${cleanSubpath}` || '/gateway';

    const activeParams = queryParams.filter((p) => p.enabled && p.key.trim().length > 0);
    let queryString = '';
    if (activeParams.length > 0) {
      const searchParams = new URLSearchParams();
      activeParams.forEach((p) => {
        searchParams.append(p.key.trim(), p.value);
      });
      queryString = `?${searchParams.toString()}`;
    }

    return {
      path,
      queryString,
      fullGatewayPath: `${path}${queryString}`,
    };
  },

  /**
   * Executes an HTTP request through the actual API Gateway.
   * Does not throw on HTTP error statuses (4xx, 5xx) so they can be inspected.
   */
  executeGatewayRequest: async (params: {
    url: string;
    method: string;
    headers: Record<string, string>;
    body?: string;
  }): Promise<PlaygroundResponse> => {
    const { url, method, headers, body } = params;

    const requestInit: RequestInit = {
      method,
      headers: new Headers(headers),
    };

    if (method !== 'GET' && method !== 'HEAD' && body !== undefined && body.trim().length > 0) {
      requestInit.body = body;
    }

    const startTime = performance.now();
    let response: Response;

    try {
      response = await fetch(url, requestInit);
    } catch (err: unknown) {
      const durationMs = Math.round(performance.now() - startTime);
      const msg = err instanceof Error ? err.message : 'Network connection failure';
      throw new Error(`Network failure: ${msg}. Gateway endpoint may be unreachable or connection was refused.`);
    }

    const endTime = performance.now();
    const durationMs = Math.max(1, Math.round(endTime - startTime));

    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value, key) => {
      responseHeaders[key.toLowerCase()] = value;
    });

    const rawBody = await response.text();
    let isJson = false;
    let formattedBody = rawBody;

    if (rawBody.trim().length > 0) {
      try {
        const parsed = JSON.parse(rawBody);
        formattedBody = JSON.stringify(parsed, null, 2);
        isJson = true;
      } catch {
        isJson = false;
        formattedBody = rawBody;
      }
    }

    let sizeBytes = 0;
    const contentLength = response.headers.get('content-length');
    if (contentLength) {
      sizeBytes = parseInt(contentLength, 10);
    } else {
      sizeBytes = new Blob([rawBody]).size;
    }

    return {
      status: response.status,
      statusText: response.statusText || getFallbackStatusText(response.status),
      durationMs,
      sizeBytes,
      headers: responseHeaders,
      body: formattedBody,
      isJson,
      correlationId: responseHeaders['x-correlation-id'],
      rateLimitLimit: responseHeaders['x-ratelimit-limit'],
      rateLimitRemaining: responseHeaders['x-ratelimit-remaining'],
      rateLimitReset: responseHeaders['x-ratelimit-reset'],
      retryAfter: responseHeaders['retry-after'],
      gatewayServiceId: responseHeaders['x-gateway-service-id'],
      cached: responseHeaders['x-cache']?.toUpperCase() === 'HIT',
    };
  },

  /**
   * Generates a safe, reproducible cURL command replacing secrets with placeholders.
   */
  generateCurlCommand: (params: {
    method: string;
    fullUrl: string;
    headers: KeyValuePair[];
    body?: string;
    hasApiKey: boolean;
  }): string => {
    const { method, fullUrl, headers, body, hasApiKey } = params;

    const parts: string[] = ['curl -i', `-X ${method}`, `"${fullUrl}"`];

    if (hasApiKey) {
      parts.push('-H "X-API-Key: ${GATEWAY_API_KEY}"');
    }

    headers
      .filter((h) => h.enabled && h.key.trim().length > 0)
      .forEach((h) => {
        const key = h.key.trim();
        // Skip sensitive headers from explicit list to avoid leaks
        if (key.toLowerCase() !== 'x-api-key' && key.toLowerCase() !== 'authorization') {
          const escapedVal = h.value.replace(/"/g, '\\"');
          parts.push(`-H "${key}: ${escapedVal}"`);
        }
      });

    if (method !== 'GET' && method !== 'HEAD' && body && body.trim().length > 0) {
      // Escape for single-quoted bash string
      const escapedBody = body.replace(/'/g, "'\\''");
      parts.push(`-d '${escapedBody}'`);
    }

    return parts.join(' \\\n  ');
  },

  /**
   * Non-sensitive local history storage helpers.
   */
  historyStorage: {
    getHistory: (): PlaygroundHistoryItem[] => {
      try {
        const raw = localStorage.getItem(HISTORY_STORAGE_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
      } catch {
        return [];
      }
    },

    saveHistoryItem: (
      item: Omit<PlaygroundHistoryItem, 'id' | 'timestamp'>
    ): PlaygroundHistoryItem => {
      const newItem: PlaygroundHistoryItem = {
        ...item,
        id: `req_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
        timestamp: new Date().toISOString(),
        // Strip any accidental sensitive headers before storing
        headers: item.headers.filter(
          (h) =>
            h.key.toLowerCase() !== 'x-api-key' &&
            h.key.toLowerCase() !== 'authorization'
        ),
      };

      try {
        const current = playgroundService.historyStorage.getHistory();
        const updated = [newItem, ...current.filter((x) => x.id !== newItem.id)].slice(
          0,
          MAX_HISTORY_ITEMS
        );
        localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(updated));
      } catch {
        // storage disabled or quota exceeded
      }

      return newItem;
    },

    clearHistory: (): void => {
      try {
        localStorage.removeItem(HISTORY_STORAGE_KEY);
      } catch {
        // ignore
      }
    },
  },
};

function getFallbackStatusText(statusCode: number): string {
  const map: Record<number, string> = {
    200: 'OK',
    201: 'Created',
    202: 'Accepted',
    204: 'No Content',
    400: 'Bad Request',
    401: 'Unauthorized',
    403: 'Forbidden',
    404: 'Not Found',
    409: 'Conflict',
    422: 'Unprocessable Entity',
    429: 'Too Many Requests',
    500: 'Internal Server Error',
    502: 'Bad Gateway',
    503: 'Service Unavailable',
    504: 'Gateway Timeout',
  };
  return map[statusCode] || (statusCode >= 200 && statusCode < 300 ? 'Success' : 'Error');
}
