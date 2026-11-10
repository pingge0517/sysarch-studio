import { describe, expect, it } from 'vitest';
import type { ReportJob } from './api';
import { pending, statusLabel } from './api';

const job = (status: ReportJob['status'], cached = false): ReportJob => ({
  jobId: 'j',
  projectId: 'p',
  templateId: 'srs',
  template: '系统规格书（SRS）',
  baselineId: null,
  source: '当前模型',
  status,
  cached,
  requestedBy: 'alice',
  createdAt: '2026-11-10T02:00:00Z',
  finishedAt: null,
  pages: null,
  missing: [],
  gaps: [],
  error: null,
  files: {},
});

describe('report jobs (P2 T5)', () => {
  it('polls only queued and running jobs', () => {
    expect(pending(job('QUEUED'))).toBe(true);
    expect(pending(job('RUNNING'))).toBe(true);
    expect(pending(job('DONE'))).toBe(false);
    expect(pending(job('FAILED'))).toBe(false);
  });

  it('says when a result came from the cache', () => {
    expect(statusLabel(job('DONE', true))).toBe('已完成（24 小时内的缓存结果）');
    expect(statusLabel(job('EXPIRED'))).toBe('已过期（文件已清理）');
  });
});
