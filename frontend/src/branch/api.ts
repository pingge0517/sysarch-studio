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

// ---------------------------------------------------------------- merge (P2 T4)

export type Side = 'OURS' | 'THEIRS';
export type Strategy = 'POLARION_WINS' | 'MODEL_WINS';

export interface MergeConflict {
  elementId: string;
  /** null: the whole element (DELETE_MODIFY, DUPLICATE_ID). */
  field: string | null;
  kind: 'FIELD' | 'DELETE_MODIFY' | 'DUPLICATE_ID' | 'DANGLING';
  base: string | null;
  theirs: string | null;
  ours: string | null;
}

export interface Resolution {
  elementId: string;
  field: string | null;
  take: Side;
}

export interface MergeView {
  runId: string;
  mode: 'PREVIEW' | 'APPLY';
  status: string;
  strategy: Strategy;
  merged: number;
  conflicts: MergeConflict[];
  unresolved: MergeConflict[];
  autoResolved: { elementId: string; field: string; strategy: Strategy; value: string | null }[];
  operations: { elementId: string; op: 'ADD' | 'MODIFY' | 'DELETE'; fields: string[] }[];
  stats: { added: number; modified: number; deleted: number; conflicts: number; unresolved: number; autoResolved: number };
  applicable: boolean;
  autoMergeRefused: boolean;
  preMergeBaseline: string | null;
  errors: { message: string }[];
}

/** 409 from APPLY: refused, with the recomputed result when there is one. */
export class MergeRefused extends Error {
  constructor(message: string, readonly result: MergeView | null) {
    super(message);
  }
}

export async function merge(
  branchId: string,
  mode: 'PREVIEW' | 'APPLY',
  strategy: Strategy,
  resolutions: Resolution[],
  runId?: string,
): Promise<MergeView> {
  const response = await fetch(`/api/v1/branches/${encodeURIComponent(branchId)}/merge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode, strategy, runId, resolutions }),
  });
  if (response.status === 409) {
    const body = await response.json();
    throw new MergeRefused(body.message, body.result ?? null);
  }
  return json(response);
}
