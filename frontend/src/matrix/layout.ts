// Pure layout and interaction rules of the matrix page, kept apart from React so they are unit tested.
import type { Header, Matrix, MatrixAction } from './api';

/** The slice [start, end) of items of a fixed size that a viewport shows, with some overscan on both sides. */
export function visibleRange(scroll: number, viewport: number, itemSize: number, count: number, overscan = 4): { start: number; end: number } {
  if (count === 0 || itemSize <= 0) {
    return { start: 0, end: 0 };
  }
  const first = Math.floor(Math.max(0, scroll) / itemSize);
  const last = Math.ceil((Math.max(0, scroll) + Math.max(0, viewport)) / itemSize);
  return { start: Math.max(0, first - overscan), end: Math.min(count, last + overscan) };
}

export interface GroupSpan {
  key: string | null;
  label: string;
  start: number;
  count: number;
}

/** Consecutive headers with the same group form one span (the backend orders headers by group). */
export function groupSpans(headers: Header[], groupLabels: Map<string, string>): GroupSpan[] {
  const spans: GroupSpan[] = [];
  headers.forEach((header, index) => {
    const last = spans[spans.length - 1];
    if (last && last.key === header.group) {
      last.count += 1;
    } else {
      spans.push({ key: header.group, label: header.group === null ? '' : groupLabels.get(header.group) ?? header.group, start: index, count: 1 });
    }
  });
  return spans;
}

export function cellKey(rowId: string, columnId: string): string {
  return `${rowId}\u0000${columnId}`;
}

export function markedCells(matrix: Matrix): Set<string> {
  return new Set(matrix.cells.map((cell) => cellKey(cell.rowId, cell.columnId)));
}

/** What a click on a cell may do: on a marked cell the delete actions, on an empty one the create/link actions. */
export function actionsFor(matrix: Matrix, marked: boolean): MatrixAction[] {
  return matrix.actions.filter((action) => action.onMarked === marked);
}
