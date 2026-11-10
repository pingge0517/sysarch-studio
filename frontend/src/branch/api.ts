// The branch API of the backend (P2 T3, /api/v1/branches). The user goes along as the sysarch_user cookie.

export interface Branch {
  id: string;
  projectId: string;
  name: string;
  baseBaseline: string;
  baselineLabel: string | null;
  status: 'OPEN' | 'MERGED' | 'ABANDONED';
  createdBy: string;
  createdAt: string;
  /** The Sirius project the branch is edited in. */
  workspaceProjectId: string;
  workspaceName: string | null;
  changes: number;
}

export interface Change {
  elementId: string;
  op: 'ADD' | 'MODIFY' | 'DELETE';
  fields: string[];
  state: { id: string; type: string; name: string | null; layer: string | null } | null;
  createdAt: string;
}

async function json<T>(response: Response): Promise<T> {
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
  return response.json() as Promise<T>;
}

/** The trunk's branches; without a project, every open branch (to tell workspaces from trunks). */
export async function listBranches(project?: string): Promise<Branch[]> {
  return json(await fetch(project ? `/api/v1/branches?projectId=${encodeURIComponent(project)}` : '/api/v1/branches'));
}

export async function createBranch(project: string, name: string, baselineId: string): Promise<Branch> {
  return json(
    await fetch('/api/v1/branches', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId: project, name, baselineId }),
    }),
  );
}

export async function listChanges(branchId: string): Promise<Change[]> {
  return json(await fetch(`/api/v1/branches/${encodeURIComponent(branchId)}/changes`));
}

/** Switching away from a branch: the user's locks in it end and their session there closes (T3 step 3). */
export async function leaveBranch(branchId: string): Promise<number> {
  const body = await json<{ releasedLocks: number }>(await fetch(`/api/v1/branches/${encodeURIComponent(branchId)}/leave`, { method: 'POST' }));
  return body.releasedLocks;
}

export async function abandonBranch(branchId: string): Promise<Branch> {
  return json(await fetch(`/api/v1/branches/${encodeURIComponent(branchId)}`, { method: 'DELETE' }));
}
