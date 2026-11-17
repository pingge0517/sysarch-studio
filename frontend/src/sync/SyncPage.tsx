import { useEffect, useRef, useState } from 'react';
import type { AccessGrant } from '../identity/auth';
import { can, fetchMe } from '../identity/auth';
import type { Binding, ConsistencyReport, PooledItem, SyncResult, SyncStatus, SyncWorkspace } from './api';
import { bind, canPush, check, fetchBindings, fetchPool, fetchStatus, fetchWorkspace, pull, push, repair, unbind, verifyToken } from './api';

type Data = { workspace: SyncWorkspace; bindings: Binding[]; pool: PooledItem[]; status: SyncStatus };
type Confirmation = { kind: 'pull' | 'unbind' | 'repair'; id: string; description: string };

/** Remounted on model/account changes by App: PATs and preflight results never cross workspaces or users. */
export function SyncPage({ project, user, grant }: { project: string; user: string; grant: AccessGrant | null }) {
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [draftToken, setDraftToken] = useState('');
  const [token, setToken] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [preview, setPreview] = useState<{ ids: string[]; result: SyncResult } | null>(null);
  const [outcome, setOutcome] = useState<{ title: string; result: SyncResult } | null>(null);
  const [report, setReport] = useState<ConsistencyReport | null>(null);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [elementId, setElementId] = useState('');
  const [workItemId, setWorkItemId] = useState('');
  const [filter, setFilter] = useState('');
  const alive = useRef(true);
  const running = useRef(false);
  const allowed = can(grant, 'SYNC') && !!user;

  async function reload(): Promise<void> {
    const workspace = await fetchWorkspace(project);
    const [bindings, pool, status] = await Promise.all([
      fetchBindings(workspace.polarionProject), fetchPool(workspace.polarionProject), fetchStatus(workspace.polarionProject),
    ]);
    if (alive.current) {
      setData({ workspace, bindings, pool, status });
      setPreview(null);
    }
  }

  useEffect(() => {
    alive.current = true;
    reload().catch((e: Error) => { if (alive.current) setError(e.message); });
    return () => { alive.current = false; };
  }, [project]);

  async function run(task: () => Promise<void>): Promise<void> {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    setConfirmation(null);
    try {
      await task();
    } catch (e) {
      if (alive.current) setError((e as Error).message);
    } finally {
      running.current = false;
      if (alive.current) setBusy(false);
    }
  }

  async function authenticate(value = token): Promise<void> {
    const current = await fetchMe();
    if (current.user !== user) throw new Error('登录账号已变化，请刷新页面后重新授权同步');
    await verifyToken(value, user);
    const workspace = await fetchWorkspace(project);
    if (!data || workspace.siriusProjectId !== data.workspace.siriusProjectId || workspace.polarionProject !== data.workspace.polarionProject) {
      throw new Error('项目绑定已变化，请刷新绑定与状态后重新预检');
    }
    if (!alive.current) throw new Error('同步页面已关闭');
  }

  function clearCredential(): void {
    setToken('');
    setDraftToken('');
    setPreview(null);
    setConfirmation(null);
    setReport(null);
    setOutcome(null);
  }

  async function preflight(): Promise<void> {
    if (!data) return;
    setPreview(null);
    await authenticate();
    const result = await push(data.workspace.polarionProject, selected, token, true);
    if (alive.current) {
      setOutcome({ title: '推送预检（尚未写入 Polarion）', result });
      if (canPush(result)) setPreview({ ids: [...selected], result });
    }
  }

  async function executePush(): Promise<void> {
    if (!data || !preview || !canPush(preview.result)) return;
    const ids = [...preview.ids];
    setPreview(null);
    await authenticate();
    const result = await push(data.workspace.polarionProject, ids, token, false);
    if (alive.current) {
      setOutcome({ title: '推送结果', result });
      setReport(null);
      await reload();
    }
  }

  async function executeConfirmation(value: Confirmation): Promise<void> {
    if (!data) return;
    setPreview(null);
    if (value.kind === 'unbind') {
      await unbind(value.id);
      if (alive.current) setNotice(`已解除 ${value.id} 的绑定；Polarion Work Item 保留`);
    } else {
      await authenticate();
      if (value.kind === 'pull') {
        const result = await pull(data.workspace.polarionProject, token);
        if (alive.current) setOutcome({ title: '拉取结果', result });
      } else {
        const result = await repair(data.workspace.polarionProject, value.id, token);
        if (alive.current) {
          setNotice(result.actions.map((a) => `${a.issueId}：${a.status} — ${a.message}`).join('；'));
          setReport(await check(data.workspace.polarionProject, token));
        }
      }
    }
    if (alive.current) {
      if (value.kind !== 'repair') setReport(null);
      await reload();
    }
  }

  function toggle(id: string): void {
    setSelected((ids) => ids.includes(id) ? ids.filter((s) => s !== id) : [...ids, id]);
    setPreview(null);
    setConfirmation(null);
  }

  const timestamp = (value: string | null) => value ? new Date(value).toLocaleString() : '尚无记录';
  const bound = new Map(data?.bindings.map((b) => [b.elementId, b]) ?? []);
  const candidate = data?.workspace.elements.find((e) => e.id === elementId);
  const usable = allowed && !!token && !busy && !!data;
  const matching = data?.workspace.elements.filter((e) => `${e.id} ${e.name} ${e.type}`.toLowerCase().includes(filter.toLowerCase())) ?? [];

  return (
    <section className="page sync-page" aria-busy={busy}>
      <h2>Polarion 同步</h2>
      <p className="hint">模型字段推送到 Polarion，需求正文和编号从 Polarion 拉取。双方修改冲突会列出，不会自动覆盖。</p>
      {error && <p className="notice error" role="alert">{error}</p>}
      {notice && <p className="notice" role="status">{notice}</p>}
      {!allowed && <p>仅当前项目的架构师或管理员可以同步。</p>}
      {!data && <button disabled={busy} onClick={() => void run(reload)}>重新加载项目绑定</button>}
      {data && <>
        <div className="panel">
          <h3>目标项目：{data.workspace.polarionProject}</h3>
          <p>上次推送：{timestamp(data.status.lastPushAt)} · 上次拉取：{timestamp(data.status.lastPullAt)}</p>
          <p>定时拉取：{data.status.schedule.enabled ? data.status.schedule.paused ? '已暂停' : '已启用' : '未启用'}{data.status.schedule.lastError ? ` — ${data.status.schedule.lastError}` : ''}</p>
          <button disabled={busy} onClick={() => void run(reload)}>刷新绑定与状态</button>
        </div>
        <div className="panel">
          <h3>本次同步授权</h3>
          <p className="hint">单点登录凭证不能直接调用 Polarion 同步。请在 My Account → Personal Access Tokens 创建当前账号的个人令牌；仅保存在本页面内存中，离开同步页或切换账号、模型后清除。</p>
          {token ? <div className="row">
            <span>已验证账号：{user}</span>
            <button disabled={busy} onClick={clearCredential}>清除令牌</button>
          </div> : <form className="row" onSubmit={(e) => {
            e.preventDefault();
            void run(async () => {
              const value = draftToken.trim();
              await authenticate(value);
              if (alive.current) { setToken(value); setDraftToken(''); }
            });
          }}>
            <input type="password" aria-label="Polarion 个人访问令牌" autoComplete="off" value={draftToken}
              disabled={busy || !allowed} onChange={(e) => setDraftToken(e.target.value)} />
            <button disabled={busy || !allowed || !draftToken.trim()}>验证当前账号令牌</button>
          </form>}
        </div>
        <div className="panel">
          <h3>选择要推送的元素</h3>
          <p className="hint">未绑定元素会在目标项目新建 Work Item。已绑定元素更新模型拥有的字段。预检可能包含追溯关系涉及的其他元素；正式执行时服务器会再次检查。</p>
          <div className="row">
            <input aria-label="筛选同步元素" placeholder="按编号、名称或类型筛选" value={filter} onChange={(e) => setFilter(e.target.value)} />
            <span>匹配 {matching.length} 个，最多显示前 200 个；已选元素在筛选后仍然保留。</span>
          </div>
          <div className="sync-table">
            <table className="diff">
              <thead><tr><th>选择</th><th>元素</th><th>类型／层</th><th>Work Item</th><th>状态</th></tr></thead>
              <tbody>{matching.slice(0, 200).map((e) => <tr key={e.id}>
                <td><input type="checkbox" aria-label={`推送 ${e.id}`} disabled={busy || !allowed} checked={selected.includes(e.id)} onChange={() => toggle(e.id)} /></td>
                <td>{e.id} {e.name}</td><td>{e.type}／{e.layer}</td>
                <td>{bound.get(e.id)?.workItemId ?? '推送时新建'}</td>
                <td>{e.errors.length ? e.errors.map((v) => v.message).join('；') : bound.get(e.id)?.state ?? '未绑定'}</td>
              </tr>)}</tbody>
            </table>
          </div>
          <div className="row">
            <span>已选 {selected.length} 个</span>
            <button disabled={!usable || !selected.length} onClick={() => void run(preflight)}>预检推送</button>
            <button disabled={!usable} onClick={() => {
              setPreview(null);
              setConfirmation({ kind: 'pull', id: '', description: `从 ${data.workspace.polarionProject} 拉取已绑定工作项，更新模型中的需求正文和编号。新 Work Item 的发现由定时拉取完成。` });
            }}>拉取…</button>
            <button disabled={!usable} onClick={() => void run(async () => {
              await authenticate();
              const result = await check(data.workspace.polarionProject, token);
              if (alive.current) { setReport(result); setPreview(null); }
            })}>在线一致性检查</button>
          </div>
          {outcome && <div role="status">
            <h4>{outcome.title}</h4>
            <p>推送 {outcome.result.pushed} · 拉取 {outcome.result.pulled} · 跳过 {outcome.result.skipped} · 冲突 {outcome.result.conflicts.length} · 错误 {outcome.result.errors.length}</p>
            <ul>{outcome.result.conflicts.map((c) => <li key={c.elementId}>冲突 {c.elementId}：{c.fields.join('、')}。请核对两端数据后处理。</li>)}
              {outcome.result.errors.map((e, i) => <li key={i}>{e.elementId} {e.code}：{e.message}</li>)}</ul>
          </div>}
          {preview && <div className="sync-confirm" role="group" aria-label="确认推送">
            <p>目标：<strong>{data.workspace.polarionProject}</strong>；所选：{preview.ids.join('、')}；预检预计写入 {preview.result.pushed} 个。</p>
            <button disabled={!usable} onClick={() => void run(executePush)}>确认推送到 {data.workspace.polarionProject}</button>
            <button disabled={busy} onClick={() => setPreview(null)}>取消</button>
          </div>}
        </div>
        <div className="panel">
          <h3>绑定与认领</h3>
          <form className="row" onSubmit={(e) => {
            e.preventDefault();
            if (!candidate || !workItemId.trim()) return;
            void run(async () => {
              await bind(data.workspace.polarionProject, candidate, workItemId.trim());
              if (alive.current) { setNotice('已建立绑定；请预检并推送模型字段'); setWorkItemId(''); setReport(null); await reload(); }
            });
          }}>
            <select aria-label="绑定模型元素" disabled={busy || !allowed} value={elementId} onChange={(e) => setElementId(e.target.value)}>
              <option value="">选择未绑定元素</option>
              {data.workspace.elements.filter((e) => !bound.has(e.id)).map((e) => <option key={e.id} value={e.id}>{e.id} {e.name} → {e.workItemType}</option>)}
            </select>
            <input aria-label="绑定 Work Item ID" placeholder="Work Item ID" list="sync-pool" disabled={busy || !allowed} value={workItemId} onChange={(e) => setWorkItemId(e.target.value)} />
            <datalist id="sync-pool">{data.pool.map((p) => <option key={p.workItemId} value={p.workItemId}>{p.title} ({p.workItemType})</option>)}</datalist>
            <button disabled={busy || !allowed || !candidate || bound.has(elementId) || !workItemId.trim()}>建立绑定</button>
          </form>
          <p className="hint">待认领池 {data.pool.length} 项；通过 Work Item ID 输入框选择。绑定本身不验证工作项是否存在，建立后请运行在线一致性检查。</p>
          <div className="sync-table"><table className="diff">
            <thead><tr><th>元素</th><th>Work Item</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>{data.bindings.map((b) => <tr key={b.elementId}>
              <td>{b.elementId}</td><td>{b.workItemId}</td><td>{b.state}</td><td>
                <button disabled={busy || !allowed} onClick={() => { setPreview(null); setConfirmation({ kind: 'unbind', id: b.elementId, description: `解除 ${b.elementId} ↔ ${b.workItemId} 的绑定。保留两端数据，不删除 Work Item。` }); }}>解除绑定…</button>
              </td>
            </tr>)}</tbody>
          </table></div>
        </div>
        {report && <div className="panel">
          <h3>一致性报告 · {timestamp(report.generatedAt)}</h3>
          <p>{report.issues.length} 个问题。修复使用服务器配置的策略，每次只处理一个问题。</p>
          {report.issues.map((issue) => <div className="sync-issue" key={issue.id}>
            <p><strong>{issue.severity} · {issue.rule}</strong> {issue.message}</p>
            {issue.repairable && <button disabled={!usable} onClick={() => { setPreview(null); setConfirmation({ kind: 'repair', id: issue.id, description: `处理 ${issue.id}：${issue.message}。按项目配置的修复策略执行，可能改变模型、绑定或 Polarion 数据。` }); }}>修复此项…</button>}
          </div>)}
        </div>}
        {confirmation && <div className="panel sync-confirm" role="group" aria-label="确认同步操作">
          <p><strong>项目：{data.workspace.polarionProject}</strong> — {confirmation.description}</p>
          <button disabled={busy || !allowed} onClick={() => void run(() => executeConfirmation(confirmation))}>确认执行</button>
          <button disabled={busy} onClick={() => setConfirmation(null)}>取消</button>
        </div>}
      </>}
    </section>
  );
}
