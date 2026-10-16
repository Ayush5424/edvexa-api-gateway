import React from 'react';
import { Sidebar } from './Sidebar';

export const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="dashboard-layout">
      <Sidebar />
      <div className="main-content">{children}</div>
    </div>
  );
};
