import React from 'react';

interface MetricBarChartProps {
  data: Record<string, number>;
  title: string;
}

const COLOR_MAP: Record<string, string> = {
  SUCCESS: '#10b981',
  CLIENT_ERROR: '#f59e0b',
  SERVER_ERROR: '#ef4444',
  TIMEOUT: '#dc2626',
  CIRCUIT_OPEN: '#7c3aed',
  RATE_LIMITED: '#d97706',
};

export const MetricBarChart: React.FC<MetricBarChartProps> = ({ data, title }) => {
  const entries = Object.entries(data || {});
  const total = entries.reduce((acc, [, val]) => acc + val, 0);

  if (entries.length === 0 || total === 0) {
    return (
      <div className="card">
        <h3 className="card-title">{title}</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginTop: '1rem' }}>
          No request activity recorded yet.
        </p>
      </div>
    );
  }

  const maxVal = Math.max(...entries.map(([, val]) => val), 1);

  return (
    <div className="card">
      <div className="card-header">
        <h3 className="card-title">{title}</h3>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Total: {total.toLocaleString()}
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
        {entries.map(([label, count]) => {
          const percent = Math.round((count / total) * 100);
          const barWidthPercent = Math.max(Math.round((count / maxVal) * 100), 2);
          const barColor = COLOR_MAP[label] || '#3b82f6';

          return (
            <div key={label} style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '0.8125rem',
                  fontWeight: 500,
                }}
              >
                <span>{label}</span>
                <span style={{ color: 'var(--text-muted)' }}>
                  {count.toLocaleString()} ({percent}%)
                </span>
              </div>
              <div
                style={{
                  width: '100%',
                  height: '0.625rem',
                  backgroundColor: '#f1f5f9',
                  borderRadius: '9999px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${barWidthPercent}%`,
                    height: '100%',
                    backgroundColor: barColor,
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
