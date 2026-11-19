/** Bounds how long an already displayed result may outlive a successful permission check. */
export const READ_ACCESS_TTL_MS = 30_000;
export const READ_ACCESS_CHECK_MS = 20_000;

export function watchReadAccess(check: (signal: AbortSignal) => Promise<void>, invalidate: () => void): () => void {
  let stopped = false;
  let request: AbortController | null = null;
  let retry: ReturnType<typeof setTimeout>;
  let expiry: ReturnType<typeof setTimeout>;
  function stop() {
    stopped = true; clearTimeout(retry); clearTimeout(expiry); request?.abort();
    window.removeEventListener('offline', fail);
    document.removeEventListener('visibilitychange', visibility);
  }
  function fail() { if (!stopped) { stop(); invalidate(); } }
  function visibility() { if (document.visibilityState !== 'visible') fail(); }
  async function verify() {
    if (stopped) return;
    request = new AbortController();
    try {
      await check(request.signal);
      if (stopped) return;
      clearTimeout(expiry); expiry = setTimeout(fail, READ_ACCESS_TTL_MS);
      retry = setTimeout(verify, READ_ACCESS_CHECK_MS);
    } catch { fail(); }
  }
  expiry = setTimeout(fail, READ_ACCESS_TTL_MS);
  retry = setTimeout(verify, READ_ACCESS_CHECK_MS);
  window.addEventListener('offline', fail);
  document.addEventListener('visibilitychange', visibility);
  if (!navigator.onLine || document.visibilityState !== 'visible') fail();
  return stop;
}
