import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import { authService } from '../services/authService';

vi.mock('../services/authService', () => ({
  authService: {
    login: vi.fn(),
    logout: vi.fn(),
    getStatus: vi.fn().mockResolvedValue({ initialized: true, bootstrapAllowed: false, bootstrapConfigured: false }),
    bootstrap: vi.fn(),
    getCurrentUser: vi.fn().mockReturnValue(null),
    isAuthenticated: vi.fn().mockReturnValue(false)
  }
}));

describe('LoginPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form with username and password fields', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </BrowserRouter>
    );

    expect(screen.getByText('Edvexa Gateway')).toBeInTheDocument();
    expect(await screen.findByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in to dashboard/i })).toBeInTheDocument();
  });

  it('displays error message on failed authentication', async () => {
    vi.mocked(authService.login).mockRejectedValueOnce(new Error('Invalid username or password'));

    render(
      <BrowserRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </BrowserRouter>
    );

    await screen.findByLabelText('Username');
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in to dashboard/i }));

    await waitFor(() => {
      expect(screen.getByText('Login failed. Please verify credentials or check backend availability.')).toBeInTheDocument();
    });
  });

  it('calls authService.login and navigates on success', async () => {
    vi.mocked(authService.login).mockResolvedValueOnce({
      token: 'mock-jwt-token',
      tokenType: 'Bearer',
      expiresInMs: 3600000,
      username: 'admin',
      role: 'ROLE_ADMIN'
    });

    render(
      <BrowserRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </BrowserRouter>
    );

    await screen.findByLabelText('Username');
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct-password' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in to dashboard/i }));

    await waitFor(() => {
      expect(authService.login).toHaveBeenCalledWith({ username: 'admin', password: 'correct-password' });
    });
  });

  it('renders first-time administrator setup mode when uninitialized', async () => {
    vi.mocked(authService.getStatus).mockResolvedValueOnce({
      initialized: false,
      bootstrapAllowed: true,
      bootstrapConfigured: true
    });

    render(
      <BrowserRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </BrowserRouter>
    );

    expect(await screen.findByText(/No administrator account is currently provisioned/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Bootstrap Secret Token/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Administrator Username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Administrator Password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Initialize & Sign In/i })).toBeInTheDocument();
  });

  it('executes bootstrap and logs in during first-time setup', async () => {
    vi.mocked(authService.getStatus).mockResolvedValueOnce({
      initialized: false,
      bootstrapAllowed: true,
      bootstrapConfigured: false
    });
    vi.mocked(authService.bootstrap).mockResolvedValueOnce({
      username: 'newadmin',
      status: 'ADMIN_PROVISIONED',
      message: 'Provisioned'
    });
    vi.mocked(authService.login).mockResolvedValueOnce({
      token: 'mock-jwt-token',
      tokenType: 'Bearer',
      expiresInMs: 3600000,
      username: 'newadmin',
      role: 'ROLE_ADMIN'
    });

    render(
      <BrowserRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </BrowserRouter>
    );

    expect(await screen.findByRole('button', { name: /Initialize & Sign In/i })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Administrator Username/i), { target: { value: 'newadmin' } });
    fireEvent.change(screen.getByLabelText(/^Administrator Password/i), { target: { value: 'Password123!' } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/i), { target: { value: 'Password123!' } });

    fireEvent.click(screen.getByRole('button', { name: /Initialize & Sign In/i }));

    await waitFor(() => {
      expect(authService.bootstrap).toHaveBeenCalledWith({
        bootstrapSecret: '',
        username: 'newadmin',
        password: 'Password123!'
      });
      expect(authService.login).toHaveBeenCalledWith({
        username: 'newadmin',
        password: 'Password123!'
      });
    });
  });
});
