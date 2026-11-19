import { ApolloLink, gql } from '@apollo/client';
import { afterEach, expect, it, vi } from 'vitest';
import { createCanvasLink } from './canvasLink';
import type { CanvasData } from './canvasData';

const data = { diagram: { id: 'diagram' }, description: { id: 'description' } } as CanvasData;
afterEach(() => vi.unstubAllGlobals());
function request(query: ReturnType<typeof gql>, variables: Record<string, unknown>) {
  return new Promise((resolve, reject) => ApolloLink.execute(createCanvasLink(data, 'read-context'), { query, variables }).subscribe({ next: resolve, error: reject }));
}
it('returns the current description in memory without a network transport', async () => {
  const fetch = vi.fn(() => { throw Error('must not fetch'); }); vi.stubGlobal('fetch', fetch);
  const result = await request(gql`query getDiagramDescription { viewer { editingContext { representation { description { id } } } } }`, { editingContextId: 'read-context', representationId: 'diagram' });
  expect(result).toMatchObject({ data: { viewer: { editingContext: { representation: { description: { id: 'description' } } } } } });
  expect(fetch).not.toHaveBeenCalled();
});
it('rejects every mutation including layout, rename and deletion', async () => {
  for (const name of ['layoutDiagram', 'editLabel', 'deleteFromDiagram']) {
    await expect(request(gql(`mutation ${name} { ${name} { id } }`), {})).rejects.toThrow('只读画布拒绝修改');
  }
});
it('rejects unknown queries and references to another diagram or context', async () => {
  await expect(request(gql`query unknown { viewer { id } }`, {})).rejects.toThrow('拒绝未知');
  const query = gql`query getDiagramDescription { viewer { id } }`;
  await expect(request(query, { editingContextId: 'other', representationId: 'diagram' })).rejects.toThrow('跨视图');
  await expect(request(query, { editingContextId: 'read-context', representationId: 'other' })).rejects.toThrow('跨视图');
});
it('serves a local subscription and can unsubscribe without completing it prematurely', async () => {
  const query = gql`subscription diagramEvent { diagramEvent { id } }`;
  const result = await request(query, { input: { editingContextId: 'read-context', diagramId: 'diagram' } });
  expect(result).toMatchObject({ data: { diagramEvent: { __typename: 'DiagramRefreshedEventPayload', diagram: { id: 'diagram' } } } });
  const next = vi.fn(); const subscription = ApolloLink.execute(createCanvasLink(data, 'read-context'), { query, variables: { input: { editingContextId: 'read-context', diagramId: 'diagram' } } }).subscribe({ next });
  subscription.unsubscribe(); await Promise.resolve(); expect(next).not.toHaveBeenCalled();
});
