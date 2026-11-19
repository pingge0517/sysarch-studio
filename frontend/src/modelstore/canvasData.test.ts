import { expect, it } from 'vitest';
import { prepareCanvas, type NativeDiagram } from './canvasData';

function node(id: string, children: unknown[] = []) {
  return { id, type: 'node:rectangle', descriptionId: 'shared', childNodes: children, borderNodes: [], style: { childrenLayoutStrategy: { kind: 'FreeForm' } } };
}
it('keeps source data immutable and merges shared description children across instances', async () => {
  const child = { ...node('child'), descriptionId: 'child-description' };
  const source = { id: 'diagram', descriptionId: 'bdd', targetObjectId: 'root', kind: 'Diagram', nodes: [node('one', [child]), node('two')], edges: [] } as unknown as NativeDiagram;
  const before = JSON.stringify(source);
  const result = await prepareCanvas(source, 'BDD');
  expect(JSON.stringify(source)).toBe(before);
  expect(result.description.nodeDescriptions.find((entry) => entry.id === 'shared')?.childNodeDescriptionIds).toEqual(['child-description']);
  expect(result.diagram.nodes.every((entry) => entry.labelEditable === false)).toBe(true);
  expect(result.diagram.nodes[0].position).not.toEqual(result.diagram.nodes[1].position);
});
it('rejects unsupported node types instead of silently losing model elements', async () => {
  const source = { nodes: [{ ...node('unknown'), type: 'custom:unknown' }], edges: [] } as unknown as NativeDiagram;
  await expect(prepareCanvas(source, 'BDD')).rejects.toThrow();
});
