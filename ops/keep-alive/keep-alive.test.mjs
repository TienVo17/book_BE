import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import worker from './keep-alive.mjs';

const originalFetch = globalThis.fetch;
const originalLog = console.log;
afterEach(() => { globalThis.fetch = originalFetch; console.log = originalLog; });

test('checks both liveness and database-backed catalogue without credentials', async () => {
  const calls = [];
  const logs = [];
  console.log = entry => logs.push(JSON.parse(entry));
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return Response.json(url.endsWith('/health') ? { status: 'UP' } : []);
  };
  await worker.scheduled();
  assert.deepEqual(calls.map(call => call.url), [
    'https://book-be-jakn.onrender.com/health',
    'https://book-be-jakn.onrender.com/api/the-loai',
  ]);
  assert.ok(calls.every(call => call.options.method === 'GET' &&
    !call.options.headers.Authorization && !call.options.headers.Cookie));
  assert.ok(logs.every(entry => entry.ok && entry.status === 200));
  assert.equal(worker.fetch, undefined);
});

test('a failed liveness check still attempts catalogue readiness and fails honestly', async () => {
  let calls = 0;
  console.log = () => {};
  globalThis.fetch = async () => ++calls === 1
    ? new Response('unavailable', { status: 503 }) : Response.json([]);
  await assert.rejects(worker.scheduled(), /readiness check failed/);
  assert.equal(calls, 2);
});

test('HTTP 200 interstitial HTML is not reported as a healthy service', async () => {
  console.log = () => {};
  globalThis.fetch = async () => new Response('<html>Starting</html>');
  await assert.rejects(worker.scheduled(), /readiness check failed/);
});

test('a working liveness endpoint does not conceal a failed database request', async () => {
  console.log = () => {};
  globalThis.fetch = async url => url.endsWith('/health')
    ? Response.json({ status: 'UP' }) : Promise.reject(new TypeError('network'));
  await assert.rejects(worker.scheduled(), /readiness check failed/);
});
