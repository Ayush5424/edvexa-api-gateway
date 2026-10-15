import React from 'react';

interface StatusBadgeProps {
  status: string | number;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const strStatus = String(status).toUpperCase();

  let badgeClass = 'badge-neutral';

  if (
    strStatus === 'CLOSED' ||
    strStatus === 'HEALTHY' ||
    strStatus === 'ACTIVE' ||
    strStatus === 'SUCCESS' ||
    strStatus === 'TRUE' ||
    strStatus === '200' ||
    strStatus === '201' ||
    strStatus === '204'
  ) {
    badgeClass = 'badge-success';
  } else if (
    strStatus === 'HALF_OPEN' ||
    strStatus === 'DEGRADED' ||
    strStatus === 'RATE_LIMITED' ||
    strStatus === 'CLIENT_ERROR' ||
    (typeof status === 'number' && status >= 400 && status < 500)
  ) {
    badgeClass = 'badge-warning';
  } else if (
    strStatus === 'OPEN' ||
    strStatus === 'UNHEALTHY' ||
    strStatus === 'REVOKED' ||
    strStatus === 'SERVER_ERROR' ||
    strStatus === 'TIMEOUT' ||
    strStatus === 'CIRCUIT_OPEN' ||
    strStatus === 'FALSE' ||
    (typeof status === 'number' && status >= 500)
  ) {
    badgeClass = 'badge-danger';
  } else if (strStatus === 'DISABLED') {
    badgeClass = 'badge-neutral';
  }

  return <span className={`badge ${badgeClass}`}>{strStatus}</span>;
};
