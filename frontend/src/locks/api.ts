// The lock API of the backend (P2 T1, /api/v1/locks). The user goes along as the sysarch_user cookie.

export interface Lock {
  id: string;
  projectId: string;
  elementId: string;
  ownerUser: string;
  leaseSeconds: number;
  acquiredAt: string;
  expiresAt: string;
}

export interface Conflict {
  elementId: string;
  ownerUser: string;
  expiresAt: string;
}

/** 423: someone else holds some of the elements; nothing was taken. */
export class LockedError extends Error {
  constructor(message: string, readonly conflicts: Conflict[]) {
    super(message);
  }
}

/** The lock is gone (404: expired) or not ours (403). */
export class LostLockError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function failure(response: Response): Promise<never> {
  let body: { message?: string; detail?: string; conflicts?: Conflict[] } = {};
  try {
    body = await response.json();
  } catch {
    // not JSON
  }
  const message = body.message ?? body.detail ?? `${response.status} ${response.statusText}`;
  if (response.status === 423) {
    throw new LockedError(message, body.conflicts ?? []);
  }
  throw new LostLockError(message, response.status);
}

export async function listLocks(project: string): Promise<Lock[]> {
  const response = await fetch(`/api/v1/locks?projectId=${encodeURIComponent(project)}`);
  return response.ok ? (await response.json()).locks : failure(response);
}

export async function acquireLocks(project: string, elementIds: string[], ownerUser: string, leaseSeconds?: number): Promise<Lock[]> {
  const response = await fetch('/api/v1/locks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectId: project, elementIds, ownerUser, leaseSeconds }),
  });
  return response.ok ? (await response.json()).locks : failure(response);
}

export async function renewLock(id: string): Promise<Lock> {
  const response = await fetch(`/api/v1/locks/${encodeURIComponent(id)}`, { method: 'PUT' });
  return response.ok ? response.json() : failure(response);
}

export async function releaseLock(id: string, force = false): Promise<void> {
  const response = await fetch(`/api/v1/locks/${encodeURIComponent(id)}${force ? '?force=true' : ''}`, { method: 'DELETE' });
  if (!response.ok) {
    await failure(response);
  }
}

export interface Session {
  id: string;
  userId: string;
  openedAt: string;
  lastActiveAt: string;
  clientInfo: string | null;
}

/** Who is editing the project (P2 T2): open sessions and the limit. */
export async function listSessions(project: string): Promise<{ limit: number; sessions: Session[] }> {
  const response = await fetch(`/api/v1/sessions?projectId=${encodeURIComponent(project)}`);
  return response.ok ? response.json() : failure(response);
}
