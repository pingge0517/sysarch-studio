import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { fetchMatrix, fetchProjects, fetchTables, runAction } from './matrix/api';
import type { Cell, Matrix, Project, TableInfo, ValidationError } from './matrix/api';
import type { CellOption } from './matrix/layout';
import { MatrixView } from './matrix/MatrixView';
import { BaselinePage } from './baseline/BaselinePage';
import { LocksPage } from './locks/LocksPage';
import type { AccessGrant, Me } from './identity/auth';
import { fetchAccess, fetchMe } from './identity/auth';
import { SignIn } from './identity/SignIn';
import { AdminPage } from './identity/AdminPage';
import { CommentsPage } from './comment/CommentsPage';
import type { Branch } from './branch/api';
import { leaveBranch, listBranches } from './branch/api';
import { BranchesPage } from './branch/BranchesPage';
import { effectiveProject, trunks } from './branch/workspaces';
import { ReportsPage } from './report/ReportsPage';
import { ImportPage } from './sysmlimport/ImportPage';
import { SequencePage } from './sequence/SequencePage';

// TODO: mount the Sirius Web frontend components and register the BDD/IBD views. Until then the diagrams are
// edited in Sirius Web's own UI (backend :8080); this shell hosts the P1 matrix pages.

const REFRESH_MS = 10_000;

function param(name: string): string {
  return new URLSearchParams(window.location.search).get(name) ?? '';
}

function setParams(values: Record<string, string>) {
  const search = new URLSearchParams(window.location.search);
  Object.entries(values).forEach(([key, value]) => (value ? search.set(key, value) : search.delete(key)));
  window.history.replaceState(null, '', `${window.location.pathname}?${search}`);
}

/**
 * The shell (P1 T4, T7, P2 T1, T3): pick a Sirius project and a workspace (the trunk or one of its branches), then the
 * matrix of a VSM table, the baselines and their diffs, the edit locks or the branches; ?project=…&branch=…&view=…&table=…
 * keeps the choice in the URL. Switching away from a branch releases the user's locks there and closes their session
 * (P2 T3 step 3). Since P2 T8 the user signs in with their Polarion token (top right); the session cookie reaches
 * Sirius Web on :8080 too, and the header shows the role in the selected workspace.
 */
export function App() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [project, setProject] = useState(param('project'));
  const [branchId, setBranchId] = useState(param('branch'));
  const [allOpen, setAllOpen] = useState<Branch[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [table, setTable] = useState(param('table') || 'allocation-matrix');
  const [view, setView] = useState(['baselines', 'locks', 'branches', 'reports', 'sequence', 'import', 'comments', 'admin'].includes(param('view')) ? param('view') : 'matrix');
  const [me, setMe] = useState<Me | null>(null);
  const [grant, setGrant] = useState<AccessGrant | null>(null);
  const user = me?.user ?? '';
  const [matrix, setMatrix] = useState<Matrix | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refusal, setRefusal] = useState<ValidationError[]>([]);
  const [firstRender, setFirstRender] = useState<number | null>(null);
  const loadStarted = useRef<number | null>(null);

  useEffect(() => {
    fetchProjects().then(setProjects, (e: Error) => setError(`项目列表：${e.message}`));
    listBranches().then(setAllOpen, () => setAllOpen([]));
    fetchTables().then(setTables, (e: Error) => setError(`表格列表：${e.message}`));
  }, []);

  // The trunk's branches, for the workspace selector.
  const reloadBranches = useCallback(async () => {
    if (!project) {
      setBranches([]);
      return;
    }
    try {
      setBranches(await listBranches(project));
      setAllOpen(await listBranches());
    } catch {
      setBranches([]);
    }
  }, [project]);

  useEffect(() => {
    void reloadBranches();
  }, [reloadBranches]);

  const branch = branches.find((b) => b.id === branchId);
  const workspace = effectiveProject(project, branch);

  const reloadMe = useCallback(() => {
    fetchMe().then(setMe, () => setMe(null));
  }, []);
  useEffect(reloadMe, [reloadMe]);
  // The role in the selected workspace (P2 T8): shown in the header; pages disable what it does not allow.
  useEffect(() => {
    fetchAccess(workspace).then(setGrant, () => setGrant(null));
  }, [workspace, me]);

  /** Switch the workspace; leaving a branch releases my locks there and closes my session (T3 step 3). */
  const switchTo = useCallback(
    async (next: string) => {
      if (branchId && branchId !== next && user) {
        try {
          const released = await leaveBranch(branchId);
          setNotice(`已离开分支，释放了 ${released} 把锁`);
        } catch (e) {
          setNotice(`离开分支时出错：${(e as Error).message}`);
        }
      }
      setBranchId(next);
      await reloadBranches();
    },
    [branchId, user, reloadBranches],
  );

  const load = useCallback(
    async (measure: boolean) => {
      if (!workspace || !table || view !== 'matrix') {
        return;
      }
      if (measure) {
        loadStarted.current = performance.now();
        setFirstRender(null);
      }
      try {
        setMatrix(await fetchMatrix(workspace, table));
        setError(null);
      } catch (e) {
        setError((e as Error).message);
        setMatrix(null);
      }
    },
    [workspace, table, view],
  );

  useEffect(() => {
    setParams({ project, branch: branchId, table, view: view === 'matrix' ? '' : view });
    setMatrix(null);
    void load(true);
  }, [project, branchId, table, load]);

  // Other people edit the model in Sirius Web: refetch now and then, and when the page gets focus again.
  useEffect(() => {
    const timer = window.setInterval(() => document.visibilityState === 'visible' && void load(false), REFRESH_MS);
    const onFocus = () => void load(false);
    window.addEventListener('focus', onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', onFocus);
    };
  }, [load]);

  // T4 step 4: time from request to the first painted matrix ("首屏渲染 < 1.5s").
  useLayoutEffect(() => {
    if (matrix && loadStarted.current !== null) {
      const started = loadStarted.current;
      loadStarted.current = null;
      requestAnimationFrame(() => setFirstRender(Math.round(performance.now() - started)));
    }
  }, [matrix]);

  const onAction = useCallback(
    async (option: CellOption, cell: Cell) => {
      try {
        const response = await runAction(workspace, table, option.action.id, cell, option.remove);
        setMatrix(response.matrix);
        setRefusal(response.errors);
        return response.errors;
      } catch (e) {
        setError((e as Error).message);
        return [];
      }
    },
    [workspace, table],
  );

  return (
    <div className="app">
      <header className="bar">
        <h1>SysArch Studio</h1>
        <label>
          项目
          <select
            value={project}
            onChange={(e) => {
              void switchTo('');
              setProject(e.target.value);
            }}
          >
            <option value="">请选择</option>
            {trunks(projects, allOpen).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        {project && (
          <label title="分支在自己的工作区里编辑；切走时释放你在该分支的锁">
            工作区
            <select value={branch ? branchId : ''} onChange={(e) => void switchTo(e.target.value)}>
              <option value="">主干</option>
              {branches
                .filter((b) => b.status === 'OPEN')
                .map((b) => (
                  <option key={b.id} value={b.id}>
                    ⎇ {b.name}
                  </option>
                ))}
            </select>
          </label>
        )}
        <label>
          视图
          <select value={view} onChange={(e) => setView(e.target.value)}>
            <option value="matrix">矩阵</option>
            <option value="baselines">基线与差异</option>
            <option value="locks">编辑锁</option>
            <option value="branches">分支</option>
            <option value="reports">报告</option>
            <option value="sequence">时序图（只读）</option>
            <option value="import">导入（SysML v2 / XMI）</option>
            <option value="comments">评论</option>
            <option value="admin">角色与审计</option>
          </select>
        </label>
        {view === 'matrix' && <label>
          表格
          <select value={table} onChange={(e) => setTable(e.target.value)}>
            {tables.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
        </label>}
        <span className="spacer" />
        {view === 'matrix' && firstRender !== null && <span className="timing">首屏 {firstRender} ms</span>}
        <SignIn me={me} grant={project ? grant : null} onChange={reloadMe} />
      </header>

      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="notice" role="status">
          {notice}
          <button type="button" onClick={() => setNotice(null)}>
            知道了
          </button>
        </div>
      )}
      {refusal.length > 0 && (
        <div className="notice refusal" role="alert">
          <strong>未执行：</strong>
          {refusal.map((r) => r.message).join('；')}
          <button type="button" onClick={() => setRefusal([])}>
            知道了
          </button>
        </div>
      )}

      <main className="content">
        {!project && view !== 'import' && view !== 'admin' && <p className="hint">选择一个 Sirius 项目。</p>}
        {view === 'import' && (
          <ImportPage
            onOpen={(id) => {
              fetchProjects().then(setProjects, (e: Error) => setError(`项目列表：${e.message}`));
              void switchTo('');
              setProject(id);
              setView('matrix');
            }}
          />
        )}
        {project && view === 'baselines' && <BaselinePage project={workspace} />}
        {project && view === 'locks' && <LocksPage project={workspace} user={user} />}
        {project && view === 'reports' && <ReportsPage project={workspace} />}
        {project && view === 'sequence' && <SequencePage project={workspace} />}
        {project && view === 'comments' && <CommentsPage project={workspace} grant={grant} />}
        {view === 'admin' && <AdminPage project={project ? workspace : ''} />}
        {project && view === 'branches' && <BranchesPage trunk={project} current={branch ? branchId : ''} onSwitch={(id) => void switchTo(id)} />}
        {project && view === 'matrix' && matrix && <MatrixView matrix={matrix} onAction={onAction} />}
        {project && view === 'matrix' && !matrix && !error && <p className="hint">加载中…</p>}
      </main>
    </div>
  );
}
