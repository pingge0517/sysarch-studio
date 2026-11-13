// Sign-in (P2 T8, replaces T1's typed name): the user pastes their Polarion personal access token, the backend asks
// Polarion who owns it and with which roles, and answers with a signed HttpOnly session cookie (per host, so Sirius
// Web on :8080 receives it too). The token is never kept in this page or anywhere else. In the backend's dev mode a
// typed name signs in instead (local and load tests).

export interface Me {
  mode: 'polarion' | 'dev';
  user: string | null;
  name: string | null;
  source: string | null;
  globalRoles: string[];
  projectRoles: Record<string, string[]>;
  /** P4 T8: reading without sign-in is allowed (dev mode, or SYSARCH_ANONYMOUS_READ=true); otherwise the application asks to sign in. */
  anonymousRead?: boolean;
}

/** Whether the application has to show the sign-in page before Sirius' application (P4 T8, finding F4). */
export function needsSignIn(me: Me | null): boolean {
  return me !== null && !me.user && me.anonymousRead !== true;
}

export interface AccessGrant {
  user: string | null;
  projectId: string | null;
  polarionProject: string | null;
  role: 'ADMIN' | 'ARCHITECT' | 'REVIEWER' | 'OBSERVER';
  roleLabel: string;
  permissions: string[];
}

export interface RoleMapping {
  id: number;
  polarionProject: string;
  polarionRole: string;
  toolRole: AccessGrant['role'];
  createdBy: string;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  projectId: string | null;
  actor: string;
  action: string;
  elementId: string | null;
  detail: string | null;
  createdAt: string;
}

export async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      detail = body.message ?? body.detail ?? detail;
    } catch {
      // not JSON
    }
    throw new Error(detail);
  }
  return (response.status === 204 ? undefined : response.json()) as Promise<T>;
}

const post = (body: unknown): RequestInit => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

export async function fetchMe(): Promise<Me> {
  return json(await fetch('/api/v1/auth/me'));
}

export async function login(token: string): Promise<Me> {
  return json(await fetch('/api/v1/auth/login', post({ token })));
}

export async function loginDev(user: string, roles: string[]): Promise<Me> {
  return json(await fetch('/api/v1/auth/login', post({ user, roles })));
}

export async function logout(): Promise<void> {
  return json(await fetch('/api/v1/auth/logout', { method: 'POST' }));
}

export async function fetchAccess(project: string): Promise<AccessGrant> {
  return json(await fetch(`/api/v1/access${project ? `?projectId=${encodeURIComponent(project)}` : ''}`));
}

export async function listMappings(): Promise<RoleMapping[]> {
  return json(await fetch('/api/v1/role-mappings'));
}

export async function putMapping(polarionProject: string, polarionRole: string, toolRole: string): Promise<RoleMapping> {
  return json(await fetch('/api/v1/role-mappings', post({ polarionProject, polarionRole, toolRole })));
}

export async function deleteMapping(id: number): Promise<void> {
  return json(await fetch(`/api/v1/role-mappings/${id}`, { method: 'DELETE' }));
}

/** P4 T1: a Polarion project bound to a model; source = table (admin page) or environment (configuration). */
export interface ProjectBinding {
  polarionProject: string;
  siriusProject: string;
  source: 'table' | 'environment';
  updatedBy: string | null;
  updatedAt: string | null;
}

export async function listProjectBindings(): Promise<ProjectBinding[]> {
  return json(await fetch('/api/v1/project-bindings'));
}

export async function putProjectBinding(polarionProject: string, siriusProject: string): Promise<ProjectBinding> {
  return json(
    await fetch(bindingUrl(polarionProject), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ siriusProject }),
    }),
  );
}

export async function deleteProjectBinding(polarionProject: string): Promise<void> {
  return json(await fetch(bindingUrl(polarionProject), { method: 'DELETE' }));
}

export function bindingUrl(polarionProject: string): string {
  return '/api/v1/project-bindings/' + encodeURIComponent(polarionProject);
}

export async function listAudit(project: string, action: string): Promise<AuditEntry[]> {
  const query = new URLSearchParams();
  if (project) query.set('projectId', project);
  if (action) query.set('action', action);
  return json(await fetch(`/api/v1/audit?${query}`));
}

/** What the header shows for a user in a project: "Rita（评审人）", or a sign-in hint. */
export function badge(me: Me | null, grant: AccessGrant | null): string {
  if (!me || !me.user) {
    // reading without sign-in only where the deployment allows it (P4 T8)
    return me && me.anonymousRead ? '未登录（只能查看）' : '未登录';
  }
  return grant ? `${me.name ?? me.user}（${grant.roleLabel}）` : (me.name ?? me.user);
}

/** Whether the role allows an action (the shell disables what it does not; the server checks again anyway). */
export function can(grant: AccessGrant | null, permission: string): boolean {
  return !!grant && grant.permissions.includes(permission);
}

/** "global:admin, sysarch/project_user" → the dev-mode role list. */
export function parseRoles(text: string): string[] {
  return text
    .split(/[,，\s]+/)
    .map((r) => r.trim())
    .filter((r) => r.length > 0);
}
