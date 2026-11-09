import { useCallback, useEffect, useState } from 'react';
import type { Baseline, ModelDiff } from './api';
import { createBaseline, diffBaseline, listBaselines, locate, siriusUrl } from './api';
import { diffRows, fieldLabel, summary } from './diffRows';

const KIND_LABELS = { added: '新增', modified: '修改', removed: '删除' } as const;

/**
 * Baseline selector and diff list (P1 T7 step 3): create a baseline of the project, pick one and compare it with the
 * current model or with another baseline; a row of an element that exists now opens it in Sirius Web.
 */
export function BaselinePage({ project }: { project: string }) {
  const [baselines, setBaselines] = useState<Baseline[]>([]);
  const [selected, setSelected] = useState('');
  const [other, setOther] = useState(''); // '' = the current model
  const [diff, setDiff] = useState<ModelDiff | null>(null);
  const [label, setLabel] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      const list = await listBaselines(project);
      setBaselines(list);
      setSelected((current) => (current && list.some((b) => b.id === current) ? current : list[0]?.id ?? ''));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project]);

  useEffect(() => {
    setDiff(null);
    setOther('');
    void reload();
  }, [reload]);

  async function create() {
    setBusy(true);
    try {
      const created = await createBaseline(project, label.trim());
      setLabel('');
      await reload();
      setSelected(created.id);
      setDiff(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function compare() {
    setBusy(true);
    try {
      setDiff(await diffBaseline(selected, other || null));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function open(elementId: string) {
    try {
      const location = await locate(project, elementId);
      window.open(siriusUrl(location.path), 'sirius-web');
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const describe = (b: Baseline) => `${b.label}（${new Date(b.createdAt).toLocaleString()}，${b.elementCount} 个元素）`;

  return (
    <div className="baselines">
      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}
      <section className="panel">
        <h2>新建基线</h2>
        <div className="row">
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="基线名称，例如 评审前" maxLength={200} />
          <button type="button" onClick={create} disabled={busy || label.trim() === ''}>
            打基线
          </button>
        </div>
      </section>

      <section className="panel">
        <h2>比较</h2>
        {baselines.length === 0 ? (
          <p className="hint">这个项目还没有基线。</p>
        ) : (
          <div className="row">
            <label>
              基线
              <select value={selected} onChange={(e) => setSelected(e.target.value)}>
                {baselines.map((b) => (
                  <option key={b.id} value={b.id}>
                    {describe(b)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              对比
              <select value={other} onChange={(e) => setOther(e.target.value)}>
                <option value="">当前模型</option>
                {baselines
                  .filter((b) => b.id !== selected)
                  .map((b) => (
                    <option key={b.id} value={b.id}>
                      {describe(b)}
                    </option>
                  ))}
              </select>
            </label>
            <button type="button" onClick={compare} disabled={busy || !selected}>
              比较
            </button>
          </div>
        )}
      </section>

      {diff && (
        <section className="panel">
          <h2>差异：{summary(diff)}</h2>
          {diffRows(diff, other === '').length === 0 ? (
            <p className="hint">没有差异。</p>
          ) : (
            <table className="diff">
              <thead>
                <tr>
                  <th>变化</th>
                  <th>元素</th>
                  <th>字段</th>
                </tr>
              </thead>
              <tbody>
                {diffRows(diff, other === '').map((row) => (
                  <tr key={`${row.kind}:${row.elementId}`} className={row.kind}>
                    <td>{KIND_LABELS[row.kind]}</td>
                    <td>
                      {row.navigable ? (
                        <button type="button" className="link" onClick={() => open(row.elementId)} title="在 Sirius Web 中打开">
                          {row.elementId}
                        </button>
                      ) : (
                        row.elementId
                      )}
                    </td>
                    <td>{row.fields.map(fieldLabel).join('、')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
