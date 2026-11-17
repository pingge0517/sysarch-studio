import { json } from '../identity/auth';
import type { Me } from '../identity/auth';
import type { ValidationError } from '../matrix/api';

export interface SyncElement {
  id: string;
  name: string;
  type: string;
  layer: string;
  workItemType: string;
  errors: ValidationError[];
}

export interface SyncWorkspace {
  siriusProjectId: string;
  polarionProject: string;
  elements: SyncElement[];
}

export interface Binding {
  elementId: string;
  workItemId: string;
  state: string;
}

export interface PooledItem {
  workItemId: string;
  workItemType: string;
  title: string;
}

export interface SyncResult {
  pushed: number;
  pulled: number;
  skipped: number;
  conflicts: { elementId: string; fields: string[] }[];
  errors: { elementId?: string | null; code: string; message: string }[];
}

export interface ConsistencyIssue {
  id: string;
  rule: string;
  severity: string;
  elementId: string | null;
  workItemId: string | null;
  message: string;
  repairable: boolean;
}

export interface ConsistencyReport {
  projectId: string;
  generatedAt: string;
  issues: ConsistencyIssue[];
}

export interface RepairResult {
  actions: { issueId: string; action: string; status: string; message: string }[];
}

export interface SyncStatus {
  lastPushAt: string | null;
  lastPullAt: string | null;
  lastCheckAt: string | null;
  schedule: { enabled: boolean; paused: boolean; lastError: string | null };
}

const query = (project: string) => new URLSearchParams({ projectId: project });
const headers = (token: string): HeadersInit => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' });

export async function fetchWorkspace(project: string): Promise<SyncWorkspace> {
  return json(await fetch(`/api/v1/projects/${encodeURIComponent(project)}/sync-workspace`));
}

/** Omit the session cookie so /me resolves the PAT itself, rather than the SSO account. No login or storage. */
export async function verifyToken(token: string, user: string): Promise<void> {
  if (!token.trim()) throw new Error('请填写当前账号的 Polarion 个人访问令牌');
  const owner = await json<Me>(await fetch('/api/v1/auth/me', { headers: headers(token.trim()), credentials: 'omit' }));
  if (!owner.user) throw new Error('Polarion 令牌无效或已过期');
  if (owner.user !== user) throw new Error(`令牌属于 ${owner.user}，请使用当前登录账号 ${user} 的令牌`);
}

export async function fetchBindings(project: string): Promise<Binding[]> {
  return json(await fetch(`/api/v1/links?${query(project)}`));
}

export async function fetchPool(project: string): Promise<PooledItem[]> {
  return json(await fetch(`/api/v1/sync/unbound?${query(project)}`));
}

export async function fetchStatus(project: string): Promise<SyncStatus> {
  return json(await fetch(`/api/v1/sync/status?${query(project)}`));
}

export async function bind(project: string, element: SyncElement, workItemId: string): Promise<void> {
  const response = await fetch('/api/v1/links', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectId: project, elementId: element.id, elementType: element.type, workItemId, workItemType: element.workItemType }),
  });
  if (!response.ok) await json(response);
}

export async function unbind(elementId: string): Promise<void> {
  const response = await fetch(`/api/v1/links/${encodeURIComponent(elementId)}`, { method: 'DELETE' });
  if (!response.ok) await json(response);
}

/** A 409 with a sync result is business data: show its conflicts, never discard or silently retry them. */
async function syncResult(response: Response): Promise<SyncResult> {
  if (response.status === 409) {
    const body = await response.clone().json().catch(() => null);
    if (body && Array.isArray(body.conflicts) && Array.isArray(body.errors)) return body as SyncResult;
  }
  if (response.status === 429) {
    throw new Error(`Polarion 请求过于频繁，请稍后重试${response.headers.get('Retry-After') ? `（${response.headers.get('Retry-After')} 秒）` : ''}`);
  }
  return json(response);
}

export async function push(project: string, ids: string[], token: string, dryRun: boolean): Promise<SyncResult> {
  if (!ids.length) throw new Error('请先选择要推送的元素');
  return syncResult(await fetch('/api/v1/sync/push', {
    method: 'POST', headers: { ...headers(token), 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ projectId: project, elementIds: ids, dryRun }),
  }));
}

export async function pull(project: string, token: string): Promise<SyncResult> {
  return syncResult(await fetch('/api/v1/sync/pull', {
    method: 'POST', headers: { ...headers(token), 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ projectId: project }),
  }));
}

export async function check(project: string, token: string): Promise<ConsistencyReport> {
  return json(await fetch(`/api/v1/consistency/report?${query(project)}`, { headers: headers(token) }));
}

export async function repair(project: string, issueId: string, token: string): Promise<RepairResult> {
  return json(await fetch('/api/v1/consistency/repair', {
    method: 'POST', headers: headers(token), body: JSON.stringify({ projectId: project, issueIds: [issueId] }),
  }));
}

export function canPush(result: SyncResult): boolean {
  return result.conflicts.length === 0 && result.errors.length === 0;
}
