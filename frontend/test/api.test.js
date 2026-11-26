import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fetchClients, publishBroadcast } from '../src/api.js';

test('uses the session and Broadcast API contracts', async (context) => {
  const mockedFetch = context.mock.method(globalThis, 'fetch', async (path) =>
    Response.json(path === '/api/clients' ? [{ channel: 'broadcast' }] : { sent: 1 }));
  const controller = new AbortController();

  assert.deepEqual(await fetchClients(controller.signal), [{ channel: 'broadcast' }]);
  assert.deepEqual(await publishBroadcast('hello'), { sent: 1 });
  assert.deepEqual(mockedFetch.mock.calls.map((call) => call.arguments), [
    ['/api/clients', { signal: controller.signal }],
    ['/api/broadcast', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"message":"hello"}',
    }],
  ]);
});

test('preserves HTTP 400 for Broadcast validation feedback', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 400 }));
  await assert.rejects(publishBroadcast(' '), { status: 400 });
});
