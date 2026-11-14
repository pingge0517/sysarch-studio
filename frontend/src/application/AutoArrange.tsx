import { useEffect, useRef, useState } from 'react';
import { useNodesInitialized, useReactFlow } from '@xyflow/react';
import type { DiagramPanelActionProps } from '@eclipse-sirius/sirius-components-diagrams';

/**
 * Display quality (2026-11-14): a new diagram is laid out once with Sirius' own arrange-all (ELK layered, in the view's
 * direction: VSM `layoutDirection`, left to right by default) when it first opens, instead of Sirius' default placement
 * that stacks the nodes inside a small frame (state machines, activities) or packs parts edge to edge (IBDs). A diagram
 * whose description has no direction (UNDEFINED) is left to Sirius.
 * "New" = no node has a saved layout yet; once arranged Sirius saves the layout, so later openings and the user's own
 * moves are left alone. Contributed to the diagram toolbar (diagramPanelActionExtensionPoint); renders nothing visible
 * and presses Sirius' arrange-all button.
 */
export function shouldArrange(direction: string | null, nodes: { data?: { isNew?: boolean } }[]): boolean {
  return !!direction && direction !== 'UNDEFINED' && nodes.length > 0 && nodes.every((node) => node.data?.isNew === true);
}

const directionQuery = `query arrangeDirection($editingContextId: ID!, $representationId: ID!) {
  viewer { editingContext(editingContextId: $editingContextId) { representation(representationId: $representationId) {
    description { ... on DiagramDescription { arrangeLayoutDirection } } } } }
}`;

async function arrangeDirection(editingContextId: string, diagramId: string): Promise<string | null> {
  try {
    const response = await fetch('/api/graphql', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: directionQuery, variables: { editingContextId, representationId: diagramId } }),
    });
    const body = await response.json();
    return body?.data?.viewer?.editingContext?.representation?.description?.arrangeLayoutDirection ?? null;
  } catch {
    return null;
  }
}

export function AutoArrange({ editingContextId, diagramId }: DiagramPanelActionProps) {
  const { getNodes } = useReactFlow();
  const initialized = useNodesInitialized();
  const [direction, setDirection] = useState<string | null | undefined>(undefined);
  const decided = useRef(false);
  const anchor = useRef<HTMLSpanElement | null>(null);

  useEffect(() => {
    let live = true;
    void arrangeDirection(editingContextId, diagramId).then((value) => live && setDirection(value));
    return () => {
      live = false;
    };
  }, [editingContextId, diagramId]);

  useEffect(() => {
    if (!initialized || direction === undefined || decided.current) {
      return;
    }
    decided.current = true;
    if (!shouldArrange(direction, getNodes())) {
      return;
    }
    // Sirius' own placement of new nodes happens on the first render; arrange right after it
    const timer = window.setTimeout(() => {
      const panel = anchor.current?.closest('.react-flow') ?? document;
      panel.querySelector<HTMLButtonElement>('[data-testid="arrange-all"]')?.click();
    }, 400);
    return () => window.clearTimeout(timer);
  }, [initialized, direction, getNodes]);

  return <span ref={anchor} hidden data-testid="sysarch-auto-arrange" />;
}
