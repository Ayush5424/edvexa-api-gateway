import React, { useEffect, useState, useCallback } from 'react';
import { ShieldCheck, ShieldAlert, AlertTriangle, Clock, RefreshCw, Activity } from 'lucide-react';
import { Header } from '../components/layout/Header';
import { StatusBadge } from '../components/common/StatusBadge';
import { Alert } from '../components/common/Alert';
import { observabilityService } from '../services/observabilityService';
import { ServiceHealthDto } from '../types/metrics';

export const ResiliencePage: React.FC = () => {
  const [services, setServices] = useState<ServiceHealthDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const fetchHealth = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    try {
      const data = await observabilityService.getServiceHealth();
      setServices(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to retrieve resilience health metrics';
      setAlert({ type: 'error', message: msg });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();
  }, [fetchHealth]);

  const closedCount = services.filter((s) => s.circuitBreakerState === 'CLOSED').length;
  const openCount = services.filter((s) => s.circuitBreakerState === 'OPEN').length;
  const halfOpenCount = services.filter((s) => s.circuitBreakerState === 'HALF_OPEN').length;

  return (
    <>
      <Header
        title="Resilience & Circuit Breakers"
        subtitle="Real-time Resilience4j state machines, failure rate thresholds, and timeout controls"
        onRefresh={() => fetchHealth(true)}
        isRefreshing={refreshing}
      />

      <div className="page-container">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

        {/* High level resilience state cards */}
        <div className="grid-cols-4">
          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-label">Circuits Closed</span>
              <div className="metric-icon" style={{ backgroundColor: '#ecfdf5', color: '#10b981' }}>
                <ShieldCheck size={18} />
              </div>
            </div>
            <div className="metric-value">{closedCount}</div>
            <div className="metric-subtext">Normal downstream execution</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-label">Circuits Half-Open</span>
              <div className="metric-icon" style={{ backgroundColor: '#fffbeb', color: '#f59e0b' }}>
                <RefreshCw size={18} />
              </div>
            </div>
            <div className="metric-value">{halfOpenCount}</div>
            <div className="metric-subtext">Trial calls testing recovery</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-label">Circuits Open</span>
              <div className="metric-icon" style={{ backgroundColor: '#fef2f2', color: '#ef4444' }}>
                <ShieldAlert size={18} />
              </div>
            </div>
            <div className="metric-value">{openCount}</div>
            <div className="metric-subtext">Fail-fast active without downstream calls</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span className="metric-label">Resilience Policy</span>
              <div className="metric-icon" style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>
                <Activity size={18} />
              </div>
            </div>
            <div className="metric-value" style={{ fontSize: '1.25rem', paddingTop: '0.25rem' }}>
              Sliding Count
            </div>
            <div className="metric-subtext">50% failure rate threshold (5-call window)</div>
          </div>
        </div>

        {/* Detailed Service Circuit Breaker Cards */}
        <div style={{ marginTop: '0.5rem' }}>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '0.25rem' }}>
            Service Circuit Breaker Status
          </h2>
          <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
            Resilience4j automatically isolates downstream failures, preventing cascading microservice collapse.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {services.length === 0 ? (
              <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                {loading ? 'Evaluating resilience states...' : 'No downstream services registered.'}
              </div>
            ) : (
              services.map((svc) => (
                <div key={svc.serviceId} className="card">
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderBottom: '1px solid var(--border-color)',
                      paddingBottom: '1rem',
                      marginBottom: '1.25rem',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <h3 style={{ fontSize: '1.125rem', fontWeight: 700 }}>{svc.name}</h3>
                        <span className="code-badge">{svc.pathPrefix}</span>
                      </div>
                      <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginTop: '0.125rem' }}>
                        Target: {svc.baseUrl}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <StatusBadge status={svc.circuitBreakerState} />
                      <StatusBadge status={svc.status} />
                    </div>
                  </div>

                  {/* Metrics Grid */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
                    <div style={{ padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '0.5rem' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>FAILURE RATE</div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 700, color: svc.failureRate > 50 ? '#ef4444' : '#10b981' }}>
                        {svc.failureRate.toFixed(1)}%
                      </div>
                      <div style={{ fontSize: '0.6875rem', color: 'var(--text-muted)' }}>Threshold: 50.0%</div>
                    </div>

                    <div style={{ padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '0.5rem' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>BUFFERED CALLS</div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>{svc.numberOfBufferedCalls}</div>
                      <div style={{ fontSize: '0.6875rem', color: 'var(--text-muted)' }}>Window size: 5 calls</div>
                    </div>

                    <div style={{ padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '0.5rem' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>SUCCESS / FAILED</div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>
                        <span style={{ color: '#10b981' }}>{svc.numberOfSuccessfulCalls}</span> /{' '}
                        <span style={{ color: '#ef4444' }}>{svc.numberOfFailedCalls}</span>
                      </div>
                      <div style={{ fontSize: '0.6875rem', color: 'var(--text-muted)' }}>Recent window ratio</div>
                    </div>

                    <div style={{ padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '0.5rem' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>REJECTED CALLS</div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 700, color: svc.numberOfNotPermittedCalls > 0 ? '#ef4444' : 'inherit' }}>
                        {svc.numberOfNotPermittedCalls}
                      </div>
                      <div style={{ fontSize: '0.6875rem', color: 'var(--text-muted)' }}>Rejected while OPEN</div>
                    </div>

                    <div style={{ padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '0.5rem' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>TIMEOUT CONFIG</div>
                      <div style={{ fontSize: '0.9375rem', fontWeight: 600, marginTop: '0.25rem' }}>
                        Conn: {svc.connectionTimeoutMs}ms
                      </div>
                      <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Resp: {svc.responseTimeoutMs}ms</div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </>
  );
};
