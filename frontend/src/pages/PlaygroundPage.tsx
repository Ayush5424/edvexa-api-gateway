import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Send,
  RotateCcw,
  Copy,
  Check,
  Plus,
  Trash2,
  ExternalLink,
  Clock,
  Key,
  Shield,
  Layers,
  FileCode,
  ListFilter,
  Eye,
  EyeOff,
  History,
  AlertCircle,
  Database,
} from 'lucide-react';
import { Header } from '../components/layout/Header';
import { Alert } from '../components/common/Alert';
import { StatusBadge } from '../components/common/StatusBadge';
import { gatewayService } from '../services/gatewayService';
import { apiKeyService } from '../services/apiKeyService';
import { playgroundService } from '../services/playgroundService';
import { ServiceResponse } from '../types/services';
import { ApiKeyResponse } from '../types/apiKeys';
import {
  HttpMethod,
  KeyValuePair,
  PlaygroundHistoryItem,
  PlaygroundResponse,
} from '../types/playground';

export const PlaygroundPage: React.FC = () => {
  const navigate = useNavigate();

  // Reference data
  const [services, setServices] = useState<ServiceResponse[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKeyResponse[]>([]);
  const [loadingServices, setLoadingServices] = useState(true);

  // Request Builder State
  const [method, setMethod] = useState<HttpMethod>('GET');
  const [selectedServiceId, setSelectedServiceId] = useState<string>('');
  const [endpointPath, setEndpointPath] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'params' | 'headers' | 'body' | 'auth'>('params');

  // Key-value editors
  const [queryParams, setQueryParams] = useState<KeyValuePair[]>([]);
  const [customHeaders, setCustomHeaders] = useState<KeyValuePair[]>([
    { id: '1', key: 'Accept', value: 'application/json', enabled: true },
  ]);
  const [requestBody, setRequestBody] = useState<string>('{\n  \n}');
  const [jsonError, setJsonError] = useState<string | null>(null);

  // Authentication State
  const [authType, setAuthType] = useState<'inventory' | 'manual' | 'none'>('inventory');
  const [selectedApiKeyId, setSelectedApiKeyId] = useState<string>('');
  const [rawApiKeyInput, setRawApiKeyInput] = useState<string>('');
  const [showApiKey, setShowApiKey] = useState<boolean>(false);

  // Execution & Response State
  const [isExecuting, setIsExecuting] = useState<boolean>(false);
  const [response, setResponse] = useState<PlaygroundResponse | null>(null);
  const [responseTab, setResponseTab] = useState<'body' | 'headers' | 'details'>('body');
  const [alert, setAlert] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  // Clipboard copy state
  const [copiedCurl, setCopiedCurl] = useState<boolean>(false);
  const [copiedBody, setCopiedBody] = useState<boolean>(false);

  // Recent Requests History
  const [history, setHistory] = useState<PlaygroundHistoryItem[]>([]);

  // Load registered services and active API keys on mount
  const loadInitialData = useCallback(async () => {
    setLoadingServices(true);
    try {
      const [servicesList, keysList] = await Promise.all([
        gatewayService.getAllServices(),
        apiKeyService.listApiKeys().catch(() => [] as ApiKeyResponse[]),
      ]);

      setServices(servicesList);
      const activeKeys = keysList.filter((k) => k.status === 'ACTIVE');
      setApiKeys(activeKeys);

      if (servicesList.length > 0) {
        setSelectedServiceId(servicesList[0].id);
      }
      if (activeKeys.length > 0) {
        setSelectedApiKeyId(activeKeys[0].id);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch services';
      setAlert({ type: 'error', message: msg });
    } finally {
      setLoadingServices(false);
    }
  }, []);

  useEffect(() => {
    loadInitialData();
    setHistory(playgroundService.historyStorage.getHistory());
  }, [loadInitialData]);

  // Selected service metadata
  const selectedService = useMemo(() => {
    return services.find((s) => s.id === selectedServiceId) || null;
  }, [services, selectedServiceId]);

  // Selected API Key metadata
  const selectedApiKey = useMemo(() => {
    return apiKeys.find((k) => k.id === selectedApiKeyId) || null;
  }, [apiKeys, selectedApiKeyId]);

  // Derived Gateway URL
  const { path: gatewayPath, fullGatewayPath } = useMemo(() => {
    const prefix = selectedService ? selectedService.pathPrefix : '';
    return playgroundService.buildGatewayUrl(prefix, endpointPath, queryParams);
  }, [selectedService, endpointPath, queryParams]);

  // Validate JSON on body change
  const handleBodyChange = (value: string) => {
    setRequestBody(value);
    if (!value.trim()) {
      setJsonError(null);
      return;
    }
    try {
      JSON.parse(value);
      setJsonError(null);
    } catch (e: unknown) {
      if (e instanceof Error) {
        setJsonError(e.message);
      } else {
        setJsonError('Invalid JSON format');
      }
    }
  };

  const handleFormatJson = () => {
    try {
      const parsed = JSON.parse(requestBody);
      setRequestBody(JSON.stringify(parsed, null, 2));
      setJsonError(null);
    } catch {
      setJsonError('Cannot format: malformed JSON');
    }
  };

  // Add / Remove Query Params
  const addQueryParam = () => {
    setQueryParams((prev) => [
      ...prev,
      { id: Date.now().toString(), key: '', value: '', enabled: true },
    ]);
  };

  const updateQueryParam = (id: string, field: 'key' | 'value' | 'enabled', val: string | boolean) => {
    setQueryParams((prev) =>
      prev.map((item) => (item.id === id ? { ...item, [field]: val } : item))
    );
  };

  const removeQueryParam = (id: string) => {
    setQueryParams((prev) => prev.filter((item) => item.id !== id));
  };

  // Add / Remove Custom Headers
  const addCustomHeader = () => {
    setCustomHeaders((prev) => [
      ...prev,
      { id: Date.now().toString(), key: '', value: '', enabled: true },
    ]);
  };

  const updateCustomHeader = (id: string, field: 'key' | 'value' | 'enabled', val: string | boolean) => {
    setCustomHeaders((prev) =>
      prev.map((item) => (item.id === id ? { ...item, [field]: val } : item))
    );
  };

  const removeCustomHeader = (id: string) => {
    setCustomHeaders((prev) => prev.filter((item) => item.id !== id));
  };

  // Resolve Effective API Key Secret (in-memory only)
  const effectiveApiKey = useMemo(() => {
    if (authType === 'none') return '';
    return rawApiKeyInput.trim();
  }, [authType, rawApiKeyInput]);

  // Execute Gateway Request
  const handleSendRequest = async () => {
    if (!selectedService) {
      setAlert({ type: 'error', message: 'Please select a registered downstream service.' });
      return;
    }

    if (method !== 'GET' && jsonError) {
      setAlert({ type: 'error', message: `Please fix JSON body error before sending: ${jsonError}` });
      return;
    }

    setIsExecuting(true);
    setAlert(null);
    setResponse(null);

    const headersMap: Record<string, string> = {};

    // Custom headers
    customHeaders
      .filter((h) => h.enabled && h.key.trim().length > 0)
      .forEach((h) => {
        headersMap[h.key.trim()] = h.value;
      });

    // Content-Type default for mutation methods
    if (method !== 'GET' && !headersMap['Content-Type']) {
      headersMap['Content-Type'] = 'application/json';
    }

    // Attach API Key if configured
    if (authType !== 'none' && effectiveApiKey) {
      headersMap['X-API-Key'] = effectiveApiKey;
    }

    try {
      const result = await playgroundService.executeGatewayRequest({
        url: fullGatewayPath,
        method,
        headers: headersMap,
        body: method !== 'GET' ? requestBody : undefined,
      });

      setResponse(result);

      // Save to non-sensitive history
      const savedHistory = playgroundService.historyStorage.saveHistoryItem({
        method,
        serviceId: selectedService.id,
        serviceName: selectedService.name,
        pathPrefix: selectedService.pathPrefix,
        endpointPath,
        queryParams: queryParams.filter((p) => p.enabled && p.key.trim().length > 0),
        headers: customHeaders.filter((h) => h.enabled && h.key.trim().length > 0),
        bodySnippet: method !== 'GET' && requestBody ? requestBody.slice(0, 150) : undefined,
        responseStatus: result.status,
        durationMs: result.durationMs,
        correlationId: result.correlationId,
      });

      setHistory((prev) => [savedHistory, ...prev.filter((x) => x.id !== savedHistory.id)].slice(0, 25));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Execution failed';
      setAlert({ type: 'error', message: msg });
    } finally {
      setIsExecuting(false);
    }
  };

  // Reset / Clear Form
  const handleClear = () => {
    setEndpointPath('');
    setQueryParams([]);
    setCustomHeaders([{ id: '1', key: 'Accept', value: 'application/json', enabled: true }]);
    setRequestBody('{\n  \n}');
    setJsonError(null);
    setRawApiKeyInput('');
    setResponse(null);
    setAlert({ type: 'info', message: 'Playground configuration reset.' });
  };

  // Copy as cURL
  const handleCopyCurl = () => {
    const origin = window.location.origin;
    const fullUrl = `${origin}${fullGatewayPath}`;
    const curlCommand = playgroundService.generateCurlCommand({
      method,
      fullUrl,
      headers: customHeaders,
      body: method !== 'GET' ? requestBody : undefined,
      hasApiKey: authType !== 'none',
    });

    navigator.clipboard.writeText(curlCommand);
    setCopiedCurl(true);
    setAlert({
      type: 'success',
      message: 'cURL command copied to clipboard with safe ${GATEWAY_API_KEY} placeholder.',
    });
    setTimeout(() => setCopiedCurl(false), 2500);
  };

  // Copy Response Body
  const handleCopyResponseBody = () => {
    if (!response) return;
    navigator.clipboard.writeText(response.body);
    setCopiedBody(true);
    setTimeout(() => setCopiedBody(false), 2000);
  };

  // Restore Request from History
  const handleRestoreHistory = (item: PlaygroundHistoryItem) => {
    setMethod(item.method);
    setSelectedServiceId(item.serviceId);
    setEndpointPath(item.endpointPath || '');
    setQueryParams(
      item.queryParams.map((p, idx) => ({
        id: `restored_q_${idx}`,
        key: p.key,
        value: p.value,
        enabled: true,
      }))
    );
    setCustomHeaders(
      item.headers.map((h, idx) => ({
        id: `restored_h_${idx}`,
        key: h.key,
        value: h.value,
        enabled: true,
      }))
    );
    if (item.bodySnippet) {
      setRequestBody(item.bodySnippet);
    }
    setAlert({
      type: 'info',
      message: `Restored ${item.method} configuration for ${item.serviceName}. Re-enter API key if authentication is required.`,
    });
  };

  // Clear History
  const handleClearHistory = () => {
    playgroundService.historyStorage.clearHistory();
    setHistory([]);
    setAlert({ type: 'info', message: 'Recent request history cleared.' });
  };

  // Navigate to Logs with Correlation ID
  const handleTraceInLogs = (corrId: string) => {
    navigate(`/logs?correlationId=${encodeURIComponent(corrId)}`);
  };

  return (
    <>
      <Header
        title="API Playground"
        subtitle="Interactively test, validate, and debug HTTP requests through the live API Gateway"
      />

      <div className="page-container">
        {alert && (
          <Alert
            type={alert.type === 'info' ? 'success' : alert.type}
            message={alert.message}
            onClose={() => setAlert(null)}
          />
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 1fr)', gap: '1.5rem' }}>
          {/* LEFT: Request Builder */}
          <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div className="card-header" style={{ marginBottom: '0.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Send size={18} color="#2563eb" />
                <h2 className="card-title" style={{ margin: 0 }}>Request Builder</h2>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  type="button"
                  onClick={handleCopyCurl}
                  className="btn btn-secondary btn-sm"
                  title="Copy as cURL command"
                >
                  {copiedCurl ? <Check size={14} color="#10b981" /> : <Copy size={14} />}
                  <span>Copy cURL</span>
                </button>
                <button
                  type="button"
                  onClick={handleClear}
                  className="btn btn-secondary btn-sm"
                  title="Reset form"
                >
                  <RotateCcw size={14} />
                  <span>Clear</span>
                </button>
              </div>
            </div>

            {/* Target URL Bar */}
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'stretch' }}>
              {/* Method Selector */}
              <select
                className="form-input"
                style={{
                  width: '110px',
                  fontWeight: 700,
                  color:
                    method === 'GET'
                      ? '#2563eb'
                      : method === 'POST'
                      ? '#059669'
                      : method === 'PUT'
                      ? '#d97706'
                      : method === 'DELETE'
                      ? '#dc2626'
                      : '#7c3aed',
                  backgroundColor: '#f8fafc',
                }}
                value={method}
                onChange={(e) => setMethod(e.target.value as HttpMethod)}
              >
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="PATCH">PATCH</option>
                <option value="DELETE">DELETE</option>
              </select>

              {/* Service Selector */}
              <select
                className="form-input"
                style={{ width: '220px' }}
                value={selectedServiceId}
                onChange={(e) => setSelectedServiceId(e.target.value)}
                disabled={loadingServices || services.length === 0}
              >
                {loadingServices ? (
                  <option value="">Loading services...</option>
                ) : services.length === 0 ? (
                  <option value="">No services registered</option>
                ) : (
                  services.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.pathPrefix})
                    </option>
                  ))
                )}
              </select>

              {/* Subpath Input */}
              <input
                type="text"
                className="form-input"
                style={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}
                placeholder="/subpath (optional)"
                value={endpointPath}
                onChange={(e) => setEndpointPath(e.target.value)}
              />

              {/* Send Button */}
              <button
                type="button"
                onClick={handleSendRequest}
                disabled={isExecuting || !selectedService}
                className="btn btn-primary"
                style={{ minWidth: '130px', fontWeight: 600 }}
              >
                {isExecuting ? (
                  <>
                    <span className="spinner" style={{ width: '14px', height: '14px' }} />
                    <span>Sending...</span>
                  </>
                ) : (
                  <>
                    <Send size={15} />
                    <span>Send</span>
                  </>
                )}
              </button>
            </div>

            {/* Gateway Route Preview Pill */}
            <div
              style={{
                padding: '0.5rem 0.75rem',
                backgroundColor: '#f1f5f9',
                borderRadius: 'var(--radius-md)',
                fontSize: '0.75rem',
                fontFamily: 'var(--font-mono)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontWeight: 700, color: '#475569' }}>GATEWAY TARGET:</span>
                <span style={{ color: '#0f172a' }}>{gatewayPath}</span>
              </div>
              {selectedService && (
                <span style={{ color: '#64748b' }}>
                  Target Base: {selectedService.baseUrl}
                </span>
              )}
            </div>

            {/* Request Builder Tabs */}
            <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', gap: '0.5rem' }}>
              <button
                type="button"
                className={`btn btn-sm ${activeTab === 'params' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('params')}
              >
                <ListFilter size={13} />
                <span>Params {queryParams.length > 0 && `(${queryParams.length})`}</span>
              </button>
              <button
                type="button"
                className={`btn btn-sm ${activeTab === 'headers' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('headers')}
              >
                <Layers size={13} />
                <span>Headers {customHeaders.length > 0 && `(${customHeaders.length})`}</span>
              </button>
              <button
                type="button"
                className={`btn btn-sm ${activeTab === 'body' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('body')}
              >
                <FileCode size={13} />
                <span>Body {jsonError && <span style={{ color: '#ef4444' }}>●</span>}</span>
              </button>
              <button
                type="button"
                className={`btn btn-sm ${activeTab === 'auth' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('auth')}
              >
                <Key size={13} />
                <span>Authentication {authType !== 'none' && <span style={{ color: '#10b981' }}>●</span>}</span>
              </button>
            </div>

            {/* Tab 1: Query Params */}
            {activeTab === 'params' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.8125rem', color: '#64748b', fontWeight: 500 }}>
                    Query parameters automatically append to the URL path
                  </span>
                  <button type="button" onClick={addQueryParam} className="btn btn-secondary btn-sm">
                    <Plus size={13} /> Add Parameter
                  </button>
                </div>

                {queryParams.length === 0 ? (
                  <div style={{ padding: '1.5rem', textAlign: 'center', color: '#94a3b8', fontSize: '0.8125rem' }}>
                    No query parameters configured. Click &quot;Add Parameter&quot; to add key/value pairs.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem' }}>
                    {queryParams.map((param) => (
                      <div key={param.id} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        <input
                          type="checkbox"
                          checked={param.enabled}
                          onChange={(e) => updateQueryParam(param.id, 'enabled', e.target.checked)}
                          title="Enable/disable parameter"
                        />
                        <input
                          type="text"
                          className="form-input"
                          style={{ flex: 1, padding: '0.375rem 0.5rem', fontSize: '0.8125rem' }}
                          placeholder="Key"
                          value={param.key}
                          onChange={(e) => updateQueryParam(param.id, 'key', e.target.value)}
                        />
                        <input
                          type="text"
                          className="form-input"
                          style={{ flex: 1, padding: '0.375rem 0.5rem', fontSize: '0.8125rem' }}
                          placeholder="Value"
                          value={param.value}
                          onChange={(e) => updateQueryParam(param.id, 'value', e.target.value)}
                        />
                        <button
                          type="button"
                          onClick={() => removeQueryParam(param.id)}
                          className="btn btn-secondary btn-sm"
                          style={{ color: '#ef4444', padding: '0.375rem 0.5rem' }}
                          title="Remove row"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Tab 2: Custom Headers */}
            {activeTab === 'headers' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.8125rem', color: '#64748b', fontWeight: 500 }}>
                    Custom HTTP request headers forwarded through the gateway
                  </span>
                  <button type="button" onClick={addCustomHeader} className="btn btn-secondary btn-sm">
                    <Plus size={13} /> Add Header
                  </button>
                </div>

                {customHeaders.length === 0 ? (
                  <div style={{ padding: '1.5rem', textAlign: 'center', color: '#94a3b8', fontSize: '0.8125rem' }}>
                    No custom headers configured. Click &quot;Add Header&quot; to add.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem' }}>
                    {customHeaders.map((header) => (
                      <div key={header.id} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        <input
                          type="checkbox"
                          checked={header.enabled}
                          onChange={(e) => updateCustomHeader(header.id, 'enabled', e.target.checked)}
                          title="Enable/disable header"
                        />
                        <input
                          type="text"
                          className="form-input"
                          style={{ flex: 1, padding: '0.375rem 0.5rem', fontSize: '0.8125rem' }}
                          placeholder="Header Name (e.g. Idempotency-Key)"
                          value={header.key}
                          onChange={(e) => updateCustomHeader(header.id, 'key', e.target.value)}
                        />
                        <input
                          type="text"
                          className="form-input"
                          style={{ flex: 1, padding: '0.375rem 0.5rem', fontSize: '0.8125rem' }}
                          placeholder="Header Value"
                          value={header.value}
                          onChange={(e) => updateCustomHeader(header.id, 'value', e.target.value)}
                        />
                        <button
                          type="button"
                          onClick={() => removeCustomHeader(header.id)}
                          className="btn btn-secondary btn-sm"
                          style={{ color: '#ef4444', padding: '0.375rem 0.5rem' }}
                          title="Remove header"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Tab 3: Request Body */}
            {activeTab === 'body' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.8125rem', color: '#64748b' }}>
                    JSON payload for {method} requests
                  </span>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                      type="button"
                      onClick={handleFormatJson}
                      className="btn btn-secondary btn-sm"
                      title="Prettify and format JSON"
                    >
                      Format JSON
                    </button>
                    <button
                      type="button"
                      onClick={() => handleBodyChange('')}
                      className="btn btn-secondary btn-sm"
                      title="Clear body"
                    >
                      Clear
                    </button>
                  </div>
                </div>

                <textarea
                  className="form-input"
                  rows={8}
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: '0.8125rem',
                    lineHeight: 1.4,
                    borderColor: jsonError ? '#ef4444' : undefined,
                  }}
                  value={requestBody}
                  onChange={(e) => handleBodyChange(e.target.value)}
                  placeholder="{}"
                />

                {jsonError && (
                  <div style={{ color: '#ef4444', fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                    <AlertCircle size={14} />
                    <span>{jsonError}</span>
                  </div>
                )}
              </div>
            )}

            {/* Tab 4: Authentication */}
            {activeTab === 'auth' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.8125rem' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="authType"
                      checked={authType === 'inventory'}
                      onChange={() => setAuthType('inventory')}
                    />
                    <span>Select from Key Inventory</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="authType"
                      checked={authType === 'manual'}
                      onChange={() => setAuthType('manual')}
                    />
                    <span>Manual Key Input</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="authType"
                      checked={authType === 'none'}
                      onChange={() => setAuthType('none')}
                    />
                    <span>None (Test Unauthenticated)</span>
                  </label>
                </div>

                {authType === 'inventory' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    <div className="form-group" style={{ margin: 0 }}>
                      <label className="form-label">Client Identity & Prefix</label>
                      <select
                        className="form-input"
                        value={selectedApiKeyId}
                        onChange={(e) => setSelectedApiKeyId(e.target.value)}
                        disabled={apiKeys.length === 0}
                      >
                        {apiKeys.length === 0 ? (
                          <option value="">No active API keys found</option>
                        ) : (
                          apiKeys.map((k) => (
                            <option key={k.id} value={k.id}>
                              {k.clientName} ({k.keyPrefix}) - Limit: {k.rateLimitPerMinute}/min
                            </option>
                          ))
                        )}
                      </select>
                    </div>

                    <div className="form-group" style={{ margin: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <label className="form-label">Client API Key Secret</label>
                        <span style={{ fontSize: '0.6875rem', color: '#94a3b8' }}>
                          In-memory only. Never persisted to disk or logs.
                        </span>
                      </div>
                      <div style={{ position: 'relative' }}>
                        <input
                          type={showApiKey ? 'text' : 'password'}
                          className="form-input"
                          style={{ width: '100%', paddingRight: '2.5rem', fontFamily: 'var(--font-mono)' }}
                          placeholder={`Enter key (e.g. ${selectedApiKey ? selectedApiKey.keyPrefix : 'edx_...'})`}
                          value={rawApiKeyInput}
                          onChange={(e) => setRawApiKeyInput(e.target.value)}
                        />
                        <button
                          type="button"
                          onClick={() => setShowApiKey(!showApiKey)}
                          style={{
                            position: 'absolute',
                            right: '0.5rem',
                            top: '50%',
                            transform: 'translateY(-50%)',
                            color: '#94a3b8',
                          }}
                          title={showApiKey ? 'Hide secret' : 'Show secret'}
                        >
                          {showApiKey ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {authType === 'manual' && (
                  <div className="form-group" style={{ margin: 0 }}>
                    <label className="form-label">Manual API Key Entry</label>
                    <div style={{ position: 'relative' }}>
                      <input
                        type={showApiKey ? 'text' : 'password'}
                        className="form-input"
                        style={{ width: '100%', paddingRight: '2.5rem', fontFamily: 'var(--font-mono)' }}
                        placeholder="edx_xxxxxxxx_xxxxxxxxxxxxxxxx"
                        value={rawApiKeyInput}
                        onChange={(e) => setRawApiKeyInput(e.target.value)}
                      />
                      <button
                        type="button"
                        onClick={() => setShowApiKey(!showApiKey)}
                        style={{
                          position: 'absolute',
                          right: '0.5rem',
                          top: '50%',
                          transform: 'translateY(-50%)',
                          color: '#94a3b8',
                        }}
                        title={showApiKey ? 'Hide secret' : 'Show secret'}
                      >
                        {showApiKey ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>
                )}

                {authType === 'none' && (
                  <div style={{ padding: '0.75rem', backgroundColor: '#fffbeb', borderRadius: 'var(--radius-md)', fontSize: '0.8125rem', color: '#92400e' }}>
                    Requests sent without an <code>X-API-Key</code> header will be evaluated by the gateway security filter and typically receive an <code>HTTP 401 Unauthorized</code> response.
                  </div>
                )}

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem', color: '#64748b' }}>
                  <Shield size={14} color="#10b981" />
                  <span>Need a new client API key?</span>
                  <button
                    type="button"
                    onClick={() => navigate('/api-keys')}
                    style={{ color: '#2563eb', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}
                  >
                    <span>Manage API Keys</span>
                    <ExternalLink size={12} />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* RIGHT: Response Viewer */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            <div className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <div className="card-header" style={{ marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Database size={18} color="#2563eb" />
                  <h2 className="card-title" style={{ margin: 0 }}>Gateway Response</h2>
                </div>
                {response && (
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                    <button
                      type="button"
                      onClick={handleCopyResponseBody}
                      className="btn btn-secondary btn-sm"
                      title="Copy response body"
                    >
                      {copiedBody ? <Check size={13} color="#10b981" /> : <Copy size={13} />}
                      <span>Copy Body</span>
                    </button>
                    {response.correlationId && (
                      <button
                        type="button"
                        onClick={() => handleTraceInLogs(response.correlationId!)}
                        className="btn btn-primary btn-sm"
                        title="View this request trace in Request Logs"
                      >
                        <ExternalLink size={13} />
                        <span>Trace in Logs</span>
                      </button>
                    )}
                  </div>
                )}
              </div>

              {response ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', flex: 1 }}>
                  {/* Status Bar */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '0.75rem 1rem',
                      backgroundColor:
                        response.status >= 200 && response.status < 300
                          ? 'var(--color-success-light)'
                          : response.status === 429
                          ? 'var(--color-warning-light)'
                          : 'var(--color-danger-light)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <span
                        className={`badge ${
                          response.status >= 200 && response.status < 300
                            ? 'badge-success'
                            : response.status === 429
                            ? 'badge-warning'
                            : 'badge-danger'
                        }`}
                        style={{ fontSize: '0.875rem', padding: '0.25rem 0.625rem' }}
                      >
                        {response.status} {response.statusText}
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8125rem', color: '#475569' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                        <Clock size={14} />
                        <strong>{response.durationMs} ms</strong>
                      </span>
                      <span>
                        Size: <strong>{response.sizeBytes} B</strong>
                      </span>
                      {response.cached && (
                        <span className="badge badge-info">IDEMPOTENT CACHE HIT</span>
                      )}
                    </div>
                  </div>

                  {/* Diagnostic Badges */}
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.375rem' }}>
                    {response.correlationId && (
                      <span className="badge badge-neutral" style={{ fontSize: '0.6875rem' }}>
                        Correlation: {response.correlationId}
                      </span>
                    )}
                    {response.rateLimitRemaining !== undefined && (
                      <span className="badge badge-neutral" style={{ fontSize: '0.6875rem' }}>
                        Rate Limit: {response.rateLimitRemaining} / {response.rateLimitLimit || '?'}
                      </span>
                    )}
                    {response.retryAfter && (
                      <span className="badge badge-warning" style={{ fontSize: '0.6875rem' }}>
                        Retry-After: {response.retryAfter}s
                      </span>
                    )}
                    {response.gatewayServiceId && (
                      <span className="badge badge-neutral" style={{ fontSize: '0.6875rem' }}>
                        Service: {response.gatewayServiceId}
                      </span>
                    )}
                  </div>

                  {/* Response Tabs */}
                  <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', gap: '0.5rem' }}>
                    <button
                      type="button"
                      className={`btn btn-sm ${responseTab === 'body' ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setResponseTab('body')}
                    >
                      Body
                    </button>
                    <button
                      type="button"
                      className={`btn btn-sm ${responseTab === 'headers' ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setResponseTab('headers')}
                    >
                      Headers ({Object.keys(response.headers).length})
                    </button>
                    <button
                      type="button"
                      className={`btn btn-sm ${responseTab === 'details' ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setResponseTab('details')}
                    >
                      Request Details
                    </button>
                  </div>

                  {/* Response Tab: Body */}
                  {responseTab === 'body' && (
                    <div
                      style={{
                        flex: 1,
                        minHeight: '220px',
                        maxHeight: '400px',
                        overflowY: 'auto',
                        backgroundColor: '#0f172a',
                        color: '#f8fafc',
                        borderRadius: 'var(--radius-md)',
                        padding: '1rem',
                        fontFamily: 'var(--font-mono)',
                        fontSize: '0.8125rem',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                      }}
                    >
                      {response.body || (
                        <span style={{ color: '#64748b', fontStyle: 'italic' }}>
                          No response body received (204 No Content or empty payload)
                        </span>
                      )}
                    </div>
                  )}

                  {/* Response Tab: Headers */}
                  {responseTab === 'headers' && (
                    <div className="table-container" style={{ maxHeight: '350px', overflowY: 'auto' }}>
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Header</th>
                            <th>Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          {Object.entries(response.headers).map(([key, value]) => (
                            <tr key={key}>
                              <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)', color: '#2563eb' }}>
                                {key}
                              </td>
                              <td style={{ fontFamily: 'var(--font-mono)', wordBreak: 'break-all' }}>
                                {value}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {/* Response Tab: Details */}
                  {responseTab === 'details' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.8125rem' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Executed Route:</span>
                        <span style={{ fontFamily: 'var(--font-mono)' }}>{fullGatewayPath}</span>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Downstream Host:</span>
                        <span>{selectedService?.baseUrl || 'Unresolved'}</span>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Correlation ID:</span>
                        <span style={{ fontFamily: 'var(--font-mono)' }}>{response.correlationId || 'N/A'}</span>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Client Identity:</span>
                        <span>{selectedApiKey ? `${selectedApiKey.clientName} (${selectedApiKey.keyPrefix})` : 'Anonymous / Custom'}</span>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', padding: '0.5rem 0' }}>
                        <span style={{ color: '#64748b', fontWeight: 600 }}>Execution Latency:</span>
                        <span>{response.durationMs} ms (network roundtrip)</span>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '3rem 1.5rem',
                    color: '#94a3b8',
                    textAlign: 'center',
                    gap: '0.75rem',
                  }}
                >
                  <Layers size={36} color="#cbd5e1" />
                  <div>
                    <h3 style={{ fontSize: '0.9375rem', fontWeight: 600, color: '#475569', marginBottom: '0.25rem' }}>
                      Ready to execute
                    </h3>
                    <p style={{ fontSize: '0.8125rem', maxWidth: '320px', margin: '0 auto' }}>
                      Configure your request method, route, headers, and authentication on the left, then click <strong>Send</strong>.
                    </p>
                  </div>
                </div>
              )}
            </div>

            {/* Recent Requests History Card */}
            <div className="card">
              <div className="card-header" style={{ marginBottom: '0.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <History size={16} color="#2563eb" />
                  <h3 className="card-title" style={{ fontSize: '0.9375rem', margin: 0 }}>Recent Requests</h3>
                </div>
                {history.length > 0 && (
                  <button
                    type="button"
                    onClick={handleClearHistory}
                    className="btn btn-secondary btn-sm"
                    style={{ fontSize: '0.6875rem', padding: '0.25rem 0.5rem' }}
                  >
                    Clear History
                  </button>
                )}
              </div>

              {history.length === 0 ? (
                <p style={{ color: '#94a3b8', fontSize: '0.75rem', margin: 0 }}>
                  No previous requests stored in this browser session.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '200px', overflowY: 'auto' }}>
                  {history.map((item) => (
                    <div
                      key={item.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '0.5rem 0.625rem',
                        backgroundColor: '#f8fafc',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '0.75rem',
                        border: '1px solid #e2e8f0',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', minWidth: 0 }}>
                        <span
                          className={`badge ${
                            item.method === 'GET'
                              ? 'badge-info'
                              : item.method === 'POST'
                              ? 'badge-success'
                              : item.method === 'DELETE'
                              ? 'badge-danger'
                              : 'badge-warning'
                          }`}
                          style={{ fontSize: '0.6875rem', padding: '0.125rem 0.375rem' }}
                        >
                          {item.method}
                        </span>
                        <span style={{ fontWeight: 600, color: '#0f172a' }}>{item.serviceName}</span>
                        <span style={{ fontFamily: 'var(--font-mono)', color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          /gateway{item.pathPrefix}{item.endpointPath}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        {item.responseStatus && (
                          <span
                            style={{
                              fontWeight: 700,
                              color:
                                item.responseStatus < 300
                                  ? '#059669'
                                  : item.responseStatus === 429
                                  ? '#d97706'
                                  : '#dc2626',
                            }}
                          >
                            {item.responseStatus}
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={() => handleRestoreHistory(item)}
                          className="btn btn-secondary btn-sm"
                          style={{ fontSize: '0.6875rem', padding: '0.2rem 0.4rem' }}
                          title="Restore request into builder"
                        >
                          Restore
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};
