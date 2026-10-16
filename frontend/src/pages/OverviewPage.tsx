import React, { useEffect, useState, useCallback } from 'react';
import {
  Activity,
  CheckCircle,
  AlertTriangle,
  Clock,
  RotateCcw,
  ZapOff,
  Server,
  ShieldAlert,
} from 'lucide-react';
import { Header } from '../components/layout/Header';
import { MetricCard } from '../components/common/MetricCard';
import { StatusBadge } from '../components/common/StatusBadge';
import { Alert } from '../components/common/Alert';
import { MetricBarChart } from '../components/charts/MetricBarChart';
import { LatencyPercentileChart } from '../components/charts/LatencyPercentileChart';
import { observabilityService } from '../services/observabilityService';
import { GatewayMetricsDto, ServiceHealthDto } from '../types/metrics';
import { RecentErrorDto } from '../types/logs';

export const OverviewPage: React.FC = () => {
  const [metrics, setMetrics] = useState<GatewayMetricsDto | null>(null);
  const [services, setServices] = useState<ServiceHealthDto[]>([]);
  const [recentErrors, setRecentErrors] = useState<RecentErrorDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    setError(null);
    try {
      const [metricsData, healthData, errorsData] = await Promise.all([
        observabilityService.getMetrics(),
        observabilityService.getServiceHealth(),
        observabilityService.getRecentErrors(0, 5),
      ]);
      setMetrics(metricsData);
      setServices(healthData);
      setRecentErrors(errorsData.content || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch observability metrics';
      setError(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const totalRequests = metrics?.totalRequests || 0;
  const successCount = metrics?.successCount || 0;
  const failureCount = metrics?.failureCount || 0;
  const successRate = totalRequests > 0 ? ((successCount / totalRequests) * 100).toFixed(1) : '100.0';
  const openBreakers = services.filter((s) => s.circuitBreakerState === 'OPEN').length;
  const activeServices = services.filter((s) => s.enabled).length;

  return (
    <>
      <Header
        title="Gateway Overview"
        subtitle="Live traffic metrics, latency percentiles, and resilience health monitoring"
        onRefresh={() => fetchData(true)}
        isRefreshing={refreshing}
      />

      <div className="page-container">
        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

        {/* Primary KPI Metrics */}
        <div className="grid-cols-4">
          <MetricCard
            label="Total Requests"
            value={totalRequests.toLocaleString()}
            subtext={`${successRate}% success rate`}
            icon={Activity}
            iconColor="#2563eb"
            iconBg="#eff6ff"
          />
          <MetricCard
            label="Successful"
            value={successCount.toLocaleString()}
            subtext="HTTP 2xx & 3xx"
            icon={CheckCircle}
            iconColor="#10b981"
            iconBg="#ecfdf5"
          />
          <MetricCard
            label="Failures / Errors"
            value={failureCount.toLocaleString()}
            subtext={`${metrics?.clientErrorCount || 0} client / ${metrics?.serverErrorCount || 0} server`}
            icon={AlertTriangle}
            iconColor="#ef4444"
            iconBg="#fef2f2"
          />
          <MetricCard
            label="Average Latency"
            value={metrics ? `${metrics.latency.avgMs} ms` : '0 ms'}
            subtext={metrics ? `p95: ${metrics.latency.p95Ms} ms` : 'p95: 0 ms'}
            icon={Clock}
            iconColor="#f59e0b"
            iconBg="#fffbeb"
          />
        </div>

        {/* Secondary Resilience Metrics */}
        <div className="grid-cols-4">
          <MetricCard
            label="Active Services"
            value={`${activeServices} / ${services.length}`}
            subtext="Registered & enabled"
            icon={Server}
            iconColor="#3b82f6"
            iconBg="#eff6ff"
          />
          <MetricCard
            label="Open Breakers"
            value={openBreakers}
            subtext={openBreakers === 0 ? 'All circuits healthy' : 'Action required'}
            icon={ShieldAlert}
            iconColor={openBreakers > 0 ? '#ef4444' : '#10b981'}
            iconBg={openBreakers > 0 ? '#fef2f2' : '#ecfdf5'}
          />
          <MetricCard
            label="Retries Performed"
            value={(metrics?.retryCount || 0).toLocaleString()}
            subtext="Bounded exponential backoff"
            icon={RotateCcw}
            iconColor="#7c3aed"
            iconBg="#f5f3ff"
          />
          <MetricCard
            label="Rate Limits Exceeded"
            value={(metrics?.rateLimitedCount || 0).toLocaleString()}
            subtext={`${metrics?.timeoutCount || 0} timeouts`}
            icon={ZapOff}
            iconColor="#ea580c"
            iconBg="#fff7ed"
          />
        </div>

        {/* Charts Section */}
        {metrics && (
          <div className="grid-cols-2">
            <LatencyPercentileChart latency={metrics.latency} />
            <MetricBarChart
              data={metrics.requestsByOutcome}
              title="Request Outcome Breakdown"
            />
          </div>
        )}

        {/* Downstream Service Health Preview */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">Downstream Services Status</h3>
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              {services.length} registered microservices
            </span>
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Service</th>
                  <th>Path Prefix</th>
                  <th>Circuit Breaker</th>
                  <th>Failure Rate</th>
                  <th>Status</th>
                  <th>Timeouts</th>
                </tr>
              </thead>
              <tbody>
                {services.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                      {loading ? 'Loading service statuses...' : 'No registered services found.'}
                    </td>
                  </tr>
                ) : (
                  services.map((svc) => (
                    <tr key={svc.serviceId}>
                      <td style={{ fontWeight: 600 }}>{svc.name}</td>
                      <td>
                        <span className="code-badge">{svc.pathPrefix}</span>
                      </td>
                      <td>
                        <StatusBadge status={svc.circuitBreakerState} />
                      </td>
                      <td>{svc.failureRate.toFixed(1)}%</td>
                      <td>
                        <StatusBadge status={svc.status} />
                      </td>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                        {svc.connectionTimeoutMs}ms / {svc.responseTimeoutMs}ms
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Recent Failures Preview */}
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">Recent Errors & Diagnostic Traces</h3>
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Latest 5 failures
            </span>
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Correlation ID</th>
                  <th>Method & Route</th>
                  <th>Status</th>
                  <th>Outcome</th>
                  <th>Diagnostic Details</th>
                </tr>
              </thead>
              <tbody>
                {recentErrors.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                      No recent gateway failures recorded.
                    </td>
                  </tr>
                ) : (
                  recentErrors.map((err) => (
                    <tr key={err.id}>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                        {new Date(err.timestamp).toLocaleTimeString()}
                      </td>
                      <td>
                        <span className="code-badge">{err.correlationId}</span>
                      </td>
                      <td>
                        <strong>{err.httpMethod}</strong> {err.requestPath}
                      </td>
                      <td>
                        <StatusBadge status={err.statusCode} />
                      </td>
                      <td>
                        <StatusBadge status={err.outcomeCategory} />
                      </td>
                      <td style={{ fontSize: '0.8125rem', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {err.errorMessage || 'No message provided'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </>
  );
};
