import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { watchReadAccess } from './readAccess';

let browser: EventTarget;
let page: EventTarget & { visibilityState: string };
beforeEach(() => {
  vi.useFakeTimers(); browser = new EventTarget(); page = Object.assign(new EventTarget(), { visibilityState: 'visible' });
  vi.stubGlobal('window', browser); vi.stubGlobal('document', page); vi.stubGlobal('navigator', { onLine: true });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });
it('rechecks authorization and invalidates displayed data when access is denied', async () => {
  const check = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(Error('403'));
  const invalidate = vi.fn(); watchReadAccess(check, invalidate);
  await vi.advanceTimersByTimeAsync(20_000); expect(check).toHaveBeenCalledTimes(1); expect(invalidate).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(20_000); expect(invalidate).toHaveBeenCalledOnce();
  await vi.advanceTimersByTimeAsync(60_000); expect(check).toHaveBeenCalledTimes(2);
});
it('expires and aborts an unresponsive check without accepting a late success', async () => {
  let signal: AbortSignal | undefined; let finish: (() => void) | undefined;
  const invalidate = vi.fn();
  watchReadAccess((value) => { signal = value; return new Promise<void>((resolve) => { finish = resolve; }); }, invalidate);
  await vi.advanceTimersByTimeAsync(30_000); expect(signal?.aborted).toBe(true); expect(invalidate).toHaveBeenCalledOnce();
  finish?.(); await vi.advanceTimersByTimeAsync(60_000); expect(invalidate).toHaveBeenCalledOnce();
});
it('clears on disconnect and on hiding the page', () => {
  for (const event of ['offline', 'visibilitychange']) {
    const invalidate = vi.fn(); watchReadAccess(vi.fn(), invalidate);
    if (event === 'offline') browser.dispatchEvent(new Event(event));
    else { page.visibilityState = 'hidden'; page.dispatchEvent(new Event(event)); }
    expect(invalidate).toHaveBeenCalledOnce();
  }
});
it('unmount cancels pending work and removes event listeners', async () => {
  let signal: AbortSignal | undefined;
  const check = vi.fn((value: AbortSignal) => { signal = value; return new Promise<void>(() => {}); }), invalidate = vi.fn();
  const stop = watchReadAccess(check, invalidate);
  await vi.advanceTimersByTimeAsync(20_000);
  stop(); browser.dispatchEvent(new Event('offline')); page.visibilityState = 'hidden'; page.dispatchEvent(new Event('visibilitychange'));
  await vi.advanceTimersByTimeAsync(60_000); expect(check).toHaveBeenCalledOnce(); expect(signal?.aborted).toBe(true); expect(invalidate).not.toHaveBeenCalled();
});
it('does not authorize an initially offline or hidden page', () => {
  vi.stubGlobal('navigator', { onLine: false }); const offline = vi.fn(); watchReadAccess(vi.fn(), offline); expect(offline).toHaveBeenCalledOnce();
  vi.stubGlobal('navigator', { onLine: true }); page.visibilityState = 'hidden';
  const hidden = vi.fn(); watchReadAccess(vi.fn(), hidden); expect(hidden).toHaveBeenCalledOnce();
});
