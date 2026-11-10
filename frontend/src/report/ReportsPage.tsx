import { useCallback, useEffect, useState } from 'react';
import type { Baseline } from '../baseline/api';
import { listBaselines } from '../baseline/api';
import type { ReportJob } from './api';
import { getReport, listReports, listTemplates, pending, statusLabel, submitReport } from './api';

const POLL_MS = 1500;

function time(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '';
}

/**
 * Reports (P2 T5): pick a template and the data (the current model or a baseline), generate; the job is polled until
 * done, then HTML, Word and PDF can be downloaded. A report that cannot be made says what is missing; gaps in the
 * model are listed (the report has a "待补充" section).
 */
export function ReportsPage({ project }: { project: string }) {
  const [templates, setTemplates] = useState<{ id: string; label: string }[]>([]);
  const [baselines, setBaselines] = useState<Baseline[]>([]);
  const [template, setTemplate] = useState('srs');
  const [baseline, setBaseline] = useState('');
  const [jobs, setJobs] = useState<ReportJob[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [t, b, j] = await Promise.all([listTemplates(), listBaselines(project), listReports(project)]);
      setTemplates(t);
      setBaselines(b);
      setJobs(j);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project]);

  useEffect(() => {
    void reload();
  }, [reload]);

  // Poll the jobs that are still running.
  useEffect(() => {
    if (!jobs.some(pending)) {
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        setJobs(await Promise.all(jobs.map((j) => (pending(j) ? getReport(j.jobId) : Promise.resolve(j)))));
      } catch (e) {
        setError((e as Error).message);
      }
    }, POLL_MS);
    return () => window.clearTimeout(timer);
  }, [jobs]);

  const submit = async () => {
    setError(null);
    try {
      const job = await submitReport(project, template, baseline);
      setJobs((current) => [job, ...current.filter((j) => j.jobId !== job.jobId)]);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="reports">
      <section>
        <h2>生成报告</h2>
        <div className="row">
          <select value={template} onChange={(e) => setTemplate(e.target.value)} aria-label="模板">
            {templates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
          <select value={baseline} onChange={(e) => setBaseline(e.target.value)} aria-label="数据来源">
            <option value="">当前模型</option>
            {baselines.map((b) => (
              <option key={b.id} value={b.id}>
                基线：{b.label}（{time(b.createdAt)}）
              </option>
            ))}
          </select>
          <button type="button" onClick={() => void submit()}>
            生成
          </button>
        </div>
        <p className="hint">同一数据、同一模板 24 小时内重复生成会直接给出已有结果。文件保留 90 天。</p>
      </section>
      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}
      <section>
        <h2>报告</h2>
        {jobs.length === 0 && <p className="hint">还没有生成过报告。</p>}
        <table className="list">
          <tbody>
            {jobs.map((j) => (
              <tr key={j.jobId}>
                <td>{j.template}</td>
                <td>{j.source}</td>
                <td>{statusLabel(j)}</td>
                <td>{j.pages ? `${j.pages} 页` : ''}</td>
                <td>
                  {j.requestedBy}，{time(j.createdAt)}
                </td>
                <td className="actions">
                  {j.files.html && (
                    <a href={j.files.html} target="_blank" rel="noreferrer">
                      HTML
                    </a>
                  )}
                  {j.files.docx && <a href={j.files.docx}>Word</a>}
                  {j.files.pdf && <a href={j.files.pdf}>PDF</a>}
                </td>
                <td>
                  {j.missing.length > 0 && <span className="missing">缺少：{j.missing.join('；')}</span>}
                  {j.error && <span className="missing">{j.error}</span>}
                  {j.gaps.length > 0 && <span className="muted">待补充：{j.gaps.join('；')}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
