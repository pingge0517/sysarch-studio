import { useEffect, useState } from 'react';
import { siriusUrl } from '../baseline/api';
import type { ImportJob } from './api';
import { countsText, getImport, importPending, recentImports, submitImport } from './api';

const LAYERS = ['OPERATIONAL', 'SYSTEM', 'LOGICAL', 'PHYSICAL', 'EPBS'];

/**
 * SysML v2 import (P2 T6): paste text or pick a .sysml file; the import creates a new project. Unsupported syntax
 * fails the whole import with every problem and its line; otherwise the report lists what was imported, which
 * elements took the default layer, what could not be placed (UNMAPPED) and the consistency check.
 */
export function ImportPage({ onOpen }: { onOpen: (projectId: string) => void }) {
  const [text, setText] = useState('');
  const [fileName, setFileName] = useState('');
  const [projectName, setProjectName] = useState('');
  const [defaultLayer, setDefaultLayer] = useState('LOGICAL');
  const [job, setJob] = useState<ImportJob | null>(null);
  const [recent, setRecent] = useState<ImportJob[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    recentImports().then(setRecent, () => setRecent([]));
  }, [job?.status]);

  useEffect(() => {
    if (!job || !importPending(job)) {
      return;
    }
    const timer = window.setTimeout(() => getImport(job.id).then(setJob, (e: Error) => setError(e.message)), 800);
    return () => window.clearTimeout(timer);
  }, [job]);

  const pick = async (file: File | undefined) => {
    if (file) {
      setText(await file.text());
      setFileName(file.name);
    }
  };

  const submit = async () => {
    setError(null);
    try {
      setJob(await submitImport(text, { projectName: projectName.trim(), defaultLayer, fileName }));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const report = job?.report;
  return (
    <div className="imports">
      <section>
        <h2>导入 SysML v2 文本</h2>
        <p className="hint">只收结构：块、部件、接口定义、端口、连接、需求；SysArch 自己导出的元数据和说明会原样还原。遇到参数、动作、状态机等不支持的语法，整批不导入并逐行列出。导入总是新建一个项目，不会创建 Polarion Work Item。</p>
        <div className="row">
          <input type="file" accept=".sysml,.txt,text/plain" onChange={(e) => void pick(e.target.files?.[0])} aria-label="选择文件" />
          <input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="新项目名称（可选）" aria-label="新项目名称" />
          <label title="文本里没有层信息的元素放在这一层">
            默认层
            <select value={defaultLayer} onChange={(e) => setDefaultLayer(e.target.value)}>
              {LAYERS.map((l) => (
                <option key={l} value={l}>
                  {l}
                </option>
              ))}
            </select>
          </label>
          <button type="button" disabled={!text.trim() || (job !== null && importPending(job))} onClick={() => void submit()}>
            导入
          </button>
        </div>
        <textarea value={text} onChange={(e) => setText(e.target.value)} rows={14} placeholder="package '…' { … }" aria-label="SysML v2 文本" />
      </section>

      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}

      {job && (
        <section>
          <h2>结果</h2>
          {importPending(job) && <p className="hint">导入中…</p>}
          {job.status === 'FAILED' && (
            <div className="notice error" role="alert">
              <strong>未导入（什么都没有创建）：</strong>
              <ul>
                {job.problems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
              {job.error}
            </div>
          )}
          {report && (
            <>
              <p>
                已导入到新项目「{report.projectName}」：共 {report.total} 个元素（{countsText(report.counts)}）。{' '}
                <a href={siriusUrl(`/projects/${report.projectId}/edit`)} target="_blank" rel="noreferrer">
                  在 Sirius 中打开
                </a>{' '}
                <button type="button" onClick={() => onOpen(report.projectId)}>
                  在本页打开
                </button>
              </p>
              <h3>按默认层 {report.defaultLayer} 放置（{report.defaultedLayer.length}）</h3>
              <ul>{report.defaultedLayer.map((x) => <li key={x}>{x}</li>)}</ul>
              <h3>按类型补出的端口（{report.derivedPorts.length}）</h3>
              <ul>{report.derivedPorts.map((x) => <li key={x}>{x}</li>)}</ul>
              <h3>未导入（UNMAPPED，{report.unmapped.length}）</h3>
              <ul>{report.unmapped.map((x) => <li key={x}>{x}</li>)}</ul>
              <h3>一致性检查</h3>
              <p>约束违规 {report.consistency.constraintViolations.length} 项；R6 无 satisfiedBy 的需求 {report.consistency.R6.length} 条。</p>
              <ul>
                {report.consistency.constraintViolations.map((x) => (
                  <li key={x} className="missing">
                    {x}
                  </li>
                ))}
                {report.consistency.R6.map((x) => (
                  <li key={x}>R6：{x}</li>
                ))}
              </ul>
              <p className="hint">{report.consistency.notApplicable}</p>
            </>
          )}
        </section>
      )}

      <section>
        <h2>最近的导入</h2>
        <table className="list">
          <tbody>
            {recent.map((j) => (
              <tr key={j.id}>
                <td>{j.sourceName ?? ''}</td>
                <td>{j.status}</td>
                <td>{j.report ? `${j.report.projectName}，${j.report.total} 个元素` : j.problems.length ? `${j.problems.length} 处问题` : j.error ?? ''}</td>
                <td>
                  {j.requestedBy}，{new Date(j.createdAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
