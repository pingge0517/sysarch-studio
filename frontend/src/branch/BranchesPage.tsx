import { useCallback, useEffect, useState } from 'react';
import type { Baseline } from '../baseline/api';
import { listBaselines, siriusUrl } from '../baseline/api';
import { fieldLabel } from '../baseline/diffRows';
import type { Branch, Change } from './api';
import { abandonBranch, createBranch, listBranches, listChanges } from './api';
import { opLabel } from './workspaces';
import { MergePanel } from './MergePanel';
import type { MergeView } from './api';

function time(iso: string): string {
  return new Date(iso).toLocaleString();
}

/**
 * Branches of a trunk project (P2 T3): fork one from a baseline, switch to it (the shell's workspace selector), see its
 * changeset (one net change per element against the base baseline), open its workspace in Sirius Web, merge it into
 * the trunk (preview, choose, apply; T4), abandon it.
 */
export function BranchesPage({ trunk, current, onSwitch }: { trunk: string; current: string; onSwitch: (branchId: string) => void }) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [baselines, setBaselines] = useState<Baseline[]>([]);
  const [name, setName] = useState('');
  const [baseline, setBaseline] = useState('');
  const [shown, setShown] = useState<string | null>(null);
  const [changes, setChanges] = useState<Change[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [merging, setMerging] = useState<Branch | null>(null);
  const [merged, setMerged] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [b, bl] = await Promise.all([listBranches(trunk), listBaselines(trunk)]);
      setBranches(b);
      setBaselines(bl);
      setBaseline((c) => (c && bl.some((x) => x.id === c) ? c : bl[0]?.id ?? ''));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [trunk]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const show = async (branchId: string) => {
    setShown(branchId);
    try {
      setChanges(await listChanges(branchId));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const create = async () => {
    setBusy(true);
    try {
      const branch = await createBranch(trunk, name.trim(), baseline);
      setName('');
      await reload();
      onSwitch(branch.id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const abandon = async (branch: Branch) => {
    try {
      await abandonBranch(branch.id);
      if (current === branch.id) {
        onSwitch('');
      }
      await reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="branches">
      <section>
        <h2>从基线拉分支</h2>
        <p className="hint">分支在自己的 Sirius 项目（工作区）里编辑，由所选基线还原，不带图；主干不受影响。每个元素记一条净变更。</p>
        <div className="row">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="分支名称" aria-label="分支名称" />
          <select value={baseline} onChange={(e) => setBaseline(e.target.value)} aria-label="基线">
            {baselines.length === 0 && <option value="">（先在“基线与差异”里建一个基线）</option>}
            {baselines.map((b) => (
              <option key={b.id} value={b.id}>
                {b.label}（{time(b.createdAt)}）
              </option>
            ))}
          </select>
          <button type="button" disabled={busy || !name.trim() || !baseline} onClick={() => void create()}>
            {busy ? '创建中…' : '创建并切换'}
          </button>
        </div>
      </section>

      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}

      <section>
        <h2>分支</h2>
        {branches.length === 0 && <p className="hint">还没有分支。</p>}
        <table className="list">
          <tbody>
            {branches.map((b) => (
              <tr key={b.id} className={b.status !== 'OPEN' ? 'closed' : undefined}>
                <td>
                  <strong>{b.name}</strong>
                  {current === b.id && '（当前）'}
                </td>
                <td>基线 {b.baselineLabel ?? b.baseBaseline}</td>
                <td>{b.status === 'OPEN' ? `${b.changes} 项变更` : b.status === 'ABANDONED' ? '已放弃' : '已合并'}</td>
                <td>
                  {b.createdBy}，{time(b.createdAt)}
                </td>
                <td className="actions">
                  {b.status === 'OPEN' && current !== b.id && (
                    <button type="button" onClick={() => onSwitch(b.id)}>
                      切换到此分支
                    </button>
                  )}
                  <button type="button" onClick={() => void show(b.id)}>
                    变更集
                  </button>
                  {b.status === 'OPEN' && (
                    <a href={siriusUrl(`/projects/${b.workspaceProjectId}/edit`)} target="_blank" rel="noreferrer">
                      在 Sirius 中编辑
                    </a>
                  )}
                  {b.status === 'OPEN' && (
                    <button type="button" onClick={() => setMerging(b)}>
                      合并到主干…
                    </button>
                  )}
                  {b.status === 'OPEN' && (
                    <button type="button" title="删除工作区，保留变更集记录（写入审计）" onClick={() => void abandon(b)}>
                      放弃
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {merged && (
        <div className="notice" role="status">
          {merged}
          <button type="button" onClick={() => setMerged(null)}>
            知道了
          </button>
        </div>
      )}

      {merging && (
        <MergePanel
          branch={merging}
          onClose={() => setMerging(null)}
          onDone={(result: MergeView) => {
            setMerging(null);
            setMerged(`已合并「${merging.name}」：新增 ${result.stats.added}、修改 ${result.stats.modified}、删除 ${result.stats.deleted}；合并前的主干已保存为基线，可在“基线与差异”中查看。`);
            if (current === merging.id) {
              onSwitch('');
            }
            void reload();
          }}
        />
      )}

      {shown && (
        <section>
          <h2>变更集：{branches.find((b) => b.id === shown)?.name}</h2>
          {changes.length === 0 && <p className="hint">与基线相同，没有变更。</p>}
          <table className="list">
            <tbody>
              {changes.map((c) => (
                <tr key={c.elementId}>
                  <td>{opLabel(c.op)}</td>
                  <td>{c.elementId}</td>
                  <td>{c.state?.name ?? ''}</td>
                  <td>{c.fields.map(fieldLabel).join('、')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
