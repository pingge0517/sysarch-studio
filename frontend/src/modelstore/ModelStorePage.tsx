import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { listModels, readModel, readView, type ModelPage, type ModelSummary, type Snapshot } from './api';
import { MatrixView } from '../matrix/MatrixView';
import type { Matrix } from '../matrix/api';
import type { NativeDiagram } from './canvasData';
import { watchReadAccess } from './readAccess';
const NativeDiagramCanvas = lazy(() => import('./NativeDiagramCanvas').then((module) => ({ default: module.NativeDiagramCanvas })));

export function ModelStorePage() {
  const [project, setProject] = useState('');
  const [token, setToken] = useState('');
  const [page, setPage] = useState<ModelPage | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [opened, setOpened] = useState<ModelSummary | null>(null);
  const [matrix, setMatrix] = useState<Matrix | null>(null);
  const [diagram, setDiagram] = useState<{ value: NativeDiagram; label: string } | null>(null);
  const [diagramRoot, setDiagramRoot] = useState('');
  const [selected, setSelected] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => {
    if (!page) return;
    return watchReadAccess(async (signal) => {
      if (snapshot && opened) {
        const next = await readModel(opened, token, signal);
        if (next.subject !== snapshot.subject || next.version !== snapshot.version || next.commitId !== snapshot.commitId) throw Error('changed');
      } else {
        const next = await listModels(project.trim(), page.page, token, signal);
        if (JSON.stringify(next) !== JSON.stringify(page)) throw Error('changed');
      }
    }, () => { clear(); setError('读取授权已失效、连接中断或模型已变化，请重新读取'); });
  }, [page, snapshot, opened, project, token]);
  function clear() {
    pending.current?.abort(); pending.current = null;
    setPage(null); setSnapshot(null); setOpened(null); setMatrix(null); setDiagram(null); setDiagramRoot(''); setSelected(''); setError(''); setBusy(false);
  }
  async function run(action: (signal: AbortSignal) => Promise<void>) {
    pending.current?.abort(); const request = new AbortController(); pending.current = request;
    setBusy(true); setError(''); setSnapshot(null); setOpened(null); setMatrix(null); setDiagram(null); setSelected('');
    try { await action(request.signal); }
    catch (failure) { if (!request.signal.aborted) { setPage(null); setError((failure as Error).message); } }
    finally { if (pending.current === request) { pending.current = null; setBusy(false); } }
  }
  async function browse(number: number) {
    setPage(null);
    await run(async (signal) => { const next = await listModels(project.trim(), number, token, signal); if (!signal.aborted) setPage(next); });
  }
  async function open(model: ModelSummary) {
    await run(async (signal) => { const next = await readModel(model, token, signal); if (!signal.aborted) { setSnapshot(next); setOpened(model); setDiagramRoot(next.model.nodes.find((node) => node.type === 'PartDefinition')?.uuid ?? ''); } });
  }
  async function graph(id: string, label: string) {
    if (!snapshot || !opened || !diagramRoot) return;
    const current = snapshot, model = opened;
    await run(async (signal) => {
      const next = await readView(model, id, token, signal, diagramRoot);
      if (signal.aborted) return;
      if (next.version !== current.version || next.commitId !== current.commitId || next.subject !== current.subject) throw new Error('模型版本或身份已变化，请重新打开模型');
      if (!next.representation.diagram) throw new Error('未返回图形');
      setSnapshot(current); setOpened(model); setDiagram({ value: next.representation.diagram, label });
    });
  }
  async function table(id: string) {
    if (!snapshot || !opened) return;
    const current = snapshot, model = opened;
    await run(async (signal) => {
      const next = await readView(model, id, token, signal);
      if (signal.aborted) return;
      if (next.version !== current.version || next.commitId !== current.commitId || next.subject !== current.subject) throw new Error('模型版本或身份已变化，请重新打开模型');
      if (!next.representation.matrix) throw new Error('未返回矩阵');
      setSnapshot(current); setOpened(model); setMatrix(next.representation.matrix);
    });
  }
  const node = snapshot?.model.nodes.find((item) => item.uuid === selected);
  const requirement = snapshot?.requirements?.find((item) => item.uuid === selected);
  return <section>
    <h2>Polarion 模型浏览（只读）</h2>
    <p className="hint">输入 Polarion 项目及个人访问令牌，浏览模型元素、属性和引用。令牌仅保留在本页面内存中。</p>
    <form onSubmit={(event) => { event.preventDefault(); void browse(1); }}>
      <label>Polarion 项目 <input value={project} onChange={(event) => { clear(); setProject(event.target.value); }} required /></label>{' '}
      <label>个人访问令牌 <input type="password" autoComplete="off" value={token} onChange={(event) => { clear(); setToken(event.target.value); }} required /></label>{' '}
      <button disabled={busy || !project.trim() || !token.trim()}>读取模型列表</button>{' '}
      <button type="button" onClick={() => { clear(); setToken(''); }}>清除</button>
    </form>
    {busy && <p role="status">读取中…</p>}
    {error && <p role="alert">{error}</p>}
    {page && <>
      <p>第 {page.page} 页</p>
      {page.models.length === 0 && <p>本页没有可读取的模型。</p>}
      <ul>{page.models.map((model) => <li key={model.location.root.workItem}>
        <button disabled={busy} onClick={() => void open(model)}>{model.title}</button> <small>{model.location.root.workItem}</small>
      </li>)}</ul>
      <button disabled={busy || page.page <= 1} onClick={() => void browse(page.page - 1)}>上一页</button>{' '}
      <button disabled={busy || !page.mayHaveMore} onClick={() => void browse(page.page + 1)}>下一页</button>
    </>}
    {snapshot && <>
      <p>读取用户：{snapshot.subject} · 版本：{snapshot.version} · 元素：{snapshot.model.nodes.length}</p>
      {(snapshot.views ?? []).filter((view) => view.kind === 'TABLE').map((view) => <button key={view.id} disabled={busy} onClick={() => void table(view.id)}>{view.label}（只读）</button>)}
      <label>图根元素 <select aria-label="图根元素" value={diagramRoot} onChange={(event) => { setDiagram(null); setDiagramRoot(event.target.value); }}>
        <option value="">请选择图根元素</option>{snapshot.model.nodes.filter((node) => node.type === 'PartDefinition').map((node) => <option key={node.uuid} value={node.uuid}>{node.attributes.name?.[0] || node.uuid}</option>)}
      </select></label>
      {(snapshot.views ?? []).filter((view) => view.kind === 'DIAGRAM').map((view) => <button key={view.id} disabled={busy || !diagramRoot} onClick={() => void graph(view.id, view.label)}>{view.label}（只读）</button>)}
      {matrix && <MatrixView key={`${snapshot.model.modelUuid}:${matrix.tableId}`} matrix={matrix} readOnly onAction={async () => []} />}
      {diagram && <Suspense fallback={<p role="status">画布加载中…</p>}><NativeDiagramCanvas key={diagram.value.id} diagram={diagram.value} label={diagram.label} /></Suspense>}
      <label>元素 <select aria-label="模型元素" value={selected} onChange={(event) => setSelected(event.target.value)}>
        <option value="">请选择元素</option>
        {snapshot.model.nodes.map((item) => <option key={item.uuid} value={item.uuid}>{item.attributes.name?.[0] || item.uuid} ({item.type})</option>)}
      </select></label>
      {node && <><h3>{node.type}</h3><p>UUID：{node.uuid}</p>
        {requirement && <p>需求来源：{requirement.item.project} / {requirement.item.workItem} · 固定修订：{requirement.item.revision}</p>}
        <table><thead><tr><th>属性</th><th>值</th></tr></thead><tbody>{Object.entries(node.attributes).map(([key, values]) => <tr key={key}><td>{key}</td><td style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{values.join('\n')}</td></tr>)}</tbody></table>
        <h3>引用</h3><ul>{Object.entries(node.references).map(([key, ids]) => <li key={key}>{key}: {ids.map((id) => <button key={id} onClick={() => setSelected(id)}>{snapshot.model.nodes.find((item) => item.uuid === id)?.attributes.name?.[0] || id}</button>)}</li>)}</ul>
      </>}
    </>}
  </section>;
}
