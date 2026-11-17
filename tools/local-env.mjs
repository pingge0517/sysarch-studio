// Reads the repository's local KEY=value / export KEY=value file without evaluating shell expressions.
// Callers must never print the returned values or put them into command-line arguments.
import { readFileSync } from 'node:fs';

export function readLocalEnv(path = '.env') {
  const values = {};
  for (const line of readFileSync(path, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const match = line.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    values[match[1]] = value;
  }
  return values;
}
