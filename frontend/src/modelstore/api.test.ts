import { afterEach, expect, it, vi } from 'vitest';
import { listModels, readModel, readView } from './api';

afterEach(() => vi.unstubAllGlobals());
it('uses same-origin read routes, no cookie/storage and an abort signal', async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json({ models: [], page: 1, pageSize: 25, mayHaveMore: false }));
  vi.stubGlobal('fetch', fetch); const signal = new AbortController().signal;
  await listModels('a/b', 2, 'fixture-token', signal);
  expect(fetch).toHaveBeenCalledWith('/api/v1/model-store/projects/a%2Fb/models?page=2&pageSize=25', expect.objectContaining({ method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', signal }));
});
it('ignores the remote server URL and pins the selected model UUID', async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal('fetch', fetch);
  await readModel({ title: 'model', location: { server: 'https://untrusted.invalid', root: { project: 'alpha', workItem: 'A-1', revision: null }, modelUuid: 'uuid' } }, 'fixture-token', new AbortController().signal);
  expect(fetch.mock.calls[0][0]).toBe('/api/v1/model-store/projects/alpha/models/A-1/snapshot?modelUuid=uuid');
});
it('permission failures reject instead of returning an empty successful model', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ detail: 'DENIED' }, { status: 403 })));
  await expect(listModels('alpha', 1, 'fixture-token', new AbortController().signal)).rejects.toThrow('DENIED');
});
it('requests a read projection through the same-origin endpoint with no write method', async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal('fetch', fetch);
  await readView({ title: 'model', location: { server: 'https://untrusted.invalid', root: { project: 'alpha', workItem: 'A-1', revision: null }, modelUuid: 'uuid' } }, 'allocation-matrix', 'fixture', new AbortController().signal);
  expect(fetch.mock.calls[0][0]).toBe('/api/v1/model-store/projects/alpha/models/A-1/views/allocation-matrix?modelUuid=uuid');
  expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'GET', credentials: 'omit', cache: 'no-store' });
});
