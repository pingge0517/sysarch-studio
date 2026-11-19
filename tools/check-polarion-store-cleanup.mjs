// Independently check only run UUIDs recorded by this workspace's real acceptance reports.
import { readLocalEnv } from './local-env.mjs';
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';

const reports = readdirSync('build/polarion-store').filter(name => /^live-[a-f0-9-]+\.json$/.test(name))
  .map(name => JSON.parse(readFileSync('build/polarion-store/' + name, 'utf8')));
const checks = [];
for (const account of ['A', 'B']) {
  const config = readLocalEnv(`build/private/polarion-test-${account.toLowerCase()}.env`);
  if (config.POLARION_BASE_URL?.replace(/\/+$/, '') !== 'http://localhost/polarion') throw new Error('Unexpected target');
  for (const report of reports) {
    const record = report.records?.[account]; if (!record) continue;
    const prefix = `SYSARCH-M0 ${report.run} `;
    const query = `title:"SYSARCH-M0" AND title:"${report.run}"`;
    const remaining = [];
    let complete = true;
    for (let page = 1; page <= 10; page++) {
      const url = 'http://localhost/polarion/rest/v1/projects/' + record.project + '/workitems?'
        + new URLSearchParams({ query, 'fields[workitems]': 'id,title', 'page[size]': '100', 'page[number]': String(page) });
      const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(15000),
        headers: { Authorization: 'Bearer ' + config.POLARION_TOKEN, Accept: 'application/json' } });
      if (!response.ok) { checks.push({ account, run: report.run, project: record.project, status: response.status, verified: false }); complete = false; break; }
      const body = await response.json();
      for (const item of body.data ?? []) {
        if (item.attributes?.title?.startsWith(prefix)) remaining.push({ id: item.id, title: item.attributes.title });
      }
      if (!body.links?.next) break;
      if (page === 10) complete = false;
    }
    if (complete) checks.push({ account, run: report.run, project: record.project, verified: remaining.length === 0, remaining });
  }
}
const result = { checkedAt: new Date().toISOString(), mode: 'read-only', checks, allClean: checks.length > 0 && checks.every(c => c.verified) };
writeFileSync('build/polarion-store/cleanup-verification.json', JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
if (!result.allClean) process.exitCode = 1;
