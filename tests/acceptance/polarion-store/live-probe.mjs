// Real, explicitly scoped M0 acceptance. Credentials stay in memory; only run-owned records are cleaned up.
import { readLocalEnv } from '../../../tools/local-env.mjs';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { spawn } from 'node:child_process';

const base = 'http://localhost/polarion/sysarch-m0';
const credentials = Object.fromEntries(['A', 'B'].map(label => {
  const env = readLocalEnv(`build/private/polarion-test-${label.toLowerCase()}.env`);
  if (env.POLARION_BASE_URL?.replace(/\/+$/, '') !== 'http://localhost/polarion' || !env.POLARION_TOKEN) throw new Error('Invalid test credential configuration');
  return [label, env.POLARION_TOKEN];
}));

async function call(account, action, values, method = 'POST', timeoutMs = 30000) {
  const response = await fetch(base + action, { method, redirect: 'error', signal: AbortSignal.timeout(timeoutMs), headers: {
    ...(account ? { Authorization: 'Bearer ' + credentials[account] } : {}),
    ...(method === 'POST' ? { 'X-Sysarch-Probe': '1', 'Content-Type': 'application/x-www-form-urlencoded' } : {}),
  }, ...(method === 'POST' ? { body: new URLSearchParams(values) } : {}) });
  if (!/application\/json/i.test(response.headers.get('content-type') ?? '')) {
    await response.body?.cancel();
    throw Object.assign(new Error('Probe response format mismatch'), { name: 'NonJsonResponse', status: response.status });
  }
  return { status: response.status, data: await response.json() };
}

if (process.argv[2] === '--worker') {
  const request = JSON.parse(process.argv[3]);
  const started = Date.now();
  try { console.log(JSON.stringify({ ...await call(request.account, request.action ?? '/commit', request.values, 'POST', 60000), elapsedMs: Date.now() - started })); }
  catch (error) { console.log(JSON.stringify({ status: null, failureType: error.name, httpStatus: error.status, elapsedMs: Date.now() - started })); process.exitCode = 1; }
} else {
  const run = randomUUID();
  const report = { run, startedAt: new Date().toISOString(), checks: [], records: {}, extraRecords: [], cleanup: {}, scope: 'dedicated M0 projects only' };
  mkdirSync('build/polarion-store', { recursive: true });
  const output = `build/polarion-store/live-${run}.json`;
  const save = () => writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  function check(name, condition, detail) {
    report.checks.push({ name, passed: Boolean(condition), ...(detail ? { detail } : {}) }); save();
    if (!condition) throw new Error(name);
  }
  async function worker(account, values, action = '/commit') {
    return new Promise((resolve) => {
      const processHandle = spawn(process.execPath, [process.argv[1], '--worker', JSON.stringify({ account, values, action })],
        { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
      let data = ''; processHandle.stdout.on('data', chunk => { data += chunk; });
      processHandle.stderr.resume();
      processHandle.on('error', () => resolve({ status: null, error: 'worker-spawn-failed' }));
      processHandle.on('close', () => { try { resolve(JSON.parse(data.trim())); } catch { resolve({ status: null }); } });
    });
  }
  async function nativeTitle(account, project, item, title) {
    if (!title.startsWith(`SYSARCH-M0 ${run} `)) throw new Error('Refusing unscoped title modification');
    const response = await fetch('http://localhost/polarion/rest/v1/projects/' + project + '/workitems/' + item, {
      method: 'PATCH', redirect: 'error', signal: AbortSignal.timeout(15000),
      headers: { Authorization: 'Bearer ' + credentials[account], 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ data: { type: 'workitems', id: project + '/' + item, attributes: { title } } }),
    });
    await response.body?.cancel(); return response.status;
  }
  save();
  try {
    const anonymous = await call(null, '/whoami', {}, 'GET');
    check('anonymous refused', anonymous.status === 401);
    const a = await call('A', '/whoami', {}, 'GET');
    const b = await call('B', '/whoami', {}, 'GET');
    report.identities = { A: a.data.actor, B: b.data.actor }; save();
    report.coordination = a.data.coordination ?? 'native-sdk-only'; save();
    report.capabilities = a.data.capabilities ?? []; save();
    check('two distinct SDK-verified PAT identities', a.status === 200 && b.status === 200 && a.data.actor && b.data.actor && a.data.actor !== b.data.actor);
    for (const account of ['A', 'B']) {
      const project = `sysarch_probe_${account.toLowerCase()}`;
      const result = await call(account, '/setup', { project, run });
      if (result.status === 201) { report.records[account] = { project, run, ...result.data }; save(); }
      check(`${account} own-project setup`, result.status === 201 && result.data.actor === report.identities[account], result.status === 201 ? undefined : result);
    }
    const values = (account, more = {}) => ({ project: report.records[account].project, run, root: report.records[account].root, ...more });
    check('A cannot read B probe', (await call('A', '/inspect', values('B'))).status === 403);
    const bReadA = await call('B', '/inspect', values('A'));
    report.bReadAccessToA = bReadA.status; save();
    check('B configured read-only-or-denied access to A resolves', bReadA.status === 200 || bReadA.status === 403);
    const bWriteA = await call('B', '/commit', values('A', { command: randomUUID(), expectedVersion: '0', value: 'forbidden-write' }));
    check('B cannot modify A by SDK object permissions', bWriteA.status === 403
      && ['item-not-writable', 'item-not-readable', 'permission-denied'].includes(bWriteA.data.error), bWriteA);
    const before = await call('A', '/inspect', values('A'));
    const failedCommand = randomUUID();
    const rollback = await call('A', '/commit', values('A', { command: failedCommand, expectedVersion: '0', value: 'rollback', rollback: 'true' }));
    check('injected failure returns rejection', rollback.status === 422, rollback.status === 422 ? undefined : rollback);
    const after = await call('A', '/inspect', values('A'));
    check('head, both elements and links rollback atomically', after.status === 200 && before.data.version === after.data.version
      && before.data.leftTitle === after.data.leftTitle && before.data.rightTitle === after.data.rightTitle && before.data.links === after.data.links);
    check('rolled-back command absent', (await call('A', '/command-status', values('A', { command: failedCommand }))).status === 404);
    const command = randomUUID();
    const commitValues = values('A', { command, expectedVersion: '0', value: 'first' });
    const committed = await call('A', '/commit', commitValues);
    check('multi-element commit', committed.status === 200 && committed.data.version === 1, committed.status === 200 ? undefined : committed);
    const replayed = await call('A', '/commit', commitValues);
    check('duplicate command replays original result', replayed.status === 200 && replayed.data.replayed === true && replayed.data.commandItem === committed.data.commandItem);
    check('same id different payload refused', (await call('A', '/commit', { ...commitValues, value: 'different' })).status === 409);
    check('stale version refused', (await call('A', '/commit', { ...commitValues, command: randomUUID() })).status === 409);
    if (report.capabilities.includes('title-drift')) {
      const stable = await call('A', '/inspect', values('A'));
      const edited = await nativeTitle('A', report.records.A.project, report.records.A.left, `SYSARCH-M0 ${run} left-native-edit`);
      check('native REST edit performed with original user', edited === 200 || edited === 204, { status: edited });
      try {
        const external = await call('A', '/inspect', values('A'));
        check('native title drift is detected', external.status === 200 && external.data.drift === true && external.data.driftFields.includes('left.title'));
        const refusal = await call('A', '/commit', values('A', { command: randomUUID(), expectedVersion: '1', value: 'must-not-overwrite' }));
        check('drift blocks model commit instead of overwriting native edit', refusal.status === 409 && refusal.data.error === 'external-change-detected');
      } finally {
        const restored = await nativeTitle('A', report.records.A.project, report.records.A.left, stable.data.leftTitle);
        report.nativeTitleRestored = restored === 200 || restored === 204; save();
      }
      const restored = await call('A', '/inspect', values('A'));
      check('exact native restoration clears drift without changing model version', report.nativeTitleRestored && restored.data.drift === false && restored.data.version === 1);
    }
    const discarded = randomUUID();
    await call('A', '/commit', values('A', { command: discarded, expectedVersion: '1', value: 'discarded-response' }));
    const recovered = await call('A', '/command-status', values('A', { command: discarded }));
    check('persisted status after deliberately discarded response', recovered.status === 200 && recovered.data.status === 'COMMITTED' && recovered.data.version === 2);
    let raceVersion = 2;
    if (report.coordination === 'shared-postgresql') {
      const unknown = randomUUID();
      const unknownValues = values('A', { command: unknown, expectedVersion: '2', value: 'unknown-outcome', fault: 'after-commit' });
      const interrupted = await call('A', '/commit', unknownValues);
      check('post-commit acknowledgement fault returns uncertain outcome', interrupted.status === 503);
      const blocked = await call('A', '/commit', values('A', { command: randomUUID(), expectedVersion: '3', value: 'must-not-write' }));
      check('UNKNOWN reservation refuses new writes', blocked.status === 409 && blocked.data.error === 'model-command-in-progress');
      const committedStatus = await call('A', '/command-status', values('A', { command: unknown }));
      check('uncertain outcome is resolved by durable Polarion record', committedStatus.status === 200 && committedStatus.data.version === 3);
      const recovery = await call('A', '/recover', values('A', { command: unknown }));
      check('original user explicitly recovers a confirmed commit', recovery.status === 200 && recovery.data.recovered === true);
      const retry = await call('A', '/commit', { ...unknownValues, fault: '' });
      check('retry after recovery replays rather than creating another commit', retry.status === 200 && retry.data.replayed === true && retry.data.version === 3);
      raceVersion = 3;
    }
    const requests = ['race-one', 'race-two'].map(value => worker('A', values('A', { command: randomUUID(), expectedVersion: String(raceVersion), value })));
    let lastSample = Promise.resolve();
    let sampling = false;
    const monitor = process.argv.includes('--diagnostic') ? setInterval(() => {
      if (sampling) return;
      sampling = true;
      lastSample = call('A', '/active', {}, 'GET', 5000).then(result => {
        (report.diagnostics ??= []).push(result); save();
      }).catch(() => { (report.diagnostics ??= []).push({ status: null }); save(); })
        .finally(() => { sampling = false; });
    }, 2000) : null;
    let race;
    try { race = await Promise.all(requests); }
    finally { if (monitor) clearInterval(monitor); await lastSample; }
    report.concurrency = race; save();
    const head = await call('A', '/inspect', values('A'));
    check('independent-process race has one winner and head advances once', race.filter(r => r.status === 200).length === 1
      && race.filter(r => r.status === 409).length === 1 && head.data.version === raceVersion + 1, { statuses: race.map(r => r.status), version: head.data.version });
    if (report.capabilities.includes('model-uuid-registration')) {
      const modelUuid = randomUUID();
      const registrationValues = { project: report.records.A.project, run, modelUuid };
      const registrations = await Promise.all([worker('A', registrationValues, '/register-model'), worker('A', registrationValues, '/register-model')]);
      report.registrations = registrations; save();
      for (const result of registrations) if (result.status === 201) {
        report.extraRecords.push({ account: 'A', project: report.records.A.project, run, ...result.data }); save();
      }
      check('concurrent model UUID registration has exactly one winner', registrations.filter(r => r.status === 201).length === 1
        && registrations.filter(r => r.status === 409).length === 1, { statuses: registrations.map(r => r.status) });
      check('published model UUID cannot be registered again', (await call('A', '/register-model', registrationValues)).status === 409);
      check('same model UUID cannot be registered in another project', (await call('B', '/register-model', { ...registrationValues, project: report.records.B.project })).status === 409);
      const registered = report.extraRecords.at(-1);
      const stored = await call('A', '/inspect', { project: registered.project, run, root: registered.root });
      check('registered UUID is persisted in Polarion root', stored.status === 200 && stored.data.modelUuid === modelUuid);
    }
    if (report.capabilities.includes('capacity')) {
      for (const bytes of [1048576, 20971520]) {
        const command = randomUUID();
        const created = await call('A', '/capacity', values('A', { command, bytes: String(bytes) }), 'POST', 60000);
        check(`${bytes}-byte attachment fixture committed`, created.status === 201 && created.data.bytes === bytes, created.status === 201 ? undefined : created);
        const verified = await call('A', '/capacity-check', values('A', { command }), 'POST', 60000);
        check(`${bytes}-byte attachment and 64KiB text round-trip`, verified.status === 200 && verified.data.bytes === bytes
          && verified.data.hashMatches === true && verified.data.sha256 === created.data.sha256 && verified.data.textBytes === 65536);
      }
      const oversize = await call('A', '/capacity', values('A', { command: randomUUID(), bytes: '22020096' }));
      check('outside configured capacity allowlist is rejected before allocation', oversize.status === 422 && oversize.data.error === 'capacity-size-not-allowlisted');
    }
    report.acceptance = 'passed';
  } catch (error) {
    report.acceptance = 'failed'; report.failedCheck = error.message;
    console.error('M0 probe stopped at a failed check; report contains sanitized results.'); process.exitCode = 1;
  } finally {
    for (const account of ['A', 'B']) {
      const record = report.records[account]; if (!record) continue;
      try {
        const result = await call(account, '/cleanup', { project: record.project, run, root: record.root });
        report.cleanup[account] = result;
        if (result.status !== 200) process.exitCode = 1;
      } catch { report.cleanup[account] = { status: null, error: 'cleanup-unconfirmed' }; process.exitCode = 1; }
      save();
    }
    for (const record of report.extraRecords) {
      try {
        const result = await call(record.account, '/cleanup', { project: record.project, run, root: record.root });
        report.cleanup[record.root] = result;
        if (result.status !== 200) process.exitCode = 1;
      } catch { report.cleanup[record.root] = { status: null, error: 'extra-cleanup-unconfirmed' }; process.exitCode = 1; }
      save();
    }
    report.finishedAt = new Date().toISOString(); save();
    console.log(JSON.stringify({ report: output, acceptance: report.acceptance, checks: report.checks, cleanup: report.cleanup }, null, 2));
  }
}
