const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
let authToken = localStorage.getItem('finanzero_token') || '';

export function setAuthToken(token) {
  authToken = token || '';
  if (authToken) localStorage.setItem('finanzero_token', authToken);
  else localStorage.removeItem('finanzero_token');
}

async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  const headers = { ...(isFormData ? {} : { 'Content-Type': 'application/json' }), ...(options.headers || {}) };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Erro ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  setAuthToken,
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body) => request(path, { method: 'PUT', body: JSON.stringify(body) }),
  upload: (path, formData) => request(path, { method: 'POST', body: formData }),
  delete: (path) => request(path, { method: 'DELETE' }),
};
