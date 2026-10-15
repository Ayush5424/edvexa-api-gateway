import React from 'react';
import { AlertCircle, CheckCircle, Info, X } from 'lucide-react';

interface AlertProps {
  type?: 'error' | 'success' | 'info';
  message: string;
  onClose?: () => void;
}

export const Alert: React.FC<AlertProps> = ({ type = 'info', message, onClose }) => {
  if (!message) return null;

  let bg = '#eff6ff';
  let border = '#bfdbfe';
  let color = '#1e40af';
  let Icon = Info;

  if (type === 'error') {
    bg = '#fef2f2';
    border = '#fecaca';
    color = '#991b1b';
    Icon = AlertCircle;
  } else if (type === 'success') {
    bg = '#ecfdf5';
    border = '#a7f3d0';
    color = '#065f46';
    Icon = CheckCircle;
  }

  return (
    <div
      style={{
        backgroundColor: bg,
        borderColor: border,
        color: color,
        borderWidth: 1,
        borderStyle: 'solid',
        borderRadius: '0.5rem',
        padding: '0.75rem 1rem',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: '0.875rem',
        marginBottom: '1rem',
      }}
      role="alert"
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <Icon size={16} />
        <span>{message}</span>
      </div>
      {onClose && (
        <button
          onClick={onClose}
          style={{ color, background: 'none', border: 'none', cursor: 'pointer' }}
          aria-label="Close alert"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
};
