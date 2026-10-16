import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Activity,
  Server,
  Key,
  FileText,
  ShieldAlert,
  Terminal,
  LogOut,
  Layers,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const Sidebar: React.FC = () => {
  const { logout, user } = useAuth();

  const navItems = [
    { to: '/', label: 'Overview', icon: Activity },
    { to: '/services', label: 'Service Registry', icon: Server },
    { to: '/api-keys', label: 'API Keys', icon: Key },
    { to: '/playground', label: 'API Playground', icon: Terminal },
    { to: '/logs', label: 'Request Logs', icon: FileText },
    { to: '/resilience', label: 'Resilience & Circuit Breakers', icon: ShieldAlert },
  ];

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <Layers size={24} color="#3b82f6" />
        <div>
          <div className="sidebar-logo">Edvexa Gateway</div>
          <span className="sidebar-badge">Resilience Hub</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
              end={item.to === '/'}
            >
              <Icon size={18} />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div>
          <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#f1f5f9' }}>
            {user?.username || 'Administrator'}
          </div>
          <div style={{ fontSize: '0.6875rem', color: '#94a3b8' }}>
            {user?.role || 'ROLE_ADMIN'}
          </div>
        </div>
        <button
          onClick={logout}
          className="btn btn-secondary btn-sm"
          title="Sign out"
          style={{ padding: '0.375rem', color: '#94a3b8' }}
        >
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  );
};
