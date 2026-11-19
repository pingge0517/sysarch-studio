import { ApolloLink, Observable } from '@apollo/client';
import { getMainDefinition } from '@apollo/client/utilities';
import type { CanvasData } from './canvasData';

/** In-memory adapter only: deliberately contains no HTTP/WebSocket link, credentials, or server context id. */
export function createCanvasLink(data: CanvasData, contextId: string): ApolloLink {
  return new ApolloLink((operation) => new Observable((observer) => {
    const definition = getMainDefinition(operation.query);
    if (definition.kind !== 'OperationDefinition' || definition.operation === 'mutation') {
      observer.error(new Error('只读画布拒绝修改操作')); return;
    }
    if (definition.operation === 'query' && operation.operationName === 'getDiagramDescription'
      && operation.variables.editingContextId === contextId && operation.variables.representationId === data.diagram.id) {
      observer.next({ data: { viewer: { editingContext: { representation: { description: data.description } } } } }); observer.complete(); return;
    }
    if (definition.operation === 'subscription' && operation.operationName === 'diagramEvent'
      && operation.variables.input?.editingContextId === contextId && operation.variables.input?.diagramId === data.diagram.id) {
      let active = true;
      queueMicrotask(() => { if (active) observer.next({ data: { diagramEvent: { __typename: 'DiagramRefreshedEventPayload', id: crypto.randomUUID(), diagram: data.diagram, cause: 'refresh', referencePosition: null } } }); });
      return () => { active = false; };
    }
    observer.error(new Error('只读画布拒绝未知或跨视图请求'));
  }));
}
