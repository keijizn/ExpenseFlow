import { test } from 'node:test';
import assert from 'node:assert/strict';

const storage = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key) }; };
globalThis.localStorage = storage();
globalThis.sessionStorage = storage();
globalThis.window = new EventTarget();
const { api } = await import('../src/services/api.js');

test('POSTs simultâneos iguais compartilham a mesma requisição', async () => {
  let resolve; let calls = 0;
  globalThis.fetch = () => { calls++; return new Promise(r => { resolve = r; }); };
  api.setAuthToken('same-user');
  const one = api.post('/transactions', { amount: 10 });
  const two = api.post('/transactions', { amount: 10 });
  assert.equal(one, two);
  resolve(new Response('{"id":1}', { status: 200 }));
  await Promise.all([one, two]);
  assert.equal(calls, 1);
});

test('401 de uma sessão antiga não encerra a nova sessão', async () => {
  let resolve; let expired = 0;
  const handler = () => expired++;
  window.addEventListener('session-expired', handler);
  globalThis.fetch = () => new Promise(r => { resolve = r; });
  api.setAuthToken('old');
  const pending = api.get('/accounts');
  api.setAuthToken('new');
  resolve(new Response('Sessão expirada', { status: 401 }));
  await assert.rejects(pending);
  assert.equal(expired, 0);
  assert.equal(sessionStorage.getItem('finanzero_token'), 'new');
  window.removeEventListener('session-expired', handler);
});

test('API envia token sem permitir cache de dados', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.cache, 'no-store');
    assert.equal(options.headers.Authorization, 'Bearer current');
    return new Response('[]', { status: 200 });
  };
  api.setAuthToken('current');
  await api.get('/accounts');
});
