// Read-only verification of the two user-supplied credential files. Never prints credentials or response bodies.
import { readLocalEnv } from './local-env.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';

const projects = ['sysarch_probe_a', 'sysarch_probe_b'];
const results = [];
for (const [label, path] of [['A', 'build/private/polarion-test-a.env'], ['B', 'build/private/polarion-test-b.env']]) {
  let config;
  try { config = readLocalEnv(path); } catch { results.push({ account: label, error: 'credential-file-unreadable' }); continue; }
  if (!config.POLARION_TOKEN || /这里|填写|your[_-]?token|placeholder|<.*>/i.test(config.POLARION_TOKEN)) {
    results.push({ account: label, error: 'missing-or-placeholder-token' }); continue;
  }
  if ((config.POLARION_BASE_URL ?? '').replace(/\/+$/, '') !== 'http://localhost/polarion') {
    results.push({ account: label, error: 'unexpected-target-url' }); continue;
  }
  async function get(path) {
    try {
      const response = await fetch('http://localhost/polarion/rest/v1' + path, { method: 'GET', redirect: 'error',
        signal: AbortSignal.timeout(15000), headers: { Accept: 'application/json', Authorization: 'Bearer ' + config.POLARION_TOKEN } });
      let body;
      if (response.ok && /json/i.test(response.headers.get('content-type') ?? '')) body = await response.json();
      else await response.body?.cancel();
      return { status: response.status, body };
    } catch { return { status: null }; }
  }
  const user = await get('/user');
  const identity = typeof user.body?.data?.id === 'string' ? user.body.data.id : null;
  const access = [];
  for (const project of projects) {
    const item = await get('/projects/' + project + '/workitems?page[size]=1&fields[workitems]=id');
    access.push({ project, workItemReadStatus: item.status });
  }
  results.push({ account: label, identityStatus: user.status, userId: identity, projects: access });
}
const users = results.map(r => r.userId).filter(Boolean);
const report = { checkedAt: new Date().toISOString(), mode: 'read-only', accounts: results,
  distinctVerifiedIdentities: users.length === 2 && new Set(users).size === 2, writePermissionsVerified: false };
mkdirSync('build/polarion-store', { recursive: true });
writeFileSync('build/polarion-store/account-readiness.json', JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
if (!report.distinctVerifiedIdentities) process.exitCode = 1;
