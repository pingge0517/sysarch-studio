/** Read-only sequence pictures (probe A's fallback): the interactions of a project and their SVG / PNG URLs. */
export interface InteractionInfo {
  id: string;
  name: string | null;
  lifelines: number;
  messages: number;
}

export async function listInteractions(project: string): Promise<InteractionInfo[]> {
  const response = await fetch(`/api/v1/projects/${encodeURIComponent(project)}/interactions`);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<InteractionInfo[]>;
}

export function sequenceUrl(project: string, id: string, format: 'svg' | 'png'): string {
  return `/api/v1/projects/${encodeURIComponent(project)}/interactions/${encodeURIComponent(id)}/sequence.${format}`;
}
