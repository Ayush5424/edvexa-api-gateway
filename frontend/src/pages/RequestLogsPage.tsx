import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Copy, Check, Filter, Info, X } from 'lucide-react';
import { Header } from '../components/layout/Header';
import { StatusBadge } from '../components/common/StatusBadge';
import { Pagination } from '../components/common/Pagination';
import { Modal } from '../components/common/Modal';
import { Alert } from '../components/common/Alert';
import { observabilityService } from '../services/observabilityService';
import { GatewayRequestLog, PageResponse } from '../types/logs';

export const RequestLogsPage: React.FC = () => {
  const [logsPage, setLogsPage] = useState<PageResponse<GatewayRequestLog>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 20,
    number: 0,
    first: true,
    last: true,
    empty: true,
  });

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const [searchParams] = useSearchParams();
  const [serviceFilter, setServiceFilter] = useState('');
  const [clientFilter, setClientFilter] = useState('');
  const [outcomeFilter, setOutcomeFilter] = useState('');
  const [correlationSearch, setCorrelationSearch] = useState(() => searchParams.get('correlationId') || '');
  const [currentPage, setCurrentPage] = useState(0);

  useEffect(() => {
    const urlCorrId = searchParams.get('correlationId');
    if (urlCorrId) {
      setCorrelationSearch(urlCorrId);
    }
  }, [searchParams]);

  // Selected log for detailed diagnostic view
  const [selectedLog, setSelectedLog] = useState<GatewayRequestLog | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const fetchLogs = useCallback(
    async (isManualRefresh = false) => {
      if (isManualRefresh) setRefreshing(true);
      try {
        if (correlationSearch.trim()) {
          const list = await observabilityService.getLogsByCorrelationId(correlationSearch.trim());
          setLogsPage({
            content: list,
            totalElements: list.length,
            totalPages: 1,
            size: list.length || 20,
            number: 0,
            first: true,
            last: true,
            empty: list.length === 0,
          });
        } else {
          const data = await observabilityService.getAuditLogs({
            serviceId: serviceFilter.trim() || undefined,
            clientId: clientFilter.trim() || undefined,
            outcome: outcomeFilter.trim() || undefined,
            page: currentPage,
            size: 20,
          });
          setLogsPage(data);
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to retrieve request logs';
        setAlert({ type: 'error', message: msg });
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [serviceFilter, clientFilter, outcomeFilter, correlationSearch, currentPage]
  );

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const handleCopy = (correlationId: string) => {
    navigator.clipboard.writeText(correlationId);
    setCopiedId(correlationId);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    fetchLogs();
  };

  const handleClearFilters = () => {
    setServiceFilter('');
    setClientFilter('');
    setOutcomeFilter('');
    setCorrelationSearch('');
    setCurrentPage(0);
  };

  return (
    <>
      <Header
        title="Request Audit Logs"
        subtitle="End-to-end distributed request tracking with correlation IDs and diagnostic audit logs"
        onRefresh={() => fetchLogs(true)}
        isRefreshing={refreshing}
      />

      <div className="page-container">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

        {/* Filter Bar */}
        <div className="card">
          <form onSubmit={handleSearchSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', alignItems: 'flex-end' }}>
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Correlation ID Search</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="text"
                    className="form-input"
                    style={{ width: '100%', paddingLeft: '2rem' }}
                    value={correlationSearch}
                    onChange={(e) => setCorrelationSearch(e.target.value)}
                    placeholder="Search by exact trace ID"
                  />
                  <Search size={14} color="#94a3b8" style={{ position: 'absolute', left: '0.625rem', top: '50%', transform: 'translateY(-50%)' }} />
                </div>
              </div>

              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Service ID</label>
                <input
                  type="text"
                  className="form-input"
                  value={serviceFilter}
                  onChange={(e) => {
                    setServiceFilter(e.target.value);
                    setCurrentPage(0);
                  }}
                  placeholder="Filter by service"
                  disabled={Boolean(correlationSearch)}
                />
              </div>

              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Client ID</label>
                <input
                  type="text"
                  className="form-input"
                  value={clientFilter}
                  onChange={(e) => {
                    setClientFilter(e.target.value);
                    setCurrentPage(0);
                  }}
                  placeholder="Filter by client"
                  disabled={Boolean(correlationSearch)}
                />
              </div>

              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Outcome Category</label>
                <select
                  className="form-input"
                  value={outcomeFilter}
                  onChange={(e) => {
                    setOutcomeFilter(e.target.value);
                    setCurrentPage(0);
                  }}
                  disabled={Boolean(correlationSearch)}
                >
                  <option value="">All Outcomes</option>
                  <option value="SUCCESS">SUCCESS</option>
                  <option value="CLIENT_ERROR">CLIENT_ERROR</option>
                  <option value="SERVER_ERROR">SERVER_ERROR</option>
                  <option value="TIMEOUT">TIMEOUT</option>
                  <option value="CIRCUIT_OPEN">CIRCUIT_OPEN</option>
                  <option value="RATE_LIMITED">RATE_LIMITED</option>
                </select>
              </div>

              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>
                  <Filter size={14} />
                  <span>Filter</span>
                </button>
                {(serviceFilter || clientFilter || outcomeFilter || correlationSearch) && (
                  <button type="button" onClick={handleClearFilters} className="btn btn-secondary">
                    <X size={14} />
                  </button>
                )}
              </div>
            </div>
          </form>
        </div>

        {/* Logs Table */}
        <div className="card" style={{ padding: 0 }}>
          <div className="table-container" style={{ border: 'none' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Correlation ID</th>
                  <th>Method & Route</th>
                  <th>Service</th>
                  <th>Client</th>
                  <th>Status</th>
                  <th>Latency</th>
                  <th>Outcome</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {logsPage.content.length === 0 ? (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      {loading ? 'Querying gateway audit logs...' : 'No request audit records matching the specified criteria.'}
                    </td>
                  </tr>
                ) : (
                  logsPage.content.map((log) => (
                    <tr key={log.id}>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </td>
                      <td>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem' }}>
                          <span className="code-badge">{log.correlationId.substring(0, 12)}...</span>
                          <button
                            onClick={() => handleCopy(log.correlationId)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8' }}
                            title="Copy full Correlation ID"
                          >
                            {copiedId === log.correlationId ? <Check size={12} color="#10b981" /> : <Copy size={12} />}
                          </button>
                        </div>
                      </td>
                      <td>
                        <strong style={{ fontSize: '0.75rem', marginRight: '0.25rem' }}>{log.httpMethod}</strong>
                        <span style={{ fontSize: '0.8125rem' }}>{log.requestPath}</span>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.8125rem' }}>{log.serviceId || 'none'}</span>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.8125rem' }}>{log.clientId || 'anonymous'}</span>
                      </td>
                      <td>
                        <StatusBadge status={log.statusCode} />
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>
                        {log.latencyMs} ms
                      </td>
                      <td>
                        <StatusBadge status={log.outcomeCategory} />
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          onClick={() => setSelectedLog(log)}
                          className="btn btn-secondary btn-sm"
                          title="View diagnostic details"
                        >
                          <Info size={14} />
                          <span>Details</span>
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            currentPage={logsPage.number}
            totalPages={logsPage.totalPages}
            totalElements={logsPage.totalElements}
            pageSize={logsPage.size}
            onPageChange={(page) => setCurrentPage(page)}
          />
        </div>
      </div>

      {/* Log Detail Modal */}
      <Modal isOpen={Boolean(selectedLog)} onClose={() => setSelectedLog(null)} title="Request Diagnostic Details">
        {selectedLog && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
              <div>
                <label className="form-label">Correlation ID</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span className="code-badge" style={{ wordBreak: 'break-all' }}>
                    {selectedLog.correlationId}
                  </span>
                  <button
                    onClick={() => handleCopy(selectedLog.correlationId)}
                    className="btn btn-secondary btn-sm"
                    title="Copy Correlation ID"
                  >
                    {copiedId === selectedLog.correlationId ? <Check size={12} color="#10b981" /> : <Copy size={12} />}
                  </button>
                </div>
              </div>

              <div>
                <label className="form-label">Timestamp</label>
                <div style={{ fontSize: '0.875rem' }}>{new Date(selectedLog.timestamp).toLocaleString()}</div>
              </div>

              <div>
                <label className="form-label">HTTP Method & Path</label>
                <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>
                  {selectedLog.httpMethod} {selectedLog.requestPath}
                </div>
              </div>

              <div>
                <label className="form-label">HTTP Status Code</label>
                <div>
                  <StatusBadge status={selectedLog.statusCode} />
                </div>
              </div>

              <div>
                <label className="form-label">Downstream Service</label>
                <div style={{ fontSize: '0.875rem' }}>{selectedLog.serviceId || 'None'}</div>
              </div>

              <div>
                <label className="form-label">Client Identity</label>
                <div style={{ fontSize: '0.875rem' }}>{selectedLog.clientId || 'Anonymous'}</div>
              </div>

              <div>
                <label className="form-label">Total Gateway Latency</label>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>{selectedLog.latencyMs} ms</div>
              </div>

              <div>
                <label className="form-label">Retry Attempts</label>
                <div style={{ fontSize: '0.875rem' }}>{selectedLog.retryCount}</div>
              </div>

              <div>
                <label className="form-label">Circuit Breaker State</label>
                <div>
                  <StatusBadge status={selectedLog.circuitBreakerState || 'N/A'} />
                </div>
              </div>

              <div>
                <label className="form-label">Outcome Category</label>
                <div>
                  <StatusBadge status={selectedLog.outcomeCategory} />
                </div>
              </div>
            </div>

            {selectedLog.errorMessage && (
              <div style={{ marginTop: '1rem' }}>
                <label className="form-label">Diagnostic Error Message</label>
                <pre
                  style={{
                    backgroundColor: '#f8fafc',
                    border: '1px solid var(--border-color)',
                    padding: '0.75rem',
                    borderRadius: '0.375rem',
                    fontSize: '0.75rem',
                    fontFamily: 'var(--font-mono)',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    color: '#991b1b',
                  }}
                >
                  {selectedLog.errorMessage}
                </pre>
              </div>
            )}

            <div className="modal-footer">
              <button onClick={() => setSelectedLog(null)} className="btn btn-secondary">
                Close
              </button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
};
