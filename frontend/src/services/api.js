const API_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api';
let authToken = sessionStorage.getItem('finanzero_token') || '';
localStorage.removeItem('finanzero_token');
localStorage.removeItem('finanzero_user');
const pending = new Map();

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

export function setAuthToken(token) {
  authToken = token || '';
  if (authToken) sessionStorage.setItem('finanzero_token', authToken);
  else sessionStorage.removeItem('finanzero_token');
}

async function request(path, options = {}) {
  const tokenAtStart = authToken;
  const isFormData = options.body instanceof FormData;
  const headers = { ...(isFormData ? {} : { 'Content-Type': 'application/json' }), ...(options.headers || {}) };
  if (authToken) headers.Authorization = `Bearer ${authToken}`;

  try {
  const response = await fetch(`${API_URL}${path}`, { ...options, headers, cache: 'no-store', signal: options.signal || AbortSignal.timeout(60000) });
  if (!response.ok) {
    const text = await response.text();
    if (response.status === 401 && tokenAtStart === authToken) window.dispatchEvent(new Event('session-expired'));
    throw new ApiError(text || `Erro ${response.status}`, response.status);
  }
  if (response.status === 204) return null;
  return response.json();
  } catch (error) {
    if (error.name === 'AbortError' && options.signal?.aborted) throw error;
    const failure = error instanceof ApiError ? error : new ApiError('Não foi possível conectar. Confira a internet e tente novamente.', 0);
    if (tokenAtStart === authToken) window.dispatchEvent(new CustomEvent('api-error', { detail: failure.message }));
    throw failure;
  }
}

function mutate(path, method, body, options = {}) {
  const key = `${authToken}:${method}:${path}:${JSON.stringify(body)}`;
  if (pending.has(key)) return pending.get(key);
  const promise = request(path, { ...options, method, body: body === undefined ? undefined : JSON.stringify(body) }).finally(() => pending.delete(key));
  pending.set(key, promise);
  return promise;
}

window.addEventListener('unhandledrejection', event => {
  if (event.reason instanceof ApiError) event.preventDefault();
});

export const api = {
  setAuthToken,
  get: (path, options) => request(path, options),
  post: (path, body, options) => mutate(path, 'POST', body, options),
  put: (path, body) => mutate(path, 'PUT', body),
  upload: (path, formData) => request(path, { method: 'POST', body: formData }),
  delete: (path) => mutate(path, 'DELETE'),
};
