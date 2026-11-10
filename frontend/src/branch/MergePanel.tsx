import { useEffect, useState } from 'react';
import { fieldLabel } from '../baseline/diffRows';
import type { Branch, MergeView, Side, Strategy } from './api';
import { MergeRefused, merge } from './api';
import { chooseAll, key, kindLabel, missing, resolutions } from './resolutions';
import { opLabel } from './workspaces';

/**
 * Merging a branch into its trunk (P2 T4): preview first (nothing is written), choose a side for every conflict, then
 * apply. Conflicts list element and field with base / branch / trunk values; Polarion-owned fields are decided by the
 * strategy and listed as such. More than 50 conflicts: no "all one side", each is handled per element (spec §3.3).
 */
export function MergePanel({ branch, onDone, onClose }: { branch: Branch; onDone: (result: MergeView) => void; onClose: () => void }) {
  const [strategy, setStrategy] = useState<Strategy>('POLARION_WINS');
  const [view, setView] = useState<MergeView | null>(null);
  const [chosen, setChosen] = useState(new Map<string, Side>());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const preview = async () => {
    setBusy(true);
    setError(null);
    try {
      setView(await merge(branch.id, 'PREVIEW', strategy, resolutions(chosen)));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void preview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branch.id, strategy]);

  const apply = async () => {
    if (!view) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onDone(await merge(branch.id, 'APPLY', strategy, resolutions(chosen), view.runId));
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof MergeRefused && e.result) {
        setView(e.result);
      }
    } finally {
      setBusy(false);
    }
  };

  const choose = (k: string, side: Side) => setChosen((c) => new Map(c).set(k, side));
  const conflicts = view?.conflicts ?? [];
  const dangling = view?.unresolved.filter((c) => c.kind === 'DANGLING') ?? [];
  const open = view ? missing([...conflicts, ...dangling], chosen) : [];

  return (
    <section className="merge">
      <h2>合并分支「{branch.name}」到主干</h2>
      <div className="row">
        <label title="只裁决 Polarion 负责的字段（需求正文、reqId）；建模字段的冲突一律交人选择">
          Polarion 字段
          <select value={strategy} onChange={(e) => setStrategy(e.target.value as Strategy)}>
            <option value="POLARION_WINS">取主干（POLARION_WINS）</option>
            <option value="MODEL_WINS">取分支（MODEL_WINS）</option>
          </select>
        </label>
        <button type="button" disabled={busy} onClick={() => void preview()}>
          重新预演
        </button>
        <button type="button" disabled={busy || !view || open.length > 0} onClick={() => void apply()}>
          {busy ? '处理中…' : '应用合并'}
        </button>
        <button type="button" onClick={onClose}>
          关闭
        </button>
      </div>
      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}
      {view && (
        <>
          <p>
            预演：新增 {view.stats.added}，修改 {view.stats.modified}，删除 {view.stats.deleted}；冲突 {view.stats.conflicts}
            {open.length > 0 ? `，还有 ${open.length} 项待处理` : '，可以应用'}。应用前会自动给主干建一个基线，便于回退。
          </p>
          {view.autoMergeRefused && <div className="notice refusal">冲突超过 50 条，已拒绝自动合并：请逐条处理（建议先把分支重新基于新基线）。</div>}
          {conflicts.length > 0 && (
            <>
              <h3>冲突</h3>
              {!view.autoMergeRefused && (
                <div className="row">
                  <button type="button" onClick={() => setChosen(chooseAll(conflicts, 'OURS'))}>
                    全部取主干
                  </button>
                  <button type="button" onClick={() => setChosen(chooseAll(conflicts, 'THEIRS'))}>
                    全部取分支
                  </button>
                </div>
              )}
              <table className="list">
                <thead>
                  <tr>
                    <th>元素</th>
                    <th>字段</th>
                    <th>类型</th>
                    <th>基线</th>
                    <th>分支</th>
                    <th>主干</th>
                    <th>选择</th>
                  </tr>
                </thead>
                <tbody>
                  {conflicts.map((c) => (
                    <tr key={key(c)} className={chosen.has(key(c)) ? undefined : 'open'}>
                      <td>{c.elementId}</td>
                      <td>{c.field ? fieldLabel(c.field) : '整个元素'}</td>
                      <td>{kindLabel(c.kind)}</td>
                      <td>{c.base ?? ''}</td>
                      <td>{c.theirs ?? ''}</td>
                      <td>{c.ours ?? ''}</td>
                      <td>
                        {(['OURS', 'THEIRS'] as Side[]).map((side) => (
                          <label key={side}>
                            <input type="radio" name={key(c)} checked={chosen.get(key(c)) === side} onChange={() => choose(key(c), side)} />
                            {side === 'OURS' ? '取主干' : '取分支'}
                          </label>
                        ))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
          {dangling.length > 0 && (
            <div className="notice refusal">
              <strong>引用悬空（改变上面的选择才能消除，然后重新预演）：</strong>
              {dangling.map((d) => `${d.elementId}.${fieldLabel(d.field ?? '')} → ${d.theirs}`).join('；')}
            </div>
          )}
          {view.autoResolved.length > 0 && (
            <p className="hint">
              按策略自动裁决的 Polarion 字段：{view.autoResolved.map((a) => `${a.elementId}.${fieldLabel(a.field)}`).join('、')}
            </p>
          )}
          <h3>对主干的修改</h3>
          {view.operations.length === 0 && <p className="hint">没有需要合入的修改。</p>}
          <table className="list">
            <tbody>
              {view.operations.map((o) => (
                <tr key={o.elementId}>
                  <td>{opLabel(o.op)}</td>
                  <td>{o.elementId}</td>
                  <td>{o.fields.map(fieldLabel).join('、')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
