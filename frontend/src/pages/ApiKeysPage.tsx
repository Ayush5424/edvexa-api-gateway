import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Key, Copy, Check, AlertTriangle, ShieldCheck } from 'lucide-react';
import { Header } from '../components/layout/Header';
import { StatusBadge } from '../components/common/StatusBadge';
import { Modal } from '../components/common/Modal';
import { Alert } from '../components/common/Alert';
import { apiKeyService } from '../services/apiKeyService';
import { ApiKeyResponse, ApiKeyCreatedResponse, CreateApiKeyRequest } from '../types/apiKeys';
import { ApiError } from '../services/apiClient';

export const ApiKeysPage: React.FC = () => {
  const [apiKeys, setApiKeys] = useState<ApiKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Create key modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [formData, setFormData] = useState<CreateApiKeyRequest>({
    clientName: '',
    rateLimitPerMinute: 60,
  });

  // One-time key reveal dialog
  const [createdKeyData, setCreatedKeyData] = useState<ApiKeyCreatedResponse | null>(null);
  const [copied, setCopied] = useState(false);

  // Revoke confirmation modal
  const [isRevokeOpen, setIsRevokeOpen] = useState(false);
  const [selectedKey, setSelectedKey] = useState<ApiKeyResponse | null>(null);

  const fetchKeys = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    try {
      const data = await apiKeyService.listApiKeys();
      setApiKeys(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch API keys';
      setAlert({ type: 'error', message: msg });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchKeys();
  }, [fetchKeys]);

  const handleCreateKey = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.clientName.trim()) {
      setAlert({ type: 'error', message: 'Client name is required.' });
      return;
    }

    try {
      const created = await apiKeyService.createApiKey({
        clientName: formData.clientName.trim(),
        rateLimitPerMinute: Number(formData.rateLimitPerMinute) || 60,
      });

      // Show one-time reveal dialog
      setCreatedKeyData(created);
      setIsCreateOpen(false);
      setFormData({ clientName: '', rateLimitPerMinute: 60 });
      fetchKeys();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to generate API key';
      setAlert({ type: 'error', message: msg });
    }
  };

  const handleCopyKey = () => {
    if (createdKeyData?.apiKey) {
      navigator.clipboard.writeText(createdKeyData.apiKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleRevokeKey = async () => {
    if (!selectedKey) return;
    try {
      await apiKeyService.revokeApiKey(selectedKey.id);
      setAlert({
        type: 'success',
        message: `API key for '${selectedKey.clientName}' has been revoked.`,
      });
      setIsRevokeOpen(false);
      setSelectedKey(null);
      fetchKeys();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to revoke API key';
      setAlert({ type: 'error', message: msg });
    }
  };

  const openRevokeModal = (key: ApiKeyResponse) => {
    setSelectedKey(key);
    setIsRevokeOpen(true);
  };

  return (
    <>
      <Header
        title="Client API Keys"
        subtitle="Cryptographic client credentials with atomic Redis-based rate limiting"
        onRefresh={() => fetchKeys(true)}
        isRefreshing={refreshing}
      />

      <div className="page-container">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600 }}>API Client Credentials</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Clients authenticate to the gateway using the <code>X-API-Key</code> request header.
            </p>
          </div>
          <button onClick={() => setIsCreateOpen(true)} className="btn btn-primary">
            <Plus size={16} />
            <span>Generate New API Key</span>
          </button>
        </div>

        <div className="card" style={{ padding: 0 }}>
          <div className="table-container" style={{ border: 'none' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Client Name</th>
                  <th>Key Identifier / Prefix</th>
                  <th>Rate Limit</th>
                  <th>Status</th>
                  <th>Created At</th>
                  <th>Revoked At</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {apiKeys.length === 0 ? (
                  <tr>
                    <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      {loading ? 'Loading API keys...' : 'No API keys issued yet. Click "Generate New API Key" to issue credentials.'}
                    </td>
                  </tr>
                ) : (
                  apiKeys.map((key) => (
                    <tr key={key.id}>
                      <td style={{ fontWeight: 600 }}>{key.clientName}</td>
                      <td>
                        <span className="code-badge">{key.keyPrefix}...</span>
                      </td>
                      <td>
                        <strong>{key.rateLimitPerMinute}</strong> req/min
                      </td>
                      <td>
                        <StatusBadge status={key.status} />
                      </td>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                        {new Date(key.createdAt).toLocaleString()}
                      </td>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                        {key.revokedAt ? new Date(key.revokedAt).toLocaleString() : 'N/A'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {key.status === 'ACTIVE' ? (
                          <button
                            onClick={() => openRevokeModal(key)}
                            className="btn btn-secondary btn-sm"
                            style={{ color: 'var(--color-danger)' }}
                            title="Revoke API key"
                          >
                            Revoke
                          </button>
                        ) : (
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Revoked</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Generate API Key Modal */}
      <Modal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} title="Generate Client API Key">
        <form onSubmit={handleCreateKey}>
          <div className="form-group">
            <label className="form-label" htmlFor="client-app-name">Client / Application Name</label>
            <input
              id="client-app-name"
              type="text"
              className="form-input"
              value={formData.clientName}
              onChange={(e) => setFormData({ ...formData, clientName: e.target.value })}
              placeholder="e.g. mobile-app, billing-service"
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="rate-limit-per-min">Rate Limit (Requests per minute)</label>
            <input
              id="rate-limit-per-min"
              type="number"
              min={1}
              max={100000}
              className="form-input"
              value={formData.rateLimitPerMinute}
              onChange={(e) => setFormData({ ...formData, rateLimitPerMinute: Number(e.target.value) })}
              required
            />
            <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Enforced atomically via sliding window Redis Lua scripts.
            </small>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={() => setIsCreateOpen(false)} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Generate Key
            </button>
          </div>
        </form>
      </Modal>

      {/* Explicit One-Time Raw Key Reveal Modal */}
      <Modal
        isOpen={Boolean(createdKeyData)}
        onClose={() => setCreatedKeyData(null)}
        title="API Key Generated (One-Time Reveal)"
      >
        <div>
          <div
            style={{
              backgroundColor: '#fffbeb',
              border: '1px solid #fde68a',
              color: '#92400e',
              padding: '0.75rem 1rem',
              borderRadius: '0.5rem',
              fontSize: '0.8125rem',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.5rem',
              marginBottom: '1rem',
            }}
          >
            <AlertTriangle size={18} style={{ flexShrink: 0, marginTop: '0.125rem' }} />
            <div>
              <strong>Copy this API key now.</strong> For your security, this key is hashed in the database and will{' '}
              <strong>never be displayed again</strong>.
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Client Name</label>
            <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>{createdKeyData?.clientName}</div>
          </div>

          <div className="form-group">
            <label className="form-label">Raw Secret API Key</label>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input
                type="text"
                readOnly
                value={createdKeyData?.apiKey || ''}
                className="form-input"
                style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.8125rem',
                  backgroundColor: '#f8fafc',
                  flex: 1,
                  fontWeight: 600,
                }}
              />
              <button
                type="button"
                onClick={handleCopyKey}
                className="btn btn-primary"
                style={{ padding: '0.5rem 0.875rem' }}
              >
                {copied ? <Check size={16} /> : <Copy size={16} />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
          </div>

          <div className="modal-footer">
            <button
              type="button"
              onClick={() => setCreatedKeyData(null)}
              className="btn btn-primary"
              style={{ width: '100%' }}
            >
              I Have Securely Copied This Key
            </button>
          </div>
        </div>
      </Modal>

      {/* Revocation Confirmation Modal */}
      <Modal isOpen={isRevokeOpen} onClose={() => setIsRevokeOpen(false)} title="Confirm Key Revocation">
        <div>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-main)', marginBottom: '1rem' }}>
            Are you sure you want to revoke the API key for client{' '}
            <strong>{selectedKey?.clientName}</strong> (Prefix: <code>{selectedKey?.keyPrefix}</code>)?
          </p>
          <p style={{ fontSize: '0.8125rem', color: 'var(--color-danger)' }}>
            This action is irreversible. All subsequent inbound requests presenting this key will immediately be
            rejected with HTTP 401 Unauthorized.
          </p>
          <div className="modal-footer">
            <button type="button" onClick={() => setIsRevokeOpen(false)} className="btn btn-secondary">
              Cancel
            </button>
            <button type="button" onClick={handleRevokeKey} className="btn btn-danger">
              Revoke API Key
            </button>
          </div>
        </div>
      </Modal>
    </>
  );
};
