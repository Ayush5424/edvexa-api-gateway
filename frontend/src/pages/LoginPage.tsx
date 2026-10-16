import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Lock, User, AlertCircle, Key, CheckCircle2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';
import { authService } from '../services/authService';
import { AuthStatusResponse } from '../types/auth';

export const LoginPage: React.FC = () => {
  const [mode, setMode] = useState<'login' | 'bootstrap'>('login');
  const [authStatus, setAuthStatus] = useState<AuthStatusResponse | null>(null);
  const [checkingStatus, setCheckingStatus] = useState<boolean>(true);

  // Form fields
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [bootstrapSecret, setBootstrapSecret] = useState('');

  // UI state
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    let isMounted = true;
    const fetchStatus = async () => {
      try {
        const status = await authService.getStatus();
        if (isMounted) {
          setAuthStatus(status);
          if (!status.initialized && status.bootstrapAllowed) {
            setMode('bootstrap');
          }
        }
      } catch {
        // Fallback to default login mode if status check is unavailable
      } finally {
        if (isMounted) {
          setCheckingStatus(false);
        }
      }
    };

    fetchStatus();
    return () => {
      isMounted = false;
    };
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);

    if (!username.trim() || !password.trim()) {
      setError('Please provide both username and password.');
      return;
    }

    setSubmitting(true);
    try {
      await login({ username: username.trim(), password });
      navigate('/');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Login failed. Please verify credentials or check backend availability.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleBootstrap = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);

    if (!username.trim() || !password.trim()) {
      setError('Please provide username and password.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Password and confirmation do not match.');
      return;
    }

    if (authStatus?.bootstrapConfigured && !bootstrapSecret.trim()) {
      setError('Bootstrap secret is required by server configuration.');
      return;
    }

    setSubmitting(true);
    try {
      await authService.bootstrap({
        bootstrapSecret: bootstrapSecret.trim(),
        username: username.trim(),
        password,
      });

      setSuccessMessage('Administrator account provisioned successfully. Signing in...');
      // Automatically log in with the new credentials
      await login({ username: username.trim(), password });
      navigate('/');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Administrator initialization failed. Please check server logs.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#0f172a',
        padding: '1.5rem 1rem',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '460px',
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          padding: '2.5rem 2rem',
          boxShadow: '0 20px 25px -5px rgb(0 0 0 / 0.2), 0 8px 10px -6px rgb(0 0 0 / 0.2)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: '1.75rem' }}>
          <div
            style={{
              width: '3.5rem',
              height: '3.5rem',
              backgroundColor: mode === 'bootstrap' ? '#ecfdf5' : '#eff6ff',
              color: mode === 'bootstrap' ? '#059669' : '#2563eb',
              borderRadius: '0.75rem',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '1rem',
            }}
          >
            {mode === 'bootstrap' ? <Key size={32} /> : <ShieldCheck size={32} />}
          </div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>Edvexa Gateway</h1>
          <p style={{ fontSize: '0.875rem', color: '#64748b', marginTop: '0.25rem' }}>
            {mode === 'bootstrap'
              ? 'First-Time Administrator Setup'
              : 'Administrative Observability & Resilience Portal'}
          </p>
        </div>

        {error && (
          <div
            style={{
              backgroundColor: '#fef2f2',
              border: '1px solid #fecaca',
              color: '#991b1b',
              padding: '0.75rem 1rem',
              borderRadius: '0.5rem',
              fontSize: '0.8125rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '1.5rem',
            }}
          >
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        {successMessage && (
          <div
            style={{
              backgroundColor: '#f0fdf4',
              border: '1px solid #bbf7d0',
              color: '#166534',
              padding: '0.75rem 1rem',
              borderRadius: '0.5rem',
              fontSize: '0.8125rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '1.5rem',
            }}
          >
            <CheckCircle2 size={16} />
            <span>{successMessage}</span>
          </div>
        )}

        {mode === 'bootstrap' ? (
          <form onSubmit={handleBootstrap}>
            <div
              style={{
                backgroundColor: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '0.5rem',
                padding: '0.75rem 1rem',
                fontSize: '0.75rem',
                color: '#475569',
                marginBottom: '1.25rem',
              }}
            >
              No administrator account is currently provisioned. Configure your initial credentials to secure the gateway.
            </div>

            {authStatus?.bootstrapConfigured && (
              <div className="form-group">
                <label className="form-label" htmlFor="bootstrapSecret">
                  Bootstrap Secret Token
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    id="bootstrapSecret"
                    type="password"
                    className="form-input"
                    style={{ width: '100%', paddingLeft: '2.25rem' }}
                    value={bootstrapSecret}
                    onChange={(e) => setBootstrapSecret(e.target.value)}
                    placeholder="Enter bootstrap token"
                    required
                  />
                  <Key
                    size={16}
                    color="#94a3b8"
                    style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                  />
                </div>
              </div>
            )}

            <div className="form-group">
              <label className="form-label" htmlFor="bootstrapUsername">
                Administrator Username
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="bootstrapUsername"
                  type="text"
                  className="form-input"
                  style={{ width: '100%', paddingLeft: '2.25rem' }}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter username"
                  autoComplete="username"
                  required
                />
                <User
                  size={16}
                  color="#94a3b8"
                  style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="bootstrapPassword">
                Administrator Password (min. 8 characters)
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="bootstrapPassword"
                  type="password"
                  className="form-input"
                  style={{ width: '100%', paddingLeft: '2.25rem' }}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Create secure password"
                  autoComplete="new-password"
                  required
                />
                <Lock
                  size={16}
                  color="#94a3b8"
                  style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="form-label" htmlFor="confirmPassword">
                Confirm Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="confirmPassword"
                  type="password"
                  className="form-input"
                  style={{ width: '100%', paddingLeft: '2.25rem' }}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat password"
                  autoComplete="new-password"
                  required
                />
                <Lock
                  size={16}
                  color="#94a3b8"
                  style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.625rem', backgroundColor: '#059669', borderColor: '#059669' }}
              disabled={submitting || checkingStatus}
            >
              {submitting ? 'Initializing Account...' : 'Initialize & Sign In'}
            </button>

            {authStatus?.initialized && (
              <div style={{ textAlign: 'center', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setMode('login')}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#2563eb',
                    fontSize: '0.8125rem',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  Return to standard Sign In
                </button>
              </div>
            )}
          </form>
        ) : (
          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label className="form-label" htmlFor="username">
                Username
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="username"
                  type="text"
                  className="form-input"
                  style={{ width: '100%', paddingLeft: '2.25rem' }}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Administrator username"
                  autoComplete="username"
                  required
                />
                <User
                  size={16}
                  color="#94a3b8"
                  style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="form-label" htmlFor="password">
                Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="password"
                  type="password"
                  className="form-input"
                  style={{ width: '100%', paddingLeft: '2.25rem' }}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter password"
                  autoComplete="current-password"
                  required
                />
                <Lock
                  size={16}
                  color="#94a3b8"
                  style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.625rem' }}
              disabled={submitting}
            >
              {submitting ? 'Authenticating...' : 'Sign In to Dashboard'}
            </button>

            {authStatus?.bootstrapAllowed && (
              <div style={{ textAlign: 'center', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setMode('bootstrap')}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#059669',
                    fontSize: '0.8125rem',
                    cursor: 'pointer',
                    fontWeight: 600,
                  }}
                >
                  First time? Initialize administrator account
                </button>
              </div>
            )}
          </form>
        )}

        <div style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: '0.75rem', color: '#94a3b8' }}>
          Role-Based Access Control enforced with signed JWT tokens
        </div>
      </div>
    </div>
  );
};
