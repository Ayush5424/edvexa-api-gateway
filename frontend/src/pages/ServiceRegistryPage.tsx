import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Edit2, Trash2, Power, CheckCircle, ExternalLink } from 'lucide-react';
import { Header } from '../components/layout/Header';
import { StatusBadge } from '../components/common/StatusBadge';
import { Modal } from '../components/common/Modal';
import { Alert } from '../components/common/Alert';
import { gatewayService } from '../services/gatewayService';
import { ServiceResponse, ServiceRegistrationRequest, ServiceUpdateRequest } from '../types/services';
import { ApiError } from '../services/apiClient';

export const ServiceRegistryPage: React.FC = () => {
  const [services, setServices] = useState<ServiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Modals state
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [selectedService, setSelectedService] = useState<ServiceResponse | null>(null);

  // Form states
  const [formData, setFormData] = useState<ServiceRegistrationRequest>({
    name: '',
    baseUrl: '',
    pathPrefix: '',
    connectionTimeoutMs: 3000,
    responseTimeoutMs: 5000,
    enabled: true,
  });

  const [editData, setEditData] = useState<ServiceUpdateRequest>({
    name: '',
    baseUrl: '',
    pathPrefix: '',
    connectionTimeoutMs: 3000,
    responseTimeoutMs: 5000,
    enabled: true,
  });

  const fetchServices = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    try {
      const data = await gatewayService.getAllServices();
      setServices(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch registered services';
      setAlert({ type: 'error', message: msg });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchServices();
  }, [fetchServices]);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.baseUrl.trim() || !formData.pathPrefix.trim()) {
      setAlert({ type: 'error', message: 'Name, Base URL, and Path Prefix are required.' });
      return;
    }

    try {
      const created = await gatewayService.registerService({
        name: formData.name.trim(),
        baseUrl: formData.baseUrl.trim(),
        pathPrefix: formData.pathPrefix.trim(),
        connectionTimeoutMs: Number(formData.connectionTimeoutMs) || 3000,
        responseTimeoutMs: Number(formData.responseTimeoutMs) || 5000,
        enabled: formData.enabled,
      });
      setAlert({ type: 'success', message: `Service '${created.name}' registered successfully.` });
      setIsRegisterOpen(false);
      setFormData({
        name: '',
        baseUrl: '',
        pathPrefix: '',
        connectionTimeoutMs: 3000,
        responseTimeoutMs: 5000,
        enabled: true,
      });
      fetchServices();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to register service';
      setAlert({ type: 'error', message: msg });
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedService) return;

    try {
      const updated = await gatewayService.updateService(selectedService.id, editData);
      setAlert({ type: 'success', message: `Service '${updated.name}' updated successfully.` });
      setIsEditOpen(false);
      setSelectedService(null);
      fetchServices();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to update service';
      setAlert({ type: 'error', message: msg });
    }
  };

  const handleToggleStatus = async (service: ServiceResponse) => {
    try {
      const nextStatus = !service.enabled;
      await gatewayService.toggleServiceStatus(service.id, nextStatus);
      setAlert({
        type: 'success',
        message: `Service '${service.name}' ${nextStatus ? 'enabled' : 'disabled'} successfully.`,
      });
      fetchServices();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to toggle status';
      setAlert({ type: 'error', message: msg });
    }
  };

  const handleDelete = async () => {
    if (!selectedService) return;
    try {
      await gatewayService.deleteService(selectedService.id);
      setAlert({ type: 'success', message: `Service '${selectedService.name}' deleted successfully.` });
      setIsDeleteOpen(false);
      setSelectedService(null);
      fetchServices();
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : 'Failed to delete service';
      setAlert({ type: 'error', message: msg });
    }
  };

  const openEditModal = (service: ServiceResponse) => {
    setSelectedService(service);
    setEditData({
      name: service.name,
      baseUrl: service.baseUrl,
      pathPrefix: service.pathPrefix,
      connectionTimeoutMs: service.connectionTimeoutMs,
      responseTimeoutMs: service.responseTimeoutMs,
      enabled: service.enabled,
    });
    setIsEditOpen(true);
  };

  const openDeleteModal = (service: ServiceResponse) => {
    setSelectedService(service);
    setIsDeleteOpen(true);
  };

  return (
    <>
      <Header
        title="Service Registry"
        subtitle="Configure downstream routing destinations, path prefixes, and timeouts"
        onRefresh={() => fetchServices(true)}
        isRefreshing={refreshing}
      />

      <div className="page-container">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600 }}>Registered Services</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              Dynamic routing routes requests based on the longest matching path prefix.
            </p>
          </div>
          <button onClick={() => setIsRegisterOpen(true)} className="btn btn-primary">
            <Plus size={16} />
            <span>Register New Service</span>
          </button>
        </div>

        <div className="card" style={{ padding: 0 }}>
          <div className="table-container" style={{ border: 'none' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Service Name</th>
                  <th>Path Prefix</th>
                  <th>Target Base URL</th>
                  <th>Status</th>
                  <th>Timeouts (Conn / Resp)</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {services.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      {loading ? 'Loading service registry...' : 'No services registered yet. Click "Register New Service" to begin.'}
                    </td>
                  </tr>
                ) : (
                  services.map((svc) => (
                    <tr key={svc.id}>
                      <td style={{ fontWeight: 600 }}>{svc.name}</td>
                      <td>
                        <span className="code-badge">{svc.pathPrefix}</span>
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}>
                        <a
                          href={svc.baseUrl}
                          target="_blank"
                          rel="noreferrer"
                          style={{ color: 'var(--color-primary)', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}
                        >
                          {svc.baseUrl}
                          <ExternalLink size={12} />
                        </a>
                      </td>
                      <td>
                        <StatusBadge status={svc.enabled ? 'ACTIVE' : 'DISABLED'} />
                      </td>
                      <td style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                        {svc.connectionTimeoutMs}ms / {svc.responseTimeoutMs}ms
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                          <button
                            onClick={() => handleToggleStatus(svc)}
                            className="btn btn-secondary btn-sm"
                            title={svc.enabled ? 'Disable service' : 'Enable service'}
                          >
                            <Power size={14} color={svc.enabled ? '#10b981' : '#94a3b8'} />
                          </button>
                          <button
                            onClick={() => openEditModal(svc)}
                            className="btn btn-secondary btn-sm"
                            title="Edit configuration"
                          >
                            <Edit2 size={14} />
                          </button>
                          <button
                            onClick={() => openDeleteModal(svc)}
                            className="btn btn-secondary btn-sm"
                            title="Delete service"
                            style={{ color: 'var(--color-danger)' }}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Register Service Modal */}
      <Modal isOpen={isRegisterOpen} onClose={() => setIsRegisterOpen(false)} title="Register Downstream Service">
        <form onSubmit={handleRegister}>
          <div className="form-group">
            <label className="form-label" htmlFor="register-service-name">Service Name</label>
            <input
              id="register-service-name"
              type="text"
              className="form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="e.g. order-service"
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="register-path-prefix">Path Prefix</label>
            <input
              id="register-path-prefix"
              type="text"
              className="form-input"
              value={formData.pathPrefix}
              onChange={(e) => setFormData({ ...formData, pathPrefix: e.target.value })}
              placeholder="e.g. /orders"
              required
            />
            <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Gateway forwards requests matching `/gateway/prefix` to this target.
            </small>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="register-base-url">Target Base URL</label>
            <input
              id="register-base-url"
              type="url"
              className="form-input"
              value={formData.baseUrl}
              onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
              placeholder="http://localhost:8080/api/v1/simulated/orders"
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="register-conn-timeout">Connection Timeout (ms)</label>
              <input
                id="register-conn-timeout"
                type="number"
                min={500}
                max={30000}
                className="form-input"
                value={formData.connectionTimeoutMs}
                onChange={(e) => setFormData({ ...formData, connectionTimeoutMs: Number(e.target.value) })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="register-resp-timeout">Response Timeout (ms)</label>
              <input
                id="register-resp-timeout"
                type="number"
                min={500}
                max={60000}
                className="form-input"
                value={formData.responseTimeoutMs}
                onChange={(e) => setFormData({ ...formData, responseTimeoutMs: Number(e.target.value) })}
                required
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={() => setIsRegisterOpen(false)} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Register Service
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit Service Modal */}
      <Modal isOpen={isEditOpen} onClose={() => setIsEditOpen(false)} title={`Edit Service: ${selectedService?.name}`}>
        <form onSubmit={handleUpdate}>
          <div className="form-group">
            <label className="form-label">Service Name</label>
            <input
              type="text"
              className="form-input"
              value={editData.name || ''}
              onChange={(e) => setEditData({ ...editData, name: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Path Prefix</label>
            <input
              type="text"
              className="form-input"
              value={editData.pathPrefix || ''}
              onChange={(e) => setEditData({ ...editData, pathPrefix: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Target Base URL</label>
            <input
              type="url"
              className="form-input"
              value={editData.baseUrl || ''}
              onChange={(e) => setEditData({ ...editData, baseUrl: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Connection Timeout (ms)</label>
              <input
                type="number"
                className="form-input"
                value={editData.connectionTimeoutMs || 3000}
                onChange={(e) => setEditData({ ...editData, connectionTimeoutMs: Number(e.target.value) })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Response Timeout (ms)</label>
              <input
                type="number"
                className="form-input"
                value={editData.responseTimeoutMs || 5000}
                onChange={(e) => setEditData({ ...editData, responseTimeoutMs: Number(e.target.value) })}
                required
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={() => setIsEditOpen(false)} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Save Changes
            </button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal isOpen={isDeleteOpen} onClose={() => setIsDeleteOpen(false)} title="Confirm Service Deletion">
        <div>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-main)', marginBottom: '1rem' }}>
            Are you sure you want to delete service{' '}
            <strong>{selectedService?.name}</strong>?
          </p>
          <p style={{ fontSize: '0.8125rem', color: 'var(--color-danger)' }}>
            This action cannot be undone. Inbound gateway requests matching path prefix{' '}
            <code>{selectedService?.pathPrefix}</code> will fail with HTTP 404 Not Found.
          </p>
          <div className="modal-footer">
            <button type="button" onClick={() => setIsDeleteOpen(false)} className="btn btn-secondary">
              Cancel
            </button>
            <button type="button" onClick={handleDelete} className="btn btn-danger">
              Delete Service
            </button>
          </div>
        </div>
      </Modal>
    </>
  );
};
