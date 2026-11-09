import { useCallback, useEffect, useRef, useState } from 'react';
import type { Conflict, Lock } from './api';
import { LockedError, acquireLocks, listLocks, releaseLock, renewLock } from './api';
import type { Held } from './leaseKeeper';
import { LeaseKeeper } from './leaseKeeper';

const REFRESH_MS = 10_000;

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString();
}

/**
 * Edit locks (P2 T1 step 4): take locks on elements before a longer edit, keep them with the 45 s heartbeat while
 * this page is open, see who holds what. A lock this page lost (expired, or released by an administrator) turns the
 * element read-only here with the reason. Editing in Sirius Web takes and renews a 90 s lease by itself; this page
 * is for holding elements longer and for seeing the holders.
 */
export function LocksPage({ project, user }: { project: string; user: string }) {
  const [input, setInput] = useState('');
  const [lease, setLease] = useState(90);
  const [held, setHeld] = useState<Held[]>([]);
  const [all, setAll] = useState<Lock[]>([]);
  const [conflicts, setConflicts] = useState<Conflict[]>([]);
  const [error, setError] = useState<string | null>(null);
  const keeper = useRef<LeaseKeeper | null>(null);

  useEffect(() => {
    keeper.current = new LeaseKeeper(renewLock, setHeld);
    return () => keeper.current?.stop();
  }, [project, user]);

  const refresh = useCallback(async () => {
    try {
      setAll(await listLocks(project));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project]);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const acquire = async () => {
    const ids = input.split(/[\s,，;；]+/).map((s) => s.trim()).filter(Boolean);
    setConflicts([]);
    setError(null);
    try {
      keeper.current?.hold(await acquireLocks(project, ids, user, lease));
      setInput('');
    } catch (e) {
      if (e instanceof LockedError) {
        setConflicts(e.conflicts);
      } else {
        setError((e as Error).message);
      }
    }
    void refresh();
  };

  const release = async (lock: Lock, force = false) => {
    try {
      await releaseLock(lock.id, force);
      keeper.current?.forget(lock.elementId);
    } catch (e) {
      setError((e as Error).message);
    }
    void refresh();
  };

  const others = all.filter((l) => l.ownerUser !== user);

  if (!user) {
    return <p className="hint">先在右上角填写你的名字：锁记在名字下面（T8 换成 Polarion 登录）。</p>;
  }

  return (
    <div className="locks">
      <section>
        <h2>申请锁</h2>
        <p className="hint">元素 id，用空格或逗号分隔，一次最多 50 个；全部拿到或全部不拿。页面开着就每 45 秒续租一次。</p>
        <div className="row">
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="PD-001 PD-002" aria-label="元素 id" />
          <label>
            租期
            <select value={lease} onChange={(e) => setLease(Number(e.target.value))}>
              {[30, 90, 180, 300].map((s) => (
                <option key={s} value={s}>
                  {s} 秒
                </option>
              ))}
            </select>
          </label>
          <button type="button" onClick={() => void acquire()} disabled={!input.trim()}>
            申请
          </button>
        </div>
        {conflicts.length > 0 && (
          <div className="notice refusal" role="alert">
            <strong>未拿到锁（一个也没拿）：</strong>
            {conflicts.map((c) => `${c.elementId} 已被 ${c.ownerUser} 锁定（至 ${time(c.expiresAt)}）`).join('；')}
          </div>
        )}
        {error && (
          <div className="notice error" role="alert">
            {error}
          </div>
        )}
      </section>

      <section>
        <h2>我的锁（{user}）</h2>
        {held.length === 0 && <p className="hint">本页没有持有的锁。</p>}
        <table className="list">
          <tbody>
            {held.map((h) => (
              <tr key={h.lock.elementId} className={h.status === 'lost' ? 'lost' : undefined}>
                <td>{h.lock.elementId}</td>
                <td>
                  {h.status === 'held' ? `持有，至 ${time(h.lock.expiresAt)}` : `已失去，只读：${h.reason ?? ''}`}
                </td>
                <td>
                  {h.status === 'held' ? (
                    <button type="button" onClick={() => void release(h.lock)}>
                      释放
                    </button>
                  ) : (
                    <button type="button" onClick={() => keeper.current?.forget(h.lock.elementId)}>
                      移除
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h2>其他人持有的锁</h2>
        {others.length === 0 && <p className="hint">没有。</p>}
        <table className="list">
          <tbody>
            {others.map((l) => (
              <tr key={l.id}>
                <td>{l.elementId}</td>
                <td>
                  {l.ownerUser}，至 {time(l.expiresAt)}
                </td>
                <td>
                  <button type="button" title="管理员强制释放，会写入审计日志" onClick={() => void release(l, true)}>
                    强制释放
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
