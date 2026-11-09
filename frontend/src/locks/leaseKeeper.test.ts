import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Lock } from './api';
import { LostLockError } from './api';
import type { Held } from './leaseKeeper';
import { HEARTBEAT_MS, LeaseKeeper } from './leaseKeeper';
import { userCookie, userFromCookies } from '../identity/user';

const lock = (elementId: string, expiresAt = '2026-11-09T06:01:30Z'): Lock => ({
  id: `lock-${elementId}`,
  projectId: 'p',
  elementId,
  ownerUser: 'alice',
  leaseSeconds: 90,
  acquiredAt: '2026-11-09T06:00:00Z',
  expiresAt,
});

describe('LeaseKeeper (P2 T1: 45 s heartbeat, lost lock = read-only)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('renews every held lock every 45 seconds', async () => {
    const renew = vi.fn(async (id: string) => lock(id.replace('lock-', ''), '2026-11-09T06:02:15Z'));
    let state: Held[] = [];
    const keeper = new LeaseKeeper(renew, (h) => (state = h));
    keeper.hold([lock('PD-001'), lock('PD-002')]);

    await vi.advanceTimersByTimeAsync(HEARTBEAT_MS - 1);
    expect(renew).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(renew).toHaveBeenCalledTimes(2);
    expect(state.map((h) => h.lock.expiresAt)).toEqual(['2026-11-09T06:02:15Z', '2026-11-09T06:02:15Z']);
    keeper.stop();
  });

  it('marks a lock lost when the server refuses the renewal, and stops renewing it', async () => {
    const renew = vi.fn(async (id: string) => {
      if (id === 'lock-PD-002') {
        throw new LostLockError('lock lock-PD-002 has expired', 404);
      }
      return lock('PD-001');
    });
    let state: Held[] = [];
    const keeper = new LeaseKeeper(renew, (h) => (state = h));
    keeper.hold([lock('PD-001'), lock('PD-002')]);

    await vi.advanceTimersByTimeAsync(HEARTBEAT_MS);
    expect(state.map((h) => [h.lock.elementId, h.status])).toEqual([['PD-001', 'held'], ['PD-002', 'lost']]);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_MS);
    expect(renew.mock.calls.filter(([id]) => id === 'lock-PD-002')).toHaveLength(1);
    keeper.stop();
  });

  it('keeps a lock through a network error', async () => {
    const renew = vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    });
    let state: Held[] = [];
    const keeper = new LeaseKeeper(renew, (h) => (state = h));
    keeper.hold([lock('PD-001')]);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_MS);
    expect(state[0].status).toBe('held');
    keeper.stop();
  });

  it('stops the heartbeat when nothing is held', async () => {
    const renew = vi.fn(async () => lock('PD-001'));
    const keeper = new LeaseKeeper(renew, () => undefined);
    keeper.hold([lock('PD-001')]);
    keeper.forget('PD-001');
    await vi.advanceTimersByTimeAsync(HEARTBEAT_MS * 3);
    expect(renew).not.toHaveBeenCalled();
  });
});

describe('user cookie (temporary identity until T8)', () => {
  it('reads and writes the user, Chinese names included', () => {
    const cookie = userCookie(' 张三 ');
    expect(cookie).toContain('sysarch_user=%E5%BC%A0%E4%B8%89; Path=/');
    expect(userFromCookies(`a=1; ${cookie.split(';')[0]}; b=2`)).toBe('张三');
    expect(userFromCookies('a=1')).toBe('');
    expect(userCookie('')).toContain('Max-Age=0');
  });
});
