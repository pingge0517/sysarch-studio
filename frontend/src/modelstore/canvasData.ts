import ELK from 'elkjs/lib/elk.bundled.js';
import type { GQLDiagram, GQLDiagramDescription, GQLNode, GQLNodeStyle } from '@eclipse-sirius/sirius-components-diagrams';

export interface NativeDiagram {
  id: string; targetObjectId: string; descriptionId: string; kind: string;
  nodes: GQLNode<GQLNodeStyle>[]; edges: GQLDiagram['edges'];
}
export interface CanvasData { diagram: GQLDiagram; description: GQLDiagramDescription & { childNodeDescriptionIds: string[]; __typename: string } }

/** Adapt the native REST serialization to the published Sirius GraphQL shape; renderers remain unchanged. */
export async function prepareCanvas(source: NativeDiagram, label: string): Promise<CanvasData> {
  const copy = structuredClone(source);
  const descriptions = new Map<string, { id: string; childNodeDescriptionIds: string[]; borderNodeDescriptionIds: string[]; userResizable: 'NONE'; keepAspectRatio: boolean }>();
  const positions: GQLDiagram['layoutData']['nodeLayoutData'] = [];
  const elk = new ELK();
  function register(node: GQLNode<GQLNodeStyle>) {
    const previous = descriptions.get(node.descriptionId);
    descriptions.set(node.descriptionId, { id: node.descriptionId,
      childNodeDescriptionIds: [...new Set([...(previous?.childNodeDescriptionIds ?? []), ...(node.childNodes ?? []).map((child) => child.descriptionId)])],
      borderNodeDescriptionIds: [...new Set([...(previous?.borderNodeDescriptionIds ?? []), ...(node.borderNodes ?? []).map((child) => child.descriptionId)])],
      userResizable: 'NONE', keepAspectRatio: false });
  }
  async function level(nodes: GQLNode<GQLNodeStyle>[]): Promise<void> {
    for (const node of nodes) {
      if (node.type !== 'node:rectangle') throw new Error(`当前只读画布尚不支持节点类型：${node.type}`);
      node.childNodes ??= []; node.borderNodes ??= []; node.outsideLabels ??= [];
      node.labelEditable = false;
      node.style = { ...node.style, __typename: 'RectangularNodeStyle', childrenLayoutStrategy: { ...node.style.childrenLayoutStrategy, __typename: 'FreeFormLayoutStrategy' } };
      register(node);
      await level(node.childNodes);
      for (const border of node.borderNodes) {
        border.childNodes ??= []; border.borderNodes ??= []; border.outsideLabels ??= []; border.labelEditable = false;
        border.style = { ...border.style, __typename: 'RectangularNodeStyle', childrenLayoutStrategy: { __typename: 'FreeFormLayoutStrategy', kind: 'FreeForm' } };
        register(border);
      }
      const children = positions.filter((position) => node.childNodes?.some((child) => child.id === position.id));
      node.size = { width: Math.max(240, ...children.map((child) => child.position.x + child.size.width + 40)), height: Math.max(node.defaultHeight ?? 120, ...children.map((child) => child.position.y + child.size.height + 60)) };
    }
    if (!nodes.length) return;
    const ids = new Set(nodes.map((node) => node.id));
    const layout = await elk.layout({ id: 'read-only-layout', layoutOptions: { 'elk.algorithm': 'layered', 'elk.direction': 'RIGHT', 'elk.spacing.nodeNode': '60', 'elk.padding': '[top=60,left=30,bottom=30,right=30]' },
      children: nodes.map((node) => ({ id: node.id, width: node.size.width, height: node.size.height })),
      edges: copy.edges.filter((edge) => ids.has(edge.sourceId) && ids.has(edge.targetId)).map((edge) => ({ id: edge.id, sources: [edge.sourceId], targets: [edge.targetId] })) });
    for (const node of nodes) {
      const item = layout.children?.find((child) => child.id === node.id);
      node.position = { x: item?.x ?? 30, y: item?.y ?? 60 };
      positions.push({ id: node.id, position: node.position, size: node.size, resizedByUser: false, handleLayoutData: [] });
    }
  }
  await level(copy.nodes);
  for (const edge of copy.edges) { edge.routingPoints ??= []; edge.centerLabelEditable = false; }
  return { diagram: { ...copy, metadata: { kind: copy.kind, label }, layoutData: { nodeLayoutData: positions, edgeLayoutData: [], labelLayoutData: [] } },
    description: { __typename: 'DiagramDescription', id: source.descriptionId, nodeDescriptions: [...descriptions.values()], childNodeDescriptionIds: copy.nodes.map((node) => node.descriptionId), dropNodeCompatibility: [], debug: false, arrangeLayoutDirection: 'RIGHT' } };
}
