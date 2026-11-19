import { Component, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApolloClient, ApolloProvider, InMemoryCache } from '@apollo/client';
import { DiagramRepresentation } from '@eclipse-sirius/sirius-components-diagrams';
import { ConfirmationDialogContextProvider, ExtensionProvider, ExtensionRegistry, SelectionContextProvider, ServerContext, ToastContext, theme } from '@eclipse-sirius/sirius-components-core';
import { ThemeProvider } from '@mui/material/styles';
import { MemoryRouter, useInRouterContext } from 'react-router-dom';
import '@xyflow/react/dist/style.css';
import { prepareCanvas, type CanvasData, type NativeDiagram } from './canvasData';
import { createCanvasLink } from './canvasLink';

class CanvasBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <p role="alert">画布显示失败，请重新读取模型</p> : this.props.children; }
}

function Canvas({ data }: { data: CanvasData }) {
  const inRouter = useInRouterContext();
  const contextId = useMemo(() => `polarion-read-${crypto.randomUUID()}`, [data]);
  const registry = useMemo(() => new ExtensionRegistry(), []);
  const [error, setError] = useState('');
  const client = useMemo(() => new ApolloClient({ cache: new InMemoryCache({ addTypename: false }), link: createCanvasLink(data, contextId), defaultOptions: { watchQuery: { fetchPolicy: 'no-cache' }, query: { fetchPolicy: 'no-cache' } } }), [data, contextId]);
  useEffect(() => () => client.stop(), [client]);
  const content = <ThemeProvider theme={theme}><ExtensionProvider registry={registry}><ApolloProvider client={client}>
    <ServerContext.Provider value={{ httpOrigin: window.location.origin }}><ToastContext.Provider value={{ enqueueSnackbar: (message) => setError(message) }}>
      <ConfirmationDialogContextProvider><SelectionContextProvider initialSelection={{ entries: [] }}>
        {error && <p role="alert">{error}</p>}
        <div data-testid="native-read-canvas" style={{ height: 600, width: '100%', position: 'relative', border: '1px solid #ccd3db', overflow: 'hidden' }}>
          <DiagramRepresentation editingContextId={contextId} representationId={data.diagram.id} readOnly />
        </div>
      </SelectionContextProvider></ConfirmationDialogContextProvider>
    </ToastContext.Provider></ServerContext.Provider>
  </ApolloProvider></ExtensionProvider></ThemeProvider>;
  return inRouter ? content : <MemoryRouter>{content}</MemoryRouter>;
}
export function NativeDiagramCanvas({ diagram, label }: { diagram: NativeDiagram; label: string }) {
  const [data, setData] = useState<CanvasData | null>(null), [error, setError] = useState('');
  useEffect(() => { let active = true; setData(null); setError(''); prepareCanvas(diagram, label).then((value) => { if (active) setData(value); }, () => { if (active) setError('图形布局生成失败'); }); return () => { active = false; }; }, [diagram, label]);
  return error ? <p role="alert">{error}</p> : data ? <CanvasBoundary key={data.diagram.id}><Canvas data={data} /></CanvasBoundary> : <p role="status">画布布局中…</p>;
}
