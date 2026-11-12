import { describe, expect, it } from 'vitest';
import type { AccessGrant, Me } from './auth';
import { badge, bindingUrl, can, parseRoles } from './auth';

const me = (user: string | null): Me => ({ mode: 'polarion', user, name: user ? 'Rita' : null, source: 'polarion', globalRoles: [], projectRoles: {} });
const reviewer: AccessGrant = {
  user: 'rita',
  projectId: 'p',
  polarionProject: 'sysarch',
  role: 'REVIEWER',
  roleLabel: '评审人',
  permissions: ['COMMENT', 'REPORT'],
};

describe('sign-in (P2 T8)', () => {
  it('shows the user with their role in the project, or that they only read', () => {
    expect(badge(me('rita'), reviewer)).toBe('Rita（评审人）');
    expect(badge(me('rita'), null)).toBe('Rita');
    expect(badge(me(null), null)).toBe('未登录（只能查看）');
    expect(badge(null, reviewer)).toBe('未登录（只能查看）');
  });

  it('allows only what the role grants', () => {
    expect(can(reviewer, 'COMMENT')).toBe(true);
    expect(can(reviewer, 'MODEL_EDIT')).toBe(false);
    expect(can(null, 'REPORT')).toBe(false);
  });

  it('reads dev-mode roles separated by commas or spaces', () => {
    expect(parseRoles('global:admin, sysarch/project_user，x')).toEqual(['global:admin', 'sysarch/project_user', 'x']);
    expect(parseRoles('  ')).toEqual([]);
  });
});

describe('project bindings (P4 T1)', () => {
  it('puts the Polarion project id into the path, encoded', () => {
    expect(bindingUrl('sysarch')).toBe('/api/v1/project-bindings/sysarch');
    expect(bindingUrl('a b/c')).toBe('/api/v1/project-bindings/a%20b%2Fc');
  });
});
