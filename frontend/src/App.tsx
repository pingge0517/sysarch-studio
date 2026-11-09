import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { fetchMatrix, fetchProjects, fetchTables, runAction } from './matrix/api';
import type { Cell, Matrix, Project, TableInfo, ValidationError } from './matrix/api';
import type { CellOption } from './matrix/layout';
import { MatrixView } from './matrix/MatrixView';
import { BaselinePage } from './baseline/BaselinePage';
import { LocksPage } from './locks/LocksPage';
import { currentUser, setCurrentUser } from './identity/user';

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
 * The shell (P1 T4, T7, P2 T1): pick a Sirius project, then the matrix of a VSM table, the baselines and their diffs,
 * or the edit locks; ?project=…&view=…&table=… keeps the choice in the URL. The user name (top right) is the temporary
 * identity until T8: a cookie that Sirius Web on :8080 receives too, so the backend knows who edits there.
 */
export function App() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [project, setProject] = useState(param('project'));
  const [table, setTable] = useState(param('table') || 'allocation-matrix');
  const [view, setView] = useState(['baselines', 'locks'].includes(param('view')) ? param('view') : 'matrix');
  const [user, setUser] = useState(currentUser());
  const [matrix, setMatrix] = useState<Matrix | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refusal, setRefusal] = useState<ValidationError[]>([]);
  const [firstRender, setFirstRender] = useState<number | null>(null);
  const loadStarted = useRef<number | null>(null);

  useEffect(() => {
    fetchProjects().then(setProjects, (e: Error) => setError(`项目列表：${e.message}`));
    fetchTables().then(setTables, (e: Error) => setError(`表格列表：${e.message}`));
  }, []);

  const load = useCallback(
    async (measure: boolean) => {
      if (!project || !table || view !== 'matrix') {
        return;
      }
      if (measure) {
        loadStarted.current = performance.now();
        setFirstRender(null);
      }
      try {
        setMatrix(await fetchMatrix(project, table));
        setError(null);
      } catch (e) {
        setError((e as Error).message);
        setMatrix(null);
      }
    },
    [project, table, view],
  );

  useEffect(() => {
    setParams({ project, table, view: view === 'matrix' ? '' : view });
    setMatrix(null);
    void load(true);
  }, [project, table, load]);

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
        const response = await runAction(project, table, option.action.id, cell, option.remove);
        setMatrix(response.matrix);
        setRefusal(response.errors);
        return response.errors;
      } catch (e) {
        setError((e as Error).message);
        return [];
      }
    },
    [project, table],
  );

  return (
    <div className="app">
      <header className="bar">
        <h1>SysArch Studio</h1>
        <label>
          项目
          <select value={project} onChange={(e) => setProject(e.target.value)}>
            <option value="">请选择</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          视图
          <select value={view} onChange={(e) => setView(e.target.value)}>
            <option value="matrix">矩阵</option>
            <option value="baselines">基线与差异</option>
            <option value="locks">编辑锁</option>
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
        <label title="临时身份：T8 换成 Polarion 登录">
          我是
          <input
            className="user"
            defaultValue={user}
            placeholder="你的名字"
            onBlur={(e) => {
              setCurrentUser(e.target.value);
              setUser(currentUser());
            }}
            onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
          />
        </label>
      </header>

      {error && (
        <div className="notice error" role="alert">
          {error}
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
        {!project && <p className="hint">选择一个 Sirius 项目。</p>}
        {project && view === 'baselines' && <BaselinePage project={project} />}
        {project && view === 'locks' && <LocksPage project={project} user={user} />}
        {project && view === 'matrix' && matrix && <MatrixView matrix={matrix} onAction={onAction} />}
        {project && view === 'matrix' && !matrix && !error && <p className="hint">加载中…</p>}
      </main>
    </div>
  );
}
