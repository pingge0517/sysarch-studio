import type { MergeConflict, Resolution, Side } from './api';

/** The key of a conflict's choice: element and field ('' for a whole element). */
export function key(conflict: { elementId: string; field: string | null }): string {
  return `${conflict.elementId}#${conflict.field ?? ''}`;
}

/** Choices still missing; DANGLING problems cannot be chosen and always count. */
export function missing(conflicts: MergeConflict[], chosen: Map<string, Side>): MergeConflict[] {
  return conflicts.filter((c) => c.kind === 'DANGLING' || !chosen.has(key(c)));
}

/** The choices as the API takes them. */
export function resolutions(chosen: Map<string, Side>): Resolution[] {
  return [...chosen.entries()].map(([k, take]) => {
    const [elementId, field] = k.split('#');
    return { elementId, field: field === '' ? null : field, take };
  });
}

/** Every conflict one side: allowed only up to 50 conflicts (beyond, each is handled per element, spec §3.3). */
export function chooseAll(conflicts: MergeConflict[], side: Side): Map<string, Side> {
  return new Map(conflicts.filter((c) => c.kind !== 'DANGLING').map((c) => [key(c), side]));
}

const KIND_LABELS: Record<MergeConflict['kind'], string> = {
  FIELD: '字段冲突',
  DELETE_MODIFY: '一方删除、一方修改',
  DUPLICATE_ID: '双方新增了同一 id',
  DANGLING: '引用悬空',
};

export function kindLabel(kind: MergeConflict['kind']): string {
  return KIND_LABELS[kind];
}
