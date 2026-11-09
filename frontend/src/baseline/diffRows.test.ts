import { describe, expect, it } from 'vitest';
import { diffRows, fieldLabel, summary } from './diffRows';

const diff = {
  added: ['PD-100'],
  removed: ['RQ-004'],
  modified: [{ elementId: 'PD-002', fields: ['name', 'satisfiedBy'] }],
};

describe('diff rows', () => {
  it('lists added, modified, then removed', () => {
    expect(diffRows(diff, true).map((r) => `${r.kind} ${r.elementId}`)).toEqual(['added PD-100', 'modified PD-002', 'removed RQ-004']);
  });

  /** T7 step 3: modified rows jump to their element; a removed element no longer exists to jump to. */
  it('lets added and modified rows open the element when compared with the current model', () => {
    expect(diffRows(diff, true).map((r) => r.navigable)).toEqual([true, true, false]);
    expect(diffRows(diff, false).every((r) => !r.navigable)).toBe(true);
  });

  it('labels fields and summarises', () => {
    expect(fieldLabel('name')).toBe('名称');
    expect(fieldLabel('satisfiedBy')).toBe('satisfiedBy');
    expect(summary(diff)).toBe('新增 1，修改 1，删除 1');
  });
});
