import React from 'react';
import { LatencyMetricsDto } from '../../types/metrics';

interface LatencyPercentileChartProps {
  latency: LatencyMetricsDto;
}

export const LatencyPercentileChart: React.FC<LatencyPercentileChartProps> = ({ latency }) => {
  if (!latency || (latency.maxMs === 0 && latency.avgMs === 0)) {
    return (
      <div className="card">
        <h3 className="card-title">Latency Distribution</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginTop: '1rem' }}>
          No latency measurements recorded yet.
        </p>
      </div>
    );
  }

  const items = [
    { label: 'Minimum', val: latency.minMs, color: '#10b981' },
    { label: 'Average', val: latency.avgMs, color: '#3b82f6' },
    { label: 'p50 (Median)', val: latency.p50Ms, color: '#2563eb' },
    { label: 'p90', val: latency.p90Ms, color: '#f59e0b' },
    { label: 'p95', val: latency.p95Ms, color: '#ea580c' },
    { label: 'p99', val: latency.p99Ms, color: '#dc2626' },
    { label: 'Maximum', val: latency.maxMs, color: '#991b1b' },
  ];

  const maxVal = Math.max(...items.map((i) => i.val), 1);

  return (
    <div className="card">
      <div className="card-header">
        <h3 className="card-title">Latency Percentiles & Distribution</h3>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Avg: {latency.avgMs} ms
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {items.map((item) => {
          const widthPercent = Math.max(Math.round((item.val / maxVal) * 100), 2);

          return (
            <div key={item.label} style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '0.8125rem',
                }}
              >
                <span style={{ fontWeight: 500 }}>{item.label}</span>
                <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                  {item.val} ms
                </span>
              </div>
              <div
                style={{
                  width: '100%',
                  height: '0.5rem',
                  backgroundColor: '#f1f5f9',
                  borderRadius: '9999px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${widthPercent}%`,
                    height: '100%',
                    backgroundColor: item.color,
                    borderRadius: '9999px',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
