import type { Node, XYPosition } from '@xyflow/react';
import {
  GQLViewModifier,
  convertBorderNodePosition,
  convertHandles,
  convertInsideLabel,
  convertLineStyle,
  convertOutsideLabels,
  defaultHeight,
  defaultWidth,
  isListLayoutStrategy,
} from '@eclipse-sirius/sirius-components-diagrams';
import type {
  GQLDiagram,
  GQLDiagramDescription,
  GQLEdge,
  GQLNode,
  GQLNodeDescription,
  GQLNodeStyle,
  IConvertEngine,
  INodeConverter,
  NodeData,
} from '@eclipse-sirius/sirius-components-diagrams';

/**
 * Sirius Web's ellipse node (server style `EllipseNodeStyle`, node type `customnode:ellipse`): SysArch draws initial
 * states (filled) and use cases (outlined) with it. Its React side is part of Sirius Web's own application, not of the
 * published packages, so the single interface (P4 T3) did not draw these nodes at all, nor the edges at them (found by
 * the end-to-end test, 2026-11-13). This converter turns one into Sirius' built-in free-form node with a round border,
 * so the built-in component, layout handler, connection handles and palette work unchanged. It mirrors the
 * rectangular-node conversion of `@eclipse-sirius/sirius-components-diagrams` (public helpers only).
 */
export interface GQLEllipseNodeStyle extends GQLNodeStyle {
  background: string;
  borderColor: string;
  borderSize: number;
  borderStyle: string;
}

export const ELLIPSE_STYLE = 'EllipseNodeStyle';

const defaultPosition: XYPosition = { x: 0, y: 0 };

export function isEllipse(gqlNode: GQLNode<GQLNodeStyle>): boolean {
  return gqlNode.style.__typename === ELLIPSE_STYLE;
}

export function toEllipseNode(
  gqlDiagram: GQLDiagram,
  gqlNode: GQLNode<GQLEllipseNodeStyle>,
  gqlParentNode: GQLNode<GQLNodeStyle> | null,
  nodeDescription: GQLNodeDescription,
  isBorderNode: boolean,
  gqlEdges: GQLEdge[],
): Node<NodeData> {
  const { targetObjectId, targetObjectLabel, targetObjectKind, descriptionId, id, insideLabel, outsideLabels, state, pinned, style, labelEditable } = gqlNode;
  const customizedStyleProperties = (gqlNode as unknown as { customizedStyleProperties?: string[] }).customizedStyleProperties ?? [];
  const nodeLayoutData = gqlDiagram.layoutData.nodeLayoutData.filter((layout) => layout.id === id);
  const connectionHandles = convertHandles(id, gqlEdges, nodeLayoutData.flatMap((layout) => layout.handleLayoutData));
  const gqlNodeLayoutData = nodeLayoutData[0];
  const data: NodeData = {
    targetObjectId,
    targetObjectLabel,
    targetObjectKind,
    descriptionId,
    style: {
      display: 'flex',
      background: style.background,
      borderColor: style.borderColor,
      borderRadius: '50%',
      borderWidth: style.borderSize,
      borderStyle: convertLineStyle(style.borderStyle),
    },
    insideLabel: null,
    outsideLabels: convertOutsideLabels(outsideLabels, gqlDiagram.layoutData.labelLayoutData),
    imageURL: null,
    faded: state === GQLViewModifier.Faded,
    pinned,
    nodeDescription,
    defaultWidth: gqlNode.defaultWidth,
    defaultHeight: gqlNode.defaultHeight,
    isBorderNode,
    borderNodePosition: convertBorderNodePosition(gqlNode.initialBorderNodePosition),
    labelEditable,
    positionDependentRotation: false,
    connectionHandles,
    isNew: gqlNodeLayoutData === undefined,
    resizedByUser: gqlNodeLayoutData?.resizedByUser ?? false,
    isListChild: isListLayoutStrategy(gqlParentNode?.style.childrenLayoutStrategy),
    isDraggedNode: false,
    isDropNodeTarget: false,
    isDropNodeCandidate: false,
    connectionLinePositionOnNode: 'none',
    isHovered: false,
    nodeAppearanceData: { gqlStyle: style, customizedStyleProperties },
  } as NodeData;
  data.insideLabel = convertInsideLabel(
    insideLabel,
    data,
    `${style.borderSize}px ${style.borderStyle} ${style.borderColor}`,
    gqlNode.childNodes?.some((child) => child.state !== GQLViewModifier.Hidden),
  );

  const node: Node<NodeData> = {
    id,
    type: 'freeFormNode',
    data,
    position: defaultPosition,
    hidden: state === GQLViewModifier.Hidden,
  };
  if (gqlParentNode) {
    node.parentId = gqlParentNode.id;
  }
  if (gqlNodeLayoutData) {
    const { position, size } = gqlNodeLayoutData;
    node.position = position;
    node.height = size.height;
    node.width = size.width;
    node.dragHandle = '.custom-drag-handle';
    node.style = { ...node.style, width: `${size.width}px`, height: `${size.height}px` };
    if (size.height && size.width) {
      node.measured = { height: size.height, width: size.width };
    }
  } else {
    node.height = data.defaultHeight ?? defaultHeight;
    node.width = data.defaultWidth ?? defaultWidth;
  }
  return node;
}

export class EllipseNodeConverter implements INodeConverter {
  canHandle(gqlNode: GQLNode<GQLNodeStyle>): boolean {
    return isEllipse(gqlNode);
  }

  handle(
    convertEngine: IConvertEngine,
    gqlDiagram: GQLDiagram,
    gqlNode: GQLNode<GQLNodeStyle>,
    gqlEdges: GQLEdge[],
    parentNode: GQLNode<GQLNodeStyle> | null,
    isBorderNode: boolean,
    nodes: Node[],
    diagramDescription: GQLDiagramDescription,
    nodeDescriptions: GQLNodeDescription[],
  ): void {
    const nodeDescription = nodeDescriptions.find((description) => description.id === gqlNode.descriptionId);
    if (nodeDescription) {
      nodes.push(toEllipseNode(gqlDiagram, gqlNode as GQLNode<GQLEllipseNodeStyle>, parentNode, nodeDescription, isBorderNode, gqlEdges));
    }
    const described = (ids: string[] | undefined) =>
      (ids ?? []).flatMap((descriptionId) => diagramDescription.nodeDescriptions.filter((description) => description.id === descriptionId));
    convertEngine.convertNodes(gqlDiagram, gqlNode.borderNodes ?? [], gqlNode, nodes, diagramDescription, described(nodeDescription?.borderNodeDescriptionIds));
    convertEngine.convertNodes(gqlDiagram, gqlNode.childNodes ?? [], gqlNode, nodes, diagramDescription, described(nodeDescription?.childNodeDescriptionIds));
  }
}
