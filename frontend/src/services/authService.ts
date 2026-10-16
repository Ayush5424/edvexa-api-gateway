import { apiRequest, tokenStorage } from './apiClient';
import {
  AdminBootstrapRequest,
  AdminBootstrapResponse,
  AuthStatusResponse,
  LoginRequest,
  LoginResponse,
  UserProfile,
} from '../types/auth';

export const authService = {
  getStatus: async (): Promise<AuthStatusResponse> => {
    return apiRequest<AuthStatusResponse>('/api/v1/auth/status', {
      method: 'GET',
      requiresAuth: false,
    });
  },

  bootstrap: async (payload: AdminBootstrapRequest): Promise<AdminBootstrapResponse> => {
    return apiRequest<AdminBootstrapResponse>('/api/v1/auth/bootstrap', {
      method: 'POST',
      body: JSON.stringify(payload),
      requiresAuth: false,
    });
  },

  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
      requiresAuth: false,
    });

    if (response && response.token) {
      tokenStorage.setToken(response.token);
      tokenStorage.setUser({
        username: response.username,
        role: response.role,
      });
    }

    return response;
  },

  getCurrentUser: async (): Promise<UserProfile> => {
    return apiRequest<UserProfile>('/api/v1/auth/me', {
      method: 'GET',
    });
  },

  logout: (): void => {
    tokenStorage.clearToken();
  },

  isAuthenticated: (): boolean => {
    return Boolean(tokenStorage.getToken());
  },

  getUser: () => {
    return tokenStorage.getUser();
  },
};
