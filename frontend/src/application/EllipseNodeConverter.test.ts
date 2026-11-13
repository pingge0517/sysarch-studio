import { describe, expect, it } from 'vitest';
import { EllipseNodeConverter, toEllipseNode } from './EllipseNodeConverter';

// A Sirius diagram as the subscription delivers it: one use case (ellipse) linked to an actor, one laid-out ellipse.
const style = { __typename: 'EllipseNodeStyle', background: '#ffffff', borderColor: '#33691e', borderSize: 1, borderStyle: 'Solid', childrenLayoutStrategy: { __typename: 'FreeFormLayoutStrategy', kind: 'FreeForm' } };
const label = (id: string, text: string) => ({
  id, text, iconURL: [], insideLabelLocation: 'TOP_CENTER', overflowStrategy: 'NONE', textAlign: 'CENTER', isHeader: false, headerSeparatorDisplayMode: 'NEVER',
  style: { color: '#000', fontSize: 12, bold: false, italic: false, underline: false, strikeThrough: false, iconURL: [], background: 'transparent', borderColor: 'black', borderSize: 0, borderRadius: 0, borderStyle: 'Solid', maxWidth: null },
  customizedStyleProperties: [],
});
const gqlNode = (id: string, withLayout: boolean) => ({
  id, type: 'customnode:ellipse', descriptionId: 'uc', targetObjectId: 'obj-' + id, targetObjectLabel: '开车门', targetObjectKind: 'UseCase', state: 'Normal', pinned: false, labelEditable: true,
  style, insideLabel: label('l-' + id, '开车门'), outsideLabels: [], borderNodes: [], childNodes: [], defaultWidth: 120, defaultHeight: 60, initialBorderNodePosition: 'EAST',
  customizedStyleProperties: [], withLayout,
});
const diagram = {
  id: 'd', metadata: {}, nodes: [gqlNode('n1', true), gqlNode('n2', false)], edges: [],
  layoutData: { nodeLayoutData: [{ id: 'n1', position: { x: 10, y: 20 }, size: { width: 140, height: 70 }, resizedByUser: false, handleLayoutData: [] }], edgeLayoutData: [], labelLayoutData: [] },
};
const description = { id: 'uc', borderNodeDescriptionIds: [], childNodeDescriptionIds: [], userResizable: 'BOTH', keepAspectRatio: false };

describe('ellipse nodes in the single interface (end-to-end finding 2026-11-13)', () => {
  it('handles exactly the ellipse style', () => {
    const converter = new EllipseNodeConverter();
    expect(converter.canHandle(gqlNode('a', true) as never)).toBe(true);
    expect(converter.canHandle({ ...gqlNode('b', true), style: { ...style, __typename: 'RectangularNodeStyle' } } as never)).toBe(false);
  });

  it("becomes Sirius' free-form node with a round border, at its saved place", () => {
    const node = toEllipseNode(diagram as never, diagram.nodes[0] as never, null, description as never, false, []);
    expect(node.type).toBe('freeFormNode');
    expect(node.data.style).toMatchObject({ borderRadius: '50%', background: '#ffffff', borderColor: '#33691e', borderWidth: 1, borderStyle: 'solid' });
    expect(node.position).toEqual({ x: 10, y: 20 });
    expect([node.width, node.height]).toEqual([140, 70]);
    expect(node.data.insideLabel?.text).toBe('开车门');
    expect(node.data.isNew).toBe(false);
  });

  it('takes its default size before the first layout and converts through the engine', () => {
    const nodes: unknown[] = [];
    const engine = { convertNodes: () => undefined };
    new EllipseNodeConverter().handle(engine as never, diagram as never, diagram.nodes[1] as never, [], null, false, nodes as never, { nodeDescriptions: [description] } as never, [description] as never);
    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toMatchObject({ id: 'n2', type: 'freeFormNode', width: 120, height: 60, data: { isNew: true } });
  });
});
