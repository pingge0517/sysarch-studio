import { describe, expect, it } from 'vitest';
import type { Matrix } from './api';
import { cellKey, groupSpans, markedCells, optionsFor, visibleRange } from './layout';

describe('visibleRange', () => {
  it('covers the viewport plus overscan', () => {
    expect(visibleRange(0, 320, 32, 50, 4)).toEqual({ start: 0, end: 14 });
    expect(visibleRange(640, 320, 32, 50, 4)).toEqual({ start: 16, end: 34 });
  });

  it('is clamped to the items that exist', () => {
    expect(visibleRange(1500, 320, 32, 50, 4)).toEqual({ start: 42, end: 50 });
    expect(visibleRange(-20, 100, 32, 3, 4)).toEqual({ start: 0, end: 3 });
    expect(visibleRange(0, 100, 32, 0)).toEqual({ start: 0, end: 0 });
  });

  /** T4 step 4: 50 × 50 renders only what is visible, not 2500 cells. */
  it('renders a fraction of a 50 x 50 matrix', () => {
    const rows = visibleRange(0, 600, 32, 50);
    const columns = visibleRange(0, 900, 36, 50);
    expect((rows.end - rows.start) * (columns.end - columns.start)).toBeLessThan(2500 / 2);
  });
});

describe('groupSpans', () => {
  const labels = new Map([
    ['SYSTEM', 'SA 系统分析'],
    ['LOGICAL', 'LA 逻辑架构'],
  ]);

  it('merges consecutive headers of one group', () => {
    const spans = groupSpans(
      [
        { id: 'a', label: 'A', group: 'SYSTEM' },
        { id: 'b', label: 'B', group: 'LOGICAL' },
        { id: 'c', label: 'C', group: 'LOGICAL' },
      ],
      labels,
    );
    expect(spans).toEqual([
      { key: 'SYSTEM', label: 'SA 系统分析', start: 0, count: 1 },
      { key: 'LOGICAL', label: 'LA 逻辑架构', start: 1, count: 2 },
    ]);
  });

  it('keeps ungrouped headers as one unlabelled span', () => {
    expect(groupSpans([{ id: 'a', label: 'A', group: null }, { id: 'b', label: 'B', group: null }], labels)).toEqual([
      { key: null, label: '', start: 0, count: 2 },
    ]);
  });
});

describe('cell actions', () => {
  const matrix: Matrix = {
    tableId: 'allocation-matrix',
    label: '分配矩阵',
    markSymbol: '●',
    markColor: '#1C3D2E',
    groups: [],
    rows: [],
    columns: [],
    cells: [{ rowId: 'r', columnId: 'c' }],
    actions: [
      { id: 'createAllocate', label: '建立分配', kind: 'CREATE_LINK', onMarked: false, undoLabel: null },
      { id: 'deleteAllocate', label: '取消分配', kind: 'DELETE_LINK', onMarked: true, undoLabel: null },
    ],
  };

  it('offers create on an empty cell and delete on a marked one', () => {
    expect(optionsFor(matrix, undefined).map((o) => o.label)).toEqual(['建立分配']);
    expect(optionsFor(matrix, matrix.cells[0]).map((o) => o.label)).toEqual(['取消分配']);
  });

  /** P1 T5 (user's decision): a link the cell holds is offered for removal, the others for adding. */
  it('offers to remove the links a trace cell holds and to add the others', () => {
    const trace: Matrix = {
      ...matrix,
      cells: [{ rowId: 'RQ-1', columnId: 'PD-1', links: ['satisfy'] }],
      actions: [
        { id: 'satisfy', label: '建立满足关系', kind: 'LINK', onMarked: false, undoLabel: '取消满足关系' },
        { id: 'verify', label: '建立验证关系', kind: 'LINK', onMarked: false, undoLabel: '取消验证关系' },
      ],
    };
    expect(optionsFor(trace, trace.cells[0]).map((o) => [o.label, o.remove])).toEqual([
      ['取消满足关系', true],
      ['建立验证关系', false],
    ]);
    expect(optionsFor(trace, undefined).map((o) => o.label)).toEqual(['建立满足关系', '建立验证关系']);
  });

  it('knows which cells are marked', () => {
    expect(markedCells(matrix).has(cellKey('r', 'c'))).toBe(true);
    expect(markedCells(matrix).has(cellKey('c', 'r'))).toBe(false);
  });
});
