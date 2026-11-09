import type { Lock } from './api';
import { LostLockError } from './api';

/** Heartbeat interval (P2 §2.2: every 45 s, half the default 90 s lease). */
export const HEARTBEAT_MS = 45_000;

export interface Held {
  lock: Lock;
  /** held: renewed on time; lost: expired or taken, the element is read-only for us. */
  status: 'held' | 'lost';
  reason?: string;
}

/**
 * Keeps this page's locks alive (P2 T1 step 4): renews every held lock every 45 s; a renewal the server refuses (404
 * expired, 403 not ours) marks the lock lost, which the page shows as read-only. A network error keeps it held: the
 * next heartbeat tries again, and if the lease ran out meanwhile the server says so.
 */
export class LeaseKeeper {
  private readonly held = new Map<string, Held>();

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly renew: (id: string) => Promise<Lock>,
    private readonly onChange: (held: Held[]) => void,
    private readonly intervalMs = HEARTBEAT_MS,
  ) {}

  hold(locks: Lock[]): void {
    locks.forEach((lock) => this.held.set(lock.elementId, { lock, status: 'held' }));
    if (this.timer === null && this.held.size > 0) {
      this.timer = setInterval(() => void this.beat(), this.intervalMs);
    }
    this.publish();
  }

  forget(elementId: string): void {
    this.held.delete(elementId);
    if (this.held.size === 0) {
      this.stop();
    }
    this.publish();
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  async beat(): Promise<void> {
    const current = [...this.held.values()].filter((h) => h.status === 'held');
    await Promise.all(
      current.map(async (h) => {
        try {
          const renewed = await this.renew(h.lock.id);
          this.held.set(h.lock.elementId, { lock: renewed, status: 'held' });
        } catch (e) {
          if (e instanceof LostLockError) {
            this.held.set(h.lock.elementId, { lock: h.lock, status: 'lost', reason: e.message });
          }
        }
      }),
    );
    this.publish();
  }

  private publish(): void {
    this.onChange([...this.held.values()].sort((a, b) => a.lock.elementId.localeCompare(b.lock.elementId)));
  }
}
