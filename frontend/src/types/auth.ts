export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInMs: number;
  username: string;
  role: string;
}

export interface UserProfile {
  username: string;
  roles: string[];
}

export interface AuthStatusResponse {
  initialized: boolean;
  bootstrapAllowed: boolean;
  bootstrapConfigured?: boolean;
}

export interface AdminBootstrapRequest {
  bootstrapSecret: string;
  username: string;
  password: string;
}

export interface AdminBootstrapResponse {
  message: string;
  username: string;
  role: string;
}
