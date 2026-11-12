import { describe, expect, it } from 'vitest';
import { embedParams, links } from './api';
import type { EmbedTarget } from './api';

const target: EmbedTarget = {
  polarionProject: 'sysarch',
  siriusProjectId: 'p-1',
  projectName: '域控演示',
  workItemId: 'SYS-7',
  elementId: 'PD-002',
  linkState: 'ACTIVE',
  objectId: 'obj-9',
  path: '/projects/p-1/edit?selection=obj-9',
  note: null,
};

describe('embedding in Polarion', () => {
  it('reads the embed parameters only in embed mode', () => {
    expect(embedParams('?embed=1&polarionProject=sysarch&workItem=SYS-7')).toEqual({ polarionProject: 'sysarch', workItem: 'SYS-7' });
    expect(embedParams('?embed=1&polarionProject=sysarch')).toEqual({ polarionProject: 'sysarch', workItem: '' });
    expect(embedParams('?view=matrix')).toBeNull();
  });

  it('links to the bound element first, then the model and the shell pages, staying in embed mode', () => {
    const all = links(target, (path) => 'http://host:8080' + path);
    expect(all[0].label).toBe('打开模型元素 PD-002');
    expect(all[0].href).toMatch(/:8080\/projects\/p-1\/edit\?selection=obj-9$/);
    const matrix = all.find((l) => l.label === '分配矩阵')!;
    expect(new URLSearchParams(matrix.href.slice(2)).get('embed')).toBe('1');
    expect(new URLSearchParams(matrix.href.slice(2)).get('project')).toBe('p-1');
  });

  it('without a bound element offers the model only', () => {
    const all = links({ ...target, elementId: null, objectId: null, note: 'Work Item SYS-7 没有绑定模型元素' }, (path) => path);
    expect(all[0].label).toBe('打开模型（Sirius Web）');
  });
});
