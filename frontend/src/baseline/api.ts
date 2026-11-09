// The baseline API of the backend (P1 T7, spec P1 §4.1 /baselines) and the element lookup for jumping to Sirius Web.

export interface Baseline {
  id: string;
  projectId: string;
  label: string;
  createdAt: string;
  elementCount: number;
  modelRev: number;
}

export interface ModelDiff {
  added: string[];
  removed: string[];
  modified: { elementId: string; fields: string[] }[];
}

export interface Location {
  elementId: string;
  siriusProjectId: string;
  objectId: string;
  /** Path of Sirius Web's editor with the element selected. */
  path: string;
}

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      detail = (await response.json()).detail ?? detail;
    } catch {
      // not JSON
    }
    throw new Error(detail);
  }
  return response.json() as Promise<T>;
}

export async function listBaselines(project: string): Promise<Baseline[]> {
  return json(await fetch(`/api/v1/baselines?projectId=${encodeURIComponent(project)}`));
}

export async function createBaseline(project: string, label: string, description?: string): Promise<Baseline> {
  return json(
    await fetch('/api/v1/baselines', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId: project, label, description }),
    }),
  );
}

/** {@code other} null: against the current model. */
export async function diffBaseline(baselineId: string, other: string | null): Promise<ModelDiff> {
  const query = other ? `?against=BASELINE&otherBaselineId=${encodeURIComponent(other)}` : '?against=CURRENT';
  return json(await fetch(`/api/v1/baselines/${encodeURIComponent(baselineId)}/diff${query}`));
}

export async function locate(project: string, elementId: string): Promise<Location> {
  return json(await fetch(`/api/v1/projects/${encodeURIComponent(project)}/elements/${encodeURIComponent(elementId)}/location`));
}

/** Sirius Web is served by the backend, by default on port 8080 of the same host (VITE_SIRIUS_URL overrides it). */
export function siriusUrl(path: string): string {
  const base = (import.meta.env.VITE_SIRIUS_URL as string | undefined) ?? `${window.location.protocol}//${window.location.hostname}:8080`;
  return base.replace(/\/+$/, '') + path;
}
