import { describe, expect, it } from 'vitest';
import { sequenceUrl } from './api';

describe('sequence pictures', () => {
  it('builds the picture URLs with encoded ids', () => {
    expect(sequenceUrl('项目 1', 'SQ/1', 'svg')).toBe('/api/v1/projects/%E9%A1%B9%E7%9B%AE%201/interactions/SQ%2F1/sequence.svg');
    expect(sequenceUrl('p', 'SQ-1', 'png')).toBe('/api/v1/projects/p/interactions/SQ-1/sequence.png');
  });
});
