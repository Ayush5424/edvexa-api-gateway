import React from 'react';
import { LucideIcon } from 'lucide-react';

interface MetricCardProps {
  label: string;
  value: string | number;
  subtext?: string;
  icon: LucideIcon;
  iconColor?: string;
  iconBg?: string;
}

export const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  subtext,
  icon: Icon,
  iconColor = '#2563eb',
  iconBg = '#eff6ff',
}) => {
  return (
    <div className="metric-card">
      <div className="metric-header">
        <span className="metric-label">{label}</span>
        <div className="metric-icon" style={{ backgroundColor: iconBg, color: iconColor }}>
          <Icon size={18} />
        </div>
      </div>
      <div className="metric-value">{value}</div>
      {subtext && <div className="metric-subtext">{subtext}</div>}
    </div>
  );
};
