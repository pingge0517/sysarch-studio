import { describe, expect, it } from 'vitest';
import { shouldArrange } from './AutoArrange';

describe('a new diagram is arranged once (display quality, 2026-11-14)', () => {
  const fresh = [{ data: { isNew: true } }, { data: { isNew: true } }];
  it('arranges a new diagram of a view with a direction', () => {
    expect(shouldArrange('RIGHT', fresh)).toBe(true);
    expect(shouldArrange('DOWN', fresh)).toBe(true);
  });
  it('leaves views without a direction to Sirius', () => {
    expect(shouldArrange('UNDEFINED', fresh)).toBe(false);
    expect(shouldArrange(null, fresh)).toBe(false);
  });
  it('never rearranges a diagram that has a saved layout, nor an empty one', () => {
    expect(shouldArrange('RIGHT', [{ data: { isNew: true } }, { data: { isNew: false } }])).toBe(false);
    expect(shouldArrange('RIGHT', [{ data: { isNew: false } }])).toBe(false);
    expect(shouldArrange('RIGHT', [])).toBe(false);
  });
});
