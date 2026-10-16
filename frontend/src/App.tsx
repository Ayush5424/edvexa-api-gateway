import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { Layout } from './components/layout/Layout';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { ServiceRegistryPage } from './pages/ServiceRegistryPage';
import { ApiKeysPage } from './pages/ApiKeysPage';
import { RequestLogsPage } from './pages/RequestLogsPage';
import { ResiliencePage } from './pages/ResiliencePage';
import { PlaygroundPage } from './pages/PlaygroundPage';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route
              path="/"
              element={
                <Layout>
                  <OverviewPage />
                </Layout>
              }
            />
            <Route
              path="/services"
              element={
                <Layout>
                  <ServiceRegistryPage />
                </Layout>
              }
            />
            <Route
              path="/api-keys"
              element={
                <Layout>
                  <ApiKeysPage />
                </Layout>
              }
            />
            <Route
              path="/playground"
              element={
                <Layout>
                  <PlaygroundPage />
                </Layout>
              }
            />
            <Route
              path="/logs"
              element={
                <Layout>
                  <RequestLogsPage />
                </Layout>
              }
            />
            <Route
              path="/resilience"
              element={
                <Layout>
                  <ResiliencePage />
                </Layout>
              }
            />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
