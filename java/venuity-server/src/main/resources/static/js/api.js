/**
 * Venuity API client for the static web UI.
 *
 * All requests target the modern camelCase API under /api. Access tokens are
 * stored in localStorage and refreshed automatically once on a 401 response.
 */

const TOKEN_KEYS = {
  access: 'venuity_access_token',
  refresh: 'venuity_refresh_token',
};

export class ApiError extends Error {
  constructor(status, code, message, details = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEYS.access);
}

export function getRefreshToken() {
  return localStorage.getItem(TOKEN_KEYS.refresh);
}

export function setTokens(access, refresh) {
  if (access) localStorage.setItem(TOKEN_KEYS.access, access);
  if (refresh) localStorage.setItem(TOKEN_KEYS.refresh, refresh);
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEYS.access);
  localStorage.removeItem(TOKEN_KEYS.refresh);
}

let refreshPromise = null;

async function refreshTokens() {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    const refresh = getRefreshToken();
    if (!refresh) return false;
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh }),
      });
      if (!response.ok) {
        clearTokens();
        return false;
      }
      const data = await response.json();
      if (!data || !data.access) {
        clearTokens();
        return false;
      }
      setTokens(data.access, data.refresh || refresh);
      return true;
    } catch (error) {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

async function parseBody(response) {
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json().catch(() => null);
  }
  const text = await response.text().catch(() => '');
  return text || null;
}

function firstDetail(details) {
  if (!details || typeof details !== 'object') return '';
  for (const [field, value] of Object.entries(details)) {
    const text = Array.isArray(value) ? value[0] : typeof value === 'string' ? value : '';
    if (!text) continue;
    return field === 'non_field_errors' ? text : `${field}: ${text}`;
  }
  return '';
}

function errorFromResponse(status, body) {
  let message = '';
  let code = '';
  let details = null;

  if (body && typeof body === 'object') {
    if (typeof body.message === 'string') message = body.message;
    else if (typeof body.error === 'string') message = body.error;
    else if (typeof body.detail === 'string') message = body.detail;

    if (typeof body.code === 'string') code = body.code;
    if (body.details && typeof body.details === 'object') details = body.details;
    else if (body.detail && typeof body.detail === 'object') details = body.detail;

    if (!message) message = firstDetail(details);
  } else if (typeof body === 'string') {
    message = body;
  }

  if (!message) {
    if (status === 403) message = 'You do not have permission to perform this action.';
    else if (status === 404) message = 'The requested item could not be found.';
    else if (status >= 500) message = 'The server encountered an error. Please try again.';
    else message = `Request failed (${status}).`;
  }

  return new ApiError(status, code || `HTTP_${status}`, message, details);
}

async function request(path, options = {}, config = {}) {
  const { auth = true, retry = true, responseType = 'json' } = config;
  const headers = new Headers(options.headers || {});

  if (options.body !== undefined && !(options.body instanceof FormData)) {
    if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  }

  if (auth) {
    const token = getAccessToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }

  let response;
  try {
    response = await fetch(path, { ...options, headers });
  } catch (error) {
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      'Unable to reach the server. Check your connection and try again.',
    );
  }

  if (response.status === 401 && auth && retry) {
    if (getRefreshToken()) {
      const refreshed = await refreshTokens();
      if (refreshed) {
        return request(path, options, { ...config, retry: false });
      }
    }
    clearTokens();
    window.dispatchEvent(new CustomEvent('venuity:unauthorized'));
    throw new ApiError(401, 'UNAUTHORIZED', 'Your session has expired. Please sign in again.');
  }

  if (responseType === 'blob') {
    if (!response.ok) {
      const body = await parseBody(response);
      throw errorFromResponse(response.status, body);
    }
    return response.blob();
  }

  const body = await parseBody(response);
  if (!response.ok) throw errorFromResponse(response.status, body);
  return body;
}

function rangeQuery(from, to) {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return params.toString();
}

export const api = {
  async login(username, password) {
    const data = await request(
      '/api/auth/login',
      { method: 'POST', body: JSON.stringify({ username, password }) },
      { auth: false, retry: false },
    );
    if (data && data.accessToken) {
      setTokens(data.accessToken, data.refreshToken);
    }
    return data;
  },

  logout() {
    const refresh = getRefreshToken();
    const pending = refresh
      ? request(
          '/api/auth/logout',
          { method: 'POST', body: JSON.stringify({ refresh }) },
          { retry: false },
        ).catch(() => null)
      : Promise.resolve(null);
    return pending.finally(() => clearTokens());
  },

  me() {
    return request('/api/auth/me');
  },

  branding() {
    return request('/api/settings/branding', {}, { auth: false, retry: false });
  },

  settings() {
    return request('/api/settings');
  },

  updateSettings(data) {
    return request('/api/settings', { method: 'PUT', body: JSON.stringify(data) });
  },

  health() {
    return request('/api/health', {}, { auth: false, retry: false });
  },

  stats() {
    return request('/api/transactions/stats');
  },

  transactions({ customerId, type, from, to, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams();
    if (customerId) params.set('customerId', customerId);
    if (type) params.set('type', type);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    params.set('page', String(page));
    params.set('size', String(size));
    return request(`/api/transactions?${params.toString()}`);
  },

  customers({ query, page = 0, size = 20, active } = {}) {
    const params = new URLSearchParams();
    if (query) params.set('query', query);
    if (active === true || active === false) params.set('active', String(active));
    params.set('page', String(page));
    params.set('size', String(size));
    return request(`/api/customers?${params.toString()}`);
  },

  customer(publicId) {
    return request(`/api/customers/${encodeURIComponent(publicId)}`);
  },

  customerByCode(code) {
    return request(`/api/customers/by-code/${encodeURIComponent(code)}`);
  },

  createCustomer(data) {
    return request('/api/customers', { method: 'POST', body: JSON.stringify(data) });
  },

  updateCustomer(publicId, data) {
    return request(`/api/customers/${encodeURIComponent(publicId)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  deactivateCustomer(publicId) {
    return request(`/api/customers/${encodeURIComponent(publicId)}`, { method: 'DELETE' });
  },

  reactivateCustomer(publicId) {
    return request(`/api/customers/${encodeURIComponent(publicId)}/reactivate`, {
      method: 'POST',
    });
  },

  qrBlob(publicId, size = 512) {
    return request(
      `/api/customers/${encodeURIComponent(publicId)}/qr.png?size=${encodeURIComponent(size)}`,
      {},
      { responseType: 'blob' },
    );
  },

  recharge(publicId, amount, note, idempotencyKey) {
    return request(`/api/customers/${encodeURIComponent(publicId)}/recharge`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ amount, note: note || null }),
    });
  },

  checkIn(code, idempotencyKey) {
    return request('/api/checkins', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ code }),
    });
  },

  analyticsSummary(from, to) {
    return request(`/api/analytics/summary?${rangeQuery(from, to)}`);
  },

  analyticsDaily(from, to) {
    return request(`/api/analytics/daily?${rangeQuery(from, to)}`);
  },

  analyticsHourly(from, to) {
    return request(`/api/analytics/hourly?${rangeQuery(from, to)}`);
  },

  users(params = {}) {
    const q = new URLSearchParams();
    if (params.query) q.set('query', params.query);
    if (params.role) q.set('role', params.role);
    if (params.status) q.set('status', params.status);
    if (params.page !== undefined) q.set('page', params.page);
    if (params.size !== undefined) q.set('size', params.size);
    if (params.sort) q.set('sort', params.sort);
    if (params.direction) q.set('direction', params.direction);
    return request(`/api/users?${q.toString()}`);
  },

  createUser(data) {
    return request('/api/users', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  user(id) {
    return request(`/api/users/${id}`);
  },

  updateUser(id, data) {
    return request(`/api/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  disableUser(id) {
    return request(`/api/users/${id}/disable`, { method: 'POST' });
  },

  enableUser(id) {
    return request(`/api/users/${id}/enable`, { method: 'POST' });
  },

  unlockUser(id) {
    return request(`/api/users/${id}/unlock`, { method: 'POST' });
  },

  resetPassword(id, data) {
    return request(`/api/users/${id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify(data || {}),
    });
  },

  forceLogoutUser(id) {
    return request(`/api/users/${id}/force-logout`, { method: 'POST' });
  },

  archiveUser(id) {
    return request(`/api/users/${id}`, { method: 'DELETE' });
  },

  restoreUser(id) {
    return request(`/api/users/${id}/restore`, { method: 'POST' });
  },

  userSessions(id) {
    return request(`/api/users/${id}/sessions`);
  },

  revokeUserSession(id, sid) {
    return request(`/api/users/${id}/sessions/${sid}`, { method: 'DELETE' });
  },

  userActivity(id) {
    return request(`/api/users/${id}/activity`);
  },

  bulkUsers(data) {
    return request('/api/users/bulk', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async importUsersPreview(file) {
    const fd = new FormData();
    fd.append('file', file);
    const token = getAccessToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch('/api/users/import/preview', {
      method: 'POST',
      headers,
      body: fd,
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Import preview failed');
    }
    return res.json();
  },

  async importUsers(file) {
    const fd = new FormData();
    fd.append('file', file);
    const token = getAccessToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch('/api/users/import', {
      method: 'POST',
      headers,
      body: fd,
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Import commit failed');
    }
    return res.json();
  },

  auditLogs(params = {}) {
    const q = new URLSearchParams();
    if (params.actor) q.set('actor', params.actor);
    if (params.target) q.set('target', params.target);
    if (params.action) q.set('action', params.action);
    if (params.success !== undefined) q.set('success', params.success);
    if (params.from) q.set('from', params.from);
    if (params.to) q.set('to', params.to);
    if (params.page !== undefined) q.set('page', params.page);
    if (params.size !== undefined) q.set('size', params.size);
    return request(`/api/audit-log?${q.toString()}`);
  },

  updateMe(data) {
    return request('/api/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  changePassword(data) {
    return request('/api/me/change-password', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  mySessions() {
    return request('/api/me/sessions');
  },

  revokeMySession(sid) {
    return request(`/api/me/sessions/${sid}`, { method: 'DELETE' });
  },

  logoutOthers() {
    return request('/api/me/logout-others', { method: 'POST' });
  },
};
