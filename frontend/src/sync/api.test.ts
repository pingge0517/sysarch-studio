import { afterEach, describe, expect, it, vi } from 'vitest';
import { bind, canPush, check, fetchWorkspace, pull, push, repair, unbind, verifyToken } from './api';
import type { SyncElement, SyncResult } from './api';

const result: SyncResult = { pushed: 1, pulled: 0, skipped: 0, conflicts: [], errors: [] };
const response = (body: unknown, status = 200, headers?: HeadersInit) => new Response(JSON.stringify(body), { status, headers });
const element: SyncElement = { id: 'PD-1', type: 'PartDefinition', name: 'Controller', layer: 'LOGICAL', workItemType: 'architecture_element', errors: [] };

afterEach(() => vi.unstubAllGlobals());

describe('browser Polarion synchronization', () => {
  it('resolves the model in an encoded path, without a PAT', async () => {
    const fetch = vi.fn().mockResolvedValue(response({ polarionProject: 'sysarch', elements: [] }));
    vi.stubGlobal('fetch', fetch);
    await fetchWorkspace('model / 1');
    expect(fetch).toHaveBeenCalledWith('/api/v1/projects/model%20%2F%201/sync-workspace');
  });

  it('checks the PAT identity without the SSO cookie and rejects another account', async () => {
    const fetch = vi.fn().mockResolvedValue(response({ user: 'bob' }));
    vi.stubGlobal('fetch', fetch);
    await expect(verifyToken('example-token', 'alice')).rejects.toThrow('令牌属于 bob');
    expect(fetch.mock.calls[0][1]).toMatchObject({ credentials: 'omit', headers: { Authorization: 'Bearer example-token' } });
  });

  it('refuses expired and empty PATs without treating the SSO login as authorization', async () => {
    const fetch = vi.fn().mockResolvedValue(response({ user: null }));
    vi.stubGlobal('fetch', fetch);
    await expect(verifyToken('', 'alice')).rejects.toThrow('请填写');
    expect(fetch).not.toHaveBeenCalled();
    await expect(verifyToken('expired', 'alice')).rejects.toThrow('无效或已过期');
  });

  it('accepts the current accounts PAT without changing the login session', async () => {
    const fetch = vi.fn().mockResolvedValue(response({ user: 'alice' }));
    vi.stubGlobal('fetch', fetch);
    await verifyToken(' example-token ', 'alice');
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/auth/me');
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer example-token');
  });

  it('sends explicit targets and different idempotency keys for preflight and actual writes', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response(result)));
    vi.stubGlobal('fetch', fetch);
    await push('sysarch', ['PD-1'], 'example-token', true);
    await push('sysarch', ['PD-1'], 'example-token', false);
    const a = fetch.mock.calls[0][1];
    const b = fetch.mock.calls[1][1];
    expect(JSON.parse(a.body)).toEqual({ projectId: 'sysarch', elementIds: ['PD-1'], dryRun: true });
    expect(JSON.parse(b.body).dryRun).toBe(false);
    expect(a.headers['Idempotency-Key']).toBeTruthy();
    expect(a.headers['Idempotency-Key']).not.toBe(b.headers['Idempotency-Key']);
    expect(b.headers.Authorization).toBe('Bearer example-token');
    await expect(push('sysarch', [], 'example-token', false)).rejects.toThrow('请先选择');
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('preserves field conflicts and never retries a failed write automatically', async () => {
    const conflict = { ...result, pushed: 0, conflicts: [{ elementId: 'PD-1', fields: ['name'] }] };
    const fetch = vi.fn().mockResolvedValue(response(conflict, 409));
    vi.stubGlobal('fetch', fetch);
    const actual = await push('sysarch', ['PD-1'], 'example-token', false);
    expect(actual).toEqual(conflict);
    expect(canPush(actual)).toBe(false);
    expect(canPush({ ...result, errors: [{ code: 'INVALID', message: 'invalid' }] })).toBe(false);
    expect(canPush(result)).toBe(true);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('surfaces rate limits and ordinary 409 errors rather than treating them as sync results', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response({}, 429, { 'Retry-After': '8' }))
      .mockResolvedValueOnce(response({ detail: 'model changed' }, 409));
    vi.stubGlobal('fetch', fetch);
    await expect(pull('sysarch', 'example-token')).rejects.toThrow('8 秒');
    await expect(push('sysarch', ['PD-1'], 'example-token', false)).rejects.toThrow('model changed');
  });

  it('binds using the configured type, unbinds without deleting a Work Item, and repairs exactly one issue', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response({ actions: [] })));
    vi.stubGlobal('fetch', fetch);
    await bind('sysarch', element, 'SYS-1');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ projectId: 'sysarch', elementId: 'PD-1', elementType: 'PartDefinition', workItemId: 'SYS-1', workItemType: 'architecture_element' });
    await unbind('PD/1');
    expect(fetch.mock.calls[1]).toEqual(['/api/v1/links/PD%2F1', { method: 'DELETE' }]);
    await repair('sysarch', 'R1:PD-1', 'example-token');
    expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({ projectId: 'sysarch', issueIds: ['R1:PD-1'] });
    await check('project / a', 'example-token');
    expect(fetch.mock.calls[3][0]).toBe('/api/v1/consistency/report?projectId=project+%2F+a');
  });
});
