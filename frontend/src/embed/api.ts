// Embedding in Polarion. A Polarion page frames the SysArch extension's /polarion/sysarch-sso/start?return=/?embed=1&…,
// which signs the user in here and lands on this page. It shows what belongs to the Polarion project (and Work Item)
// it was opened from, with links into the model, and stays inside the frame.
import { json } from '../identity/auth';
import { siriusUrl } from '../baseline/api';

export interface EmbedTarget {
  polarionProject: string;
  siriusProjectId: string;
  projectName: string;
  workItemId: string | null;
  elementId: string | null;
  linkState: string | null;
  objectId: string | null;
  path: string;
  note: string | null;
}

export interface EmbedParams {
  polarionProject: string;
  workItem: string;
}

export function embedParams(search: string): EmbedParams | null {
  const params = new URLSearchParams(search);
  if (params.get('embed') !== '1') {
    return null;
  }
  return { polarionProject: params.get('polarionProject') ?? '', workItem: params.get('workItem') ?? '' };
}

export async function fetchTarget(params: EmbedParams): Promise<EmbedTarget> {
  const query = new URLSearchParams({ polarionProject: params.polarionProject });
  if (params.workItem) {
    query.set('workItem', params.workItem);
  }
  return json<EmbedTarget>(await fetch(`/api/v1/embed/target?${query}`));
}

export interface EmbedLink {
  label: string;
  href: string;
}

/** Where the page can go from a target, all inside the frame: Sirius Web for the diagrams, this shell for the tables. */
export function links(target: EmbedTarget, sirius: (path: string) => string = siriusUrl): EmbedLink[] {
  const shell = (view: string, extra: Record<string, string> = {}) =>
    `/?${new URLSearchParams({ embed: '1', polarionProject: target.polarionProject, project: target.siriusProjectId, view, ...extra })}`;
  const result: EmbedLink[] = [];
  if (target.objectId) {
    result.push({ label: `打开模型元素 ${target.elementId}`, href: sirius(target.path) });
  }
  result.push({ label: '打开模型（Sirius Web）', href: sirius(`/projects/${target.siriusProjectId}/edit`) });
  result.push({ label: '分配矩阵', href: shell('matrix', { table: 'allocation-matrix' }) });
  result.push({ label: '需求追踪', href: shell('matrix', { table: 'requirement-trace' }) });
  result.push({ label: '基线与差异', href: shell('baselines') });
  result.push({ label: '报告', href: shell('reports') });
  return result;
}
