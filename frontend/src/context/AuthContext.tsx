import React, { createContext, useContext, useEffect, useState } from 'react';
import { authService } from '../services/authService';
import { tokenStorage } from '../services/apiClient';
import { LoginRequest } from '../types/auth';

interface AuthContextType {
  isAuthenticated: boolean;
  user: { username: string; role: string } | null;
  loading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => authService.isAuthenticated());
  const [user, setUser] = useState<{ username: string; role: string } | null>(() => tokenStorage.getUser());
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    const handleUnauthorized = () => {
      setIsAuthenticated(false);
      setUser(null);
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, []);

  const login = async (credentials: LoginRequest): Promise<void> => {
    setLoading(true);
    try {
      const response = await authService.login(credentials);
      setIsAuthenticated(true);
      setUser({
        username: response.username,
        role: response.role,
      });
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    authService.logout();
    setIsAuthenticated(false);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
