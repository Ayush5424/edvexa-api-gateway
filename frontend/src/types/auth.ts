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
