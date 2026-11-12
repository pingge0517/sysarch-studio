// Sirius Web's DefaultExtensionRegistryMergeStrategy replaces a data contribution with ours, so contributing the route
// /sysarch removed Sirius' own routes (its home page became a 404; found in the P4 T3 spike). Lists are concatenated
// instead: Sirius' entries first, then ours. Kept free of Sirius imports so it is tested without loading Sirius.

export interface DataContribution<T> {
  identifier: string;
  data: T;
}

export function mergeData<T>(existing: DataContribution<T>, added: DataContribution<T>): DataContribution<T> {
  if (Array.isArray(existing.data) && Array.isArray(added.data)) {
    return { identifier: added.identifier, data: [...existing.data, ...added.data] as T };
  }
  return added;
}

export function mergeComponents<T>(existing: T[], added: T[]): T[] {
  return [...existing, ...added];
}
