import test from 'node:test';
import assert from 'node:assert/strict';
import { probe } from '../../../tools/probe-polarion-store.mjs';

test('probe only sends GET to fixed same-origin endpoints and redacts credentials', async () => {
  const calls = [];
  const result = await probe({ baseUrl: 'http://localhost/polarion', token: 'private-fixture-token', fetchImpl: async (url, options) => {
    calls.push({ url, options });
    return new Response('{"secret":"not-for-report"}', { headers: { 'Content-Type': 'application/json' } });
  } });
  assert.equal(calls.length, 2);
  assert.ok(calls.every(c => c.options.method === 'GET' && c.options.redirect === 'error'));
  assert.ok(calls.every(c => c.url.startsWith('http://localhost/polarion/rest/v1/')));
  assert.equal(result.writesAllowed, false);
  assert.equal(result.atomicityVerified, false);
  assert.ok(!JSON.stringify(result).includes('private-fixture-token'));
  assert.ok(!JSON.stringify(result).includes('not-for-report'));
});

test('HTML login and forbidden responses do not count as REST capability', async () => {
  let request = 0;
  const result = await probe({ baseUrl: 'http://localhost/polarion', fetchImpl: async () => ++request === 1
    ? new Response('<html>login</html>', { headers: { 'Content-Type': 'text/html' } })
    : new Response('', { status: 403 }) });
  assert.ok(result.results.every(r => !r.accessible));
  assert.equal(result.writesAllowed, false);
});

test('reject insecure remote targets and embedded credentials without any request', async () => {
  for (const baseUrl of ['http://example.com/polarion', 'https://user:secret@example.com/polarion', 'file:///private']) {
    await assert.rejects(probe({ baseUrl, fetchImpl: () => assert.fail('network must not be called') }));
  }
});

test('failure does not leak a transport exception containing credentials', async () => {
  const result = await probe({ baseUrl: 'https://example.com/polarion', fetchImpl: () => { throw new Error('private-token'); } });
  assert.ok(result.results.every(r => !r.accessible));
  assert.ok(!JSON.stringify(result).includes('private-token'));
});
