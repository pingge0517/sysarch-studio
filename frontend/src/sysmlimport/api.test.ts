import { describe, expect, it } from 'vitest';
import { countsText } from './api';

describe('import report (P2 T6)', () => {
  it('lists the counts by kind in a stable order', () => {
    expect(countsText({ requirement: 5, 'part def': 6, allocation: 3, part: 3, port: 6, connection: 4, 'port def': 1 })).toBe(
      '块 6、部件 3、接口定义 1、端口 6、连接 4、需求 5、分配 3',
    );
    expect(countsText({})).toBe('');
    expect(countsText({ message: 5, 'occurrence def': 1, lifeline: 3 })).toBe('交互 1、生命线 3、消息 5');
  });
});
