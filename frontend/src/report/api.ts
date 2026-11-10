// The report API of the backend (P2 T5, /api/v1/reports).

export interface ReportJob {
  jobId: string;
  projectId: string;
  templateId: string;
  template: string;
  baselineId: string | null;
  source: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'EXPIRED';
  cached: boolean;
  requestedBy: string;
  createdAt: string;
  finishedAt: string | null;
  pages: number | null;
  missing: string[];
  gaps: string[];
  error: string | null;
  files: Partial<Record<'html' | 'docx' | 'pdf', string>>;
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

export async function listTemplates(): Promise<{ id: string; label: string }[]> {
  return json(await fetch('/api/v1/reports/templates'));
}

export async function submitReport(project: string, templateId: string, baselineId: string): Promise<ReportJob> {
  return json(
    await fetch('/api/v1/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId: project, templateId, baselineId: baselineId || null }),
    }),
  );
}

export async function getReport(jobId: string): Promise<ReportJob> {
  return json(await fetch(`/api/v1/reports/${encodeURIComponent(jobId)}`));
}

export async function listReports(project: string): Promise<ReportJob[]> {
  return json(await fetch(`/api/v1/reports?projectId=${encodeURIComponent(project)}`));
}

/** Whether a job is still being worked on (keep polling). */
export function pending(job: ReportJob): boolean {
  return job.status === 'QUEUED' || job.status === 'RUNNING';
}

const STATUS: Record<ReportJob['status'], string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  DONE: '已完成',
  FAILED: '失败',
  EXPIRED: '已过期（文件已清理）',
};

export function statusLabel(job: ReportJob): string {
  return STATUS[job.status] + (job.cached ? '（24 小时内的缓存结果）' : '');
}
