// The SysML v2 import API of the backend (P2 T6, /api/v1/imports).

export interface ImportReport {
  projectId: string;
  projectName: string;
  packageName: string | null;
  total: number;
  counts: Record<string, number>;
  defaultLayer: string;
  defaultedLayer: string[];
  derivedPorts: string[];
  unmapped: string[];
  consistency: { constraintViolations: string[]; R6: string[]; notApplicable: string };
}

export interface ImportJob {
  id: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  sourceName: string | null;
  defaultLayer: string;
  projectId: string | null;
  report: ImportReport | null;
  /** Each "第 N 行：…": why nothing was imported. */
  problems: string[];
  error: string | null;
  requestedBy: string;
  createdAt: string;
  finishedAt: string | null;
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

/** P3 T8: SysML v2 text, or XMI (our own export or a minimal Cameo export). */
export type ImportFormat = 'sysmlv2' | 'xmi';

export async function submitImport(
  text: string,
  options: { projectName?: string; defaultLayer?: string; fileName?: string },
  format: ImportFormat = 'sysmlv2',
): Promise<ImportJob> {
  const query = new URLSearchParams();
  Object.entries(options).forEach(([k, v]) => v && query.set(k, v));
  return json(
    await fetch(`/api/v1/imports/${format}?${query}`, {
      method: 'POST',
      headers: { 'Content-Type': format === 'xmi' ? 'application/xml; charset=utf-8' : 'text/plain; charset=utf-8' },
      body: text,
    }),
  );
}

export async function getImport(id: string): Promise<ImportJob> {
  return json(await fetch(`/api/v1/imports/${encodeURIComponent(id)}`));
}

export async function recentImports(): Promise<ImportJob[]> {
  return json(await fetch('/api/v1/imports'));
}

export function importPending(job: ImportJob): boolean {
  return job.status === 'QUEUED' || job.status === 'RUNNING';
}

const KINDS: Record<string, string> = {
  'part def': '块',
  part: '部件',
  'port def': '接口定义',
  port: '端口',
  connection: '连接',
  requirement: '需求',
  allocation: '分配',
  'item flow': '项流',
  'occurrence def': '交互',
  lifeline: '生命线',
  message: '消息',
};

/** "块 6、部件 3、…" in a stable order. */
export function countsText(counts: Record<string, number>): string {
  return Object.keys(KINDS)
    .filter((k) => counts[k])
    .map((k) => `${KINDS[k]} ${counts[k]}`)
    .join('、');
}
