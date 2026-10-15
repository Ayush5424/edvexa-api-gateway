export class ApiError extends Error {
  statusCode: number;
  data: unknown;
  retryAfterSeconds?: number;

  constructor(message: string, statusCode: number, data?: unknown, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.data = data;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

const TOKEN_KEY = 'edvexa_auth_token';
const USER_KEY = 'edvexa_auth_user';

export const tokenStorage = {
  getToken: (): string | null => {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  setToken: (token: string): void => {
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // storage unavailable
    }
  },
  clearToken: (): void => {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
    } catch {
      // storage unavailable
    }
  },
  getUser: (): { username: string; role: string } | null => {
    try {
      const data = sessionStorage.getItem(USER_KEY);
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  },
  setUser: (user: { username: string; role: string }): void => {
    try {
      sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    } catch {
      // storage unavailable
    }
  },
};

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

interface RequestOptions extends RequestInit {
  requiresAuth?: boolean;
}

export async function apiRequest<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { requiresAuth = true, headers: customHeaders, ...rest } = options;

  const headers = new Headers(customHeaders || {});
  if (!headers.has('Content-Type') && !(rest.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (requiresAuth) {
    const token = tokenStorage.getToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  const url = `${BASE_URL}${endpoint}`;

  let response: Response;
  try {
    response = await fetch(url, { ...rest, headers });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Network connection failed';
    throw new ApiError(`Network error: ${msg}. Please ensure backend is reachable.`, 0);
  }

  // Handle 401 Unauthorized
  if (response.status === 401) {
    tokenStorage.clearToken();
    window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    throw new ApiError('Authentication session expired or invalid credentials.', 401);
  }

  // Handle 403 Forbidden
  if (response.status === 403) {
    throw new ApiError('Access denied: administrative privileges required for this action.', 403);
  }

  // Handle 429 Too Many Requests
  if (response.status === 429) {
    const retryAfter = response.headers.get('Retry-After');
    const retrySeconds = retryAfter ? parseInt(retryAfter, 10) : 60;
    throw new ApiError(
      `Rate limit exceeded. Try again in ${retrySeconds} seconds.`,
      429,
      undefined,
      retrySeconds
    );
  }

  // Handle No Content
  if (response.status === 204) {
    return {} as T;
  }

  let data: unknown;
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    let sanitizedMessage = 'An error occurred while processing the request.';
    if (data && typeof data === 'object' && 'message' in data && typeof (data as { message: unknown }).message === 'string') {
      sanitizedMessage = (data as { message: string }).message;
    } else if (typeof data === 'string' && data.length > 0 && data.length < 200) {
      sanitizedMessage = data;
    }
    throw new ApiError(sanitizedMessage, response.status, data);
  }

  return data as T;
}
