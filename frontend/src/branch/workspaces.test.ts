import { describe, expect, it } from 'vitest';
import type { Branch } from './api';
import { effectiveProject, opLabel, trunks } from './workspaces';

const branch = (status: Branch['status']): Branch => ({
  id: 'b1',
  projectId: 'trunk',
  name: '方案A',
  baseBaseline: 'bl',
  baselineLabel: '评审前',
  status,
  createdBy: 'alice',
  createdAt: '2026-11-09T06:00:00Z',
  workspaceProjectId: 'ws',
  workspaceName: '域控 ⎇ 方案A',
  changes: 0,
});

describe('workspaces (P2 T3: branch = workspace switch)', () => {
  it('lists trunks only', () => {
    const projects = [
      { id: 'trunk', name: '域控' },
      { id: 'ws', name: '域控 ⎇ 方案A' },
    ];
    expect(trunks(projects, [branch('OPEN')])).toEqual([{ id: 'trunk', name: '域控' }]);
  });

  it('works on the open branch workspace, else on the trunk', () => {
    expect(effectiveProject('trunk', undefined)).toBe('trunk');
    expect(effectiveProject('trunk', branch('OPEN'))).toBe('ws');
    expect(effectiveProject('trunk', branch('ABANDONED'))).toBe('trunk');
    expect(opLabel('MODIFY')).toBe('修改');
  });
});
