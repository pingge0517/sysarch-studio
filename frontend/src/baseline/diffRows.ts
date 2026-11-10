// The diff list as rows, kept apart from React so it is unit tested.
import type { ModelDiff } from './api';

export type ChangeKind = 'added' | 'removed' | 'modified';

export interface DiffRow {
  kind: ChangeKind;
  elementId: string;
  /** For modified: the fields that differ. */
  fields: string[];
  /** Added and modified elements exist in the current model, so the row can open them in Sirius Web. */
  navigable: boolean;
}

const LABELS: Record<string, string> = {
  name: '名称',
  layer: '层',
  type: '类型',
  stereotypes: '构造型',
  description: '说明',
  container: '所属',
};

/** One row per change, in the order added, modified, removed; each group sorted by element id (as the backend sends it). */
export function diffRows(diff: ModelDiff, againstCurrent: boolean): DiffRow[] {
  return [
    ...diff.added.map((elementId) => ({ kind: 'added' as const, elementId, fields: [], navigable: againstCurrent })),
    ...diff.modified.map((m) => ({ kind: 'modified' as const, elementId: m.elementId, fields: m.fields, navigable: againstCurrent })),
    ...diff.removed.map((elementId) => ({ kind: 'removed' as const, elementId, fields: [], navigable: false })),
  ];
}

/** A field name for display: the common ones in Chinese, the others as in the metamodel. */
export function fieldLabel(field: string): string {
  return LABELS[field] ?? field;
}

export function summary(diff: ModelDiff): string {
  return `新增 ${diff.added.length}，修改 ${diff.modified.length}，删除 ${diff.removed.length}`;
}
