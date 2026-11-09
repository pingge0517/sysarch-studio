import { useEffect, useMemo, useRef, useState } from 'react';
import type { Cell, Matrix, MatrixAction, ValidationError } from './api';
import { actionsFor, cellKey, groupSpans, markedCells, visibleRange } from './layout';

const ROW_HEIGHT = 32;
const COLUMN_WIDTH = 40;
const ROW_GROUP_WIDTH = 92;
const ROW_HEADER_WIDTH = 220;
const COLUMN_GROUP_HEIGHT = 30;
const COLUMN_HEADER_HEIGHT = 150;

interface Props {
  matrix: Matrix;
  /** Runs an action on a cell; resolves to the refusal reasons (empty when it was applied). */
  onAction: (action: MatrixAction, cell: Cell) => Promise<ValidationError[]>;
}

interface Menu {
  x: number;
  y: number;
  cell: Cell;
  actions: MatrixAction[];
}

/**
 * The VSM table as a matrix (P1 T4): row and column headers grouped by layer, the mark symbol and colour from the YAML,
 * click a cell to create or remove a link. Only the visible part of the grid is in the DOM (T4 step 4), so a 50 × 50
 * matrix renders a few hundred cells, not 2500.
 */
export function MatrixView({ matrix, onAction }: Props) {
  const body = useRef<HTMLDivElement>(null);
  const [scroll, setScroll] = useState({ left: 0, top: 0 });
  const [viewport, setViewport] = useState({ width: 800, height: 500 });
  const [hover, setHover] = useState<Cell | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [menu, setMenu] = useState<Menu | null>(null);
  const [flash, setFlash] = useState<Set<string>>(new Set());

  useEffect(() => {
    const element = body.current;
    if (!element) {
      return undefined;
    }
    const observer = new ResizeObserver(() => setViewport({ width: element.clientWidth, height: element.clientHeight }));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const groupLabels = useMemo(() => new Map(matrix.groups.map((g) => [g.key, g.label])), [matrix.groups]);
  const rowSpans = useMemo(() => groupSpans(matrix.rows, groupLabels), [matrix.rows, groupLabels]);
  const columnSpans = useMemo(() => groupSpans(matrix.columns, groupLabels), [matrix.columns, groupLabels]);
  const marked = useMemo(() => markedCells(matrix), [matrix]);

  const rows = visibleRange(scroll.top, viewport.height, ROW_HEIGHT, matrix.rows.length);
  const columns = visibleRange(scroll.left, viewport.width, COLUMN_WIDTH, matrix.columns.length);
  const totalWidth = matrix.columns.length * COLUMN_WIDTH;
  const totalHeight = matrix.rows.length * ROW_HEIGHT;

  async function click(cell: Cell, event: React.MouseEvent) {
    const isMarked = marked.has(cellKey(cell.rowId, cell.columnId));
    const actions = actionsFor(matrix, isMarked);
    if (actions.length === 1) {
      await run(actions[0], cell);
    } else if (actions.length > 1) {
      setMenu({ x: event.clientX, y: event.clientY, cell, actions });
    }
  }

  async function run(action: MatrixAction, cell: Cell) {
    setMenu(null);
    setBusy(cellKey(cell.rowId, cell.columnId));
    try {
      const errors = await onAction(action, cell);
      if (errors.length > 0) {
        const anchors = new Set(errors.flatMap((e) => e.anchors));
        setFlash(anchors);
        window.setTimeout(() => setFlash(new Set()), 4000);
      }
    } finally {
      setBusy(null);
    }
  }

  const visibleCells: JSX.Element[] = [];
  for (let r = rows.start; r < rows.end; r++) {
    const row = matrix.rows[r];
    for (let c = columns.start; c < columns.end; c++) {
      const column = matrix.columns[c];
      const key = cellKey(row.id, column.id);
      const isMarked = marked.has(key);
      const crosshair = hover !== null && (hover.rowId === row.id || hover.columnId === column.id);
      visibleCells.push(
        <button
          key={key}
          type="button"
          className={`cell${isMarked ? ' marked' : ''}${crosshair ? ' crosshair' : ''}${busy === key ? ' busy' : ''}`}
          style={{ left: c * COLUMN_WIDTH, top: r * ROW_HEIGHT, width: COLUMN_WIDTH, height: ROW_HEIGHT, color: matrix.markColor ?? undefined }}
          title={`${row.label} → ${column.label}`}
          aria-label={`${row.label} → ${column.label}${isMarked ? '：' + matrix.markSymbol : ''}`}
          onMouseEnter={() => setHover({ rowId: row.id, columnId: column.id })}
          onClick={(event) => click({ rowId: row.id, columnId: column.id }, event)}
        >
          {isMarked ? matrix.markSymbol : ''}
        </button>,
      );
    }
  }

  return (
    <div
      className="matrix"
      style={{
        gridTemplateColumns: `${ROW_GROUP_WIDTH + ROW_HEADER_WIDTH}px 1fr`,
        gridTemplateRows: `${COLUMN_GROUP_HEIGHT + COLUMN_HEADER_HEIGHT}px 1fr`,
      }}
      onMouseLeave={() => setHover(null)}
    >
      <div className="corner">
        <span className="legend">
          <span style={{ color: matrix.markColor ?? undefined }}>{matrix.markSymbol}</span> 行 → 列
        </span>
        <span className="counts">
          {matrix.rows.length} × {matrix.columns.length}，{matrix.cells.length} 项
        </span>
      </div>

      <div className="column-headers">
        <div style={{ width: totalWidth, height: '100%', transform: `translateX(${-scroll.left}px)` }} className="strip">
          {columnSpans.map((span) => (
            <div key={`g${span.start}`} className="group column-group" style={{ left: span.start * COLUMN_WIDTH, width: span.count * COLUMN_WIDTH, height: COLUMN_GROUP_HEIGHT }}>
              {span.label}
            </div>
          ))}
          {matrix.columns.slice(columns.start, columns.end).map((column, i) => (
            <div
              key={column.id}
              className={`header column-header${hover?.columnId === column.id ? ' crosshair' : ''}${flash.has(column.id) ? ' flash' : ''}`}
              style={{ left: (columns.start + i) * COLUMN_WIDTH, top: COLUMN_GROUP_HEIGHT, width: COLUMN_WIDTH, height: COLUMN_HEADER_HEIGHT }}
              title={column.label}
            >
              <span>{column.label}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="row-headers">
        <div style={{ height: totalHeight, width: '100%', transform: `translateY(${-scroll.top}px)` }} className="strip">
          {rowSpans.map((span) => (
            <div key={`g${span.start}`} className="group row-group" style={{ top: span.start * ROW_HEIGHT, height: span.count * ROW_HEIGHT, width: ROW_GROUP_WIDTH }}>
              {span.label}
            </div>
          ))}
          {matrix.rows.slice(rows.start, rows.end).map((row, i) => (
            <div
              key={row.id}
              className={`header row-header${hover?.rowId === row.id ? ' crosshair' : ''}${flash.has(row.id) ? ' flash' : ''}`}
              style={{ top: (rows.start + i) * ROW_HEIGHT, left: ROW_GROUP_WIDTH, width: ROW_HEADER_WIDTH, height: ROW_HEIGHT }}
              title={row.label}
            >
              {row.label}
            </div>
          ))}
        </div>
      </div>

      <div className="body" ref={body} onScroll={(e) => setScroll({ left: e.currentTarget.scrollLeft, top: e.currentTarget.scrollTop })}>
        <div style={{ width: totalWidth, height: totalHeight, position: 'relative' }}>{visibleCells}</div>
      </div>

      {menu && (
        <div className="menu" style={{ left: menu.x, top: menu.y }} role="menu">
          {menu.actions.map((action) => (
            <button key={action.id} type="button" role="menuitem" onClick={() => run(action, menu.cell)}>
              {action.label}
            </button>
          ))}
          <button type="button" className="cancel" onClick={() => setMenu(null)}>
            取消
          </button>
        </div>
      )}
    </div>
  );
}
