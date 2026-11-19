// M0 read-only capability probe. No Polarion writes, redirects, response bodies or credentials in the report.
import { readLocalEnv } from './local-env.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function probe({ baseUrl, token, fetchImpl = fetch }) {
  const base = new URL(baseUrl);
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) {
    throw new Error('Invalid Polarion URL');
  }
  if (base.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) {
    throw new Error('Remote Polarion probes require HTTPS');
  }
  const rest = base.href.replace(/\/+$/, '') + '/rest/v1';
  const results = [];
  for (const [name, path] of [['metadata', '/metadata'], ['projects', '/projects?page[size]=1']]) {
    try {
      const response = await fetchImpl(rest + path, {
        method: 'GET', redirect: 'error', signal: AbortSignal.timeout(15000),
        headers: { Accept: 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      });
      const isJson = /(?:application\/json|application\/vnd\.api\+json)/i.test(response.headers.get('content-type') ?? '');
      // Do not parse potentially large or sensitive response content. Status alone proves only endpoint reachability.
      await response.body?.cancel();
      results.push({ name, status: response.status, jsonContentType: isJson, accessible: response.ok && isJson });
    } catch {
      results.push({ name, status: null, accessible: false, error: 'request-failed-or-timed-out' });
    }
  }
  return {
    schemaVersion: '1.0', performedAt: new Date().toISOString(), mode: 'read-only', results,
    remoteWritesVerified: false, atomicityVerified: false, concurrencyVerified: false,
    delegatedIdentityVerified: false, writesAllowed: false,
    pending: ['target-sdk-extension', 'atomic-rollback', 'cross-process-version-conflict', 'two-account-identity',
      'idempotency-after-timeout', 'managed-field-protection', 'payload-size-limits'],
  };
}

async function main() {
  const args = process.argv.slice(2);
  const outputIndex = args.indexOf('--output');
  const envIndex = args.indexOf('--env-file');
  const seen = new Set();
  for (let i = 0; i < args.length; i += 2) {
    if (!['--output', '--env-file'].includes(args[i]) || seen.has(args[i]) || !args[i + 1] || args[i + 1].startsWith('--')) {
      throw new Error('Use --output <workspace-build-file> and optional --env-file <local-credential-file>');
    }
    seen.add(args[i]);
  }
  const output = resolve(outputIndex >= 0 ? args[outputIndex + 1] ?? '' : 'build/polarion-store/m0-readonly.json');
  const buildRoot = resolve('build') + sep;
  if (!output.startsWith(buildRoot) || !output.endsWith('.json')) throw new Error('Output must be a JSON file inside workspace build/');
  const config = readLocalEnv(envIndex >= 0 ? args[envIndex + 1] : '.env');
  const fallback = !config.POLARION_BASE_URL;
  const result = await probe({ baseUrl: config.POLARION_BASE_URL || 'http://localhost/polarion', token: config.POLARION_TOKEN });
  result.usedLocalUrlFallback = fallback;
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ report: output, results: result.results, writesAllowed: false }));
  if (result.results.some(r => !r.accessible)) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error('Read-only probe failed; verify URL, credential file and local permissions. No credentials logged.'); process.exitCode = 1; });
}
