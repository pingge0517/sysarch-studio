import { describe, expect, it } from 'vitest';
import { mergeComponents, mergeData } from './merge';

describe('extension merge (P4 T3)', () => {
  it("adds our routes to Sirius' own instead of replacing them", () => {
    const sirius = { identifier: 'siriusweb_router', data: [{ path: '/projects' }, { path: '/errors/:code' }] };
    const ours = { identifier: 'sysarch_routes', data: [{ path: '/sysarch' }] };
    expect(mergeData(sirius, ours).data.map((r) => r.path)).toEqual(['/projects', '/errors/:code', '/sysarch']);
  });

  it('replaces data that is not a list, as Sirius does', () => {
    expect(mergeData({ identifier: 'a', data: { x: 1 } }, { identifier: 'b', data: { x: 2 } })).toEqual({ identifier: 'b', data: { x: 2 } });
  });

  it('keeps every component contribution', () => {
    expect(mergeComponents(['sirius'], ['sysarch'])).toEqual(['sirius', 'sysarch']);
  });
});
