import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Lock, User, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/apiClient';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

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

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#0f172a',
        padding: '1rem',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '420px',
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          padding: '2.5rem 2rem',
          boxShadow: '0 20px 25px -5px rgb(0 0 0 / 0.2), 0 8px 10px -6px rgb(0 0 0 / 0.2)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div
            style={{
              width: '3.5rem',
              height: '3.5rem',
              backgroundColor: '#eff6ff',
              color: '#2563eb',
              borderRadius: '0.75rem',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '1rem',
            }}
          >
            <ShieldCheck size={32} />
          </div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>Edvexa Gateway</h1>
          <p style={{ fontSize: '0.875rem', color: '#64748b', marginTop: '0.25rem' }}>
            Administrative Observability & Resilience Portal
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

        <form onSubmit={handleSubmit}>
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
        </form>

        <div style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: '0.75rem', color: '#94a3b8' }}>
          Role-Based Access Control enforced with signed JWT tokens
        </div>
      </div>
    </div>
  );
};
