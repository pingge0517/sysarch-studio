import type { Branch } from './api';

export interface Project {
  id: string;
  name: string;
}

/**
 * The project list of the shell (P2 T3): trunk projects only, since a branch workspace is chosen as a workspace of
 * its trunk, not as a project of its own.
 */
export function trunks(projects: Project[], openBranches: Branch[]): Project[] {
  const workspaces = new Set(openBranches.map((b) => b.workspaceProjectId));
  return projects.filter((p) => !workspaces.has(p.id));
}

/** The project the pages work on: the branch's workspace, or the trunk. */
export function effectiveProject(trunk: string, branch: Branch | undefined): string {
  return branch && branch.status === 'OPEN' ? branch.workspaceProjectId : trunk;
}

const OP_LABELS = { ADD: '新增', MODIFY: '修改', DELETE: '删除' } as const;

export function opLabel(op: keyof typeof OP_LABELS): string {
  return OP_LABELS[op];
}
