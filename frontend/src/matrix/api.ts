// The table API of the backend (TableController, P1 T4). Same origin: Vite (dev) and nginx (docker) proxy /api.

export interface Header {
  id: string;
  label: string;
  /** The groupBy value, e.g. "LOGICAL", or null. */
  group: string | null;
}

export interface Group {
  key: string;
  label: string;
}

export interface Cell {
  rowId: string;
  columnId: string;
  /** On a marked cell: the link actions whose relation it holds (e.g. "satisfy"). */
  links?: string[];
}

export interface MatrixAction {
  id: string;
  label: string;
  kind: 'CREATE_LINK' | 'DELETE_LINK' | 'LINK';
  /** Applies to a marked cell (delete-link) rather than an empty one. */
  onMarked: boolean;
  /** For a link action: the label of removing its relation, e.g. 取消满足关系. */
  undoLabel: string | null;
}

export interface Matrix {
  tableId: string;
  label: string;
  markSymbol: string;
  markColor: string | null;
  groups: Group[];
  rows: Header[];
  columns: Header[];
  cells: Cell[];
  actions: MatrixAction[];
}

/** Spec §3.2's error shape. */
export interface ValidationError {
  code: string;
  elementId: string | null;
  message: string;
  anchors: string[];
}

export interface ActionResponse {
  changed: boolean;
  errors: ValidationError[];
  matrix: Matrix;
}

export interface TableInfo {
  id: string;
  label: string;
}

export interface Project {
  id: string;
  name: string;
}

async function json<T>(response: Response): Promise<T> {
  // 422: refused by a constraint; 423: an element is locked by someone else (P2 T1); 429: the project has its maximum
  // of editors (P2 T2). All carry the matrix.
  if (!response.ok && response.status !== 422 && response.status !== 423 && response.status !== 429) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      detail = problem.detail ?? detail;
    } catch {
      // not JSON
    }
    throw new Error(detail);
  }
  return response.json() as Promise<T>;
}

export async function fetchTables(): Promise<TableInfo[]> {
  return json(await fetch('/api/v1/tables'));
}

/** Sirius Web's projects, through its GraphQL API. */
export async function fetchProjects(): Promise<Project[]> {
  const response = await fetch('/api/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: 'query { viewer { projects(first: 100) { edges { node { id name } } } } }' }),
  });
  const body = await json<{ data: { viewer: { projects: { edges: { node: Project }[] } } } }>(response);
  return body.data.viewer.projects.edges.map((edge) => edge.node);
}

export async function fetchMatrix(project: string, table: string): Promise<Matrix> {
  return json(await fetch(`/api/v1/projects/${encodeURIComponent(project)}/tables/${encodeURIComponent(table)}`));
}

export async function runAction(project: string, table: string, actionId: string, cell: Cell, remove = false): Promise<ActionResponse> {
  const response = await fetch(`/api/v1/projects/${encodeURIComponent(project)}/tables/${encodeURIComponent(table)}/actions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ actionId, rowId: cell.rowId, columnId: cell.columnId, remove }),
  });
  return json(response);
}
