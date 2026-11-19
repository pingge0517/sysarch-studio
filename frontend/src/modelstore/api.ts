import { json } from '../identity/auth';
import type { Matrix } from '../matrix/api';
import type { NativeDiagram } from './canvasData';

export interface Location { server: string; root: { project: string; workItem: string; revision: string | null }; modelUuid: string }
export interface ModelSummary { location: Location; title: string }
export interface ModelPage { models: ModelSummary[]; page: number; pageSize: number; mayHaveMore: boolean }
export interface ModelNode { uuid: string; type: string; attributes: Record<string, string[]>; references: Record<string, string[]> }
export interface Snapshot { subject: string; version: number; commitId: string; model: {
  modelUuid: string; documents: { documentUuid: string; roots: string[] }[]; nodes: ModelNode[];
}; requirements?: { uuid: string; item: { project: string; workItem: string; revision: string } }[];
views?: { id: string; label: string; kind: 'TABLE' | 'DIAGRAM'; rootType: string | null }[] }
export interface ReadView { subject: string; version: number; commitId: string; representation: { viewId: string; matrix: Matrix | null; diagram?: NativeDiagram | null } }

const options = (token: string, signal: AbortSignal): RequestInit => ({
  method: 'GET', headers: { Authorization: `Bearer ${token}` }, credentials: 'omit', cache: 'no-store', redirect: 'error', signal,
});
export async function listModels(project: string, page: number, token: string, signal: AbortSignal): Promise<ModelPage> {
  return json(await fetch(`/api/v1/model-store/projects/${encodeURIComponent(project)}/models?${new URLSearchParams({ page: String(page), pageSize: '25' })}`, options(token, signal)));
}
export async function readModel(model: ModelSummary, token: string, signal: AbortSignal): Promise<Snapshot> {
  const { root, modelUuid } = model.location;
  return json(await fetch(`/api/v1/model-store/projects/${encodeURIComponent(root.project)}/models/${encodeURIComponent(root.workItem)}/snapshot?${new URLSearchParams({ modelUuid })}`, options(token, signal)));
}
export async function readView(model: ModelSummary, view: string, token: string, signal: AbortSignal, elementUuid?: string): Promise<ReadView> {
  const { root, modelUuid } = model.location;
  const query = new URLSearchParams({ modelUuid }); if (elementUuid) query.set('elementUuid', elementUuid);
  return json(await fetch(`/api/v1/model-store/projects/${encodeURIComponent(root.project)}/models/${encodeURIComponent(root.workItem)}/views/${encodeURIComponent(view)}?${query}`, options(token, signal)));
}
