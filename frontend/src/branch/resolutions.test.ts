import { describe, expect, it } from 'vitest';
import type { MergeConflict, Side } from './api';
import { chooseAll, key, kindLabel, missing, resolutions } from './resolutions';

const field: MergeConflict = { elementId: 'PD-002', field: 'name', kind: 'FIELD', base: 'a', theirs: 'b', ours: 'c' };
const element: MergeConflict = { elementId: 'RQ-005', field: null, kind: 'DELETE_MODIFY', base: 'x', theirs: '（已删除）', ours: 'y' };
const dangling: MergeConflict = { elementId: 'RQ-004', field: 'satisfiedBy', kind: 'DANGLING', base: null, theirs: 'PD-006', ours: '…' };

describe('merge choices (P2 T4: every conflict needs one)', () => {
  it('tracks what is still missing; a dangling reference always blocks', () => {
    const chosen = new Map<string, Side>([[key(field), 'THEIRS']]);
    expect(missing([field, element, dangling], chosen)).toEqual([element, dangling]);
    chosen.set(key(element), 'OURS');
    expect(missing([field, element], chosen)).toEqual([]);
  });

  it('sends whole-element choices without a field', () => {
    const chosen = new Map<string, Side>([
      [key(field), 'THEIRS'],
      [key(element), 'OURS'],
    ]);
    expect(resolutions(chosen)).toEqual([
      { elementId: 'PD-002', field: 'name', take: 'THEIRS' },
      { elementId: 'RQ-005', field: null, take: 'OURS' },
    ]);
  });

  it('chooses all but the dangling ones', () => {
    expect([...chooseAll([field, element, dangling], 'OURS').keys()]).toEqual(['PD-002#name', 'RQ-005#']);
    expect(kindLabel('DELETE_MODIFY')).toBe('一方删除、一方修改');
  });
});
