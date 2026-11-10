// P2 T9: thirty people working on one model (Node >= 22, no dependencies), against a running backend in dev mode
// (SYSARCH_AUTH_MODE=dev). Spec P2 §8 T9 step 1: "各持不同元素编辑、偶发同元素冲突、周期性打基线".
//
// - 30 users (cookies user-1..user-30) each create their own block in the LA view, then rename it over GraphQL
//   (editLabel, the canvas' direct edit), one edit then a pause of THINK_MS ± 50 %.
// - With probability --conflict (default 0.1) a user edits a SHARED block instead (the demo's logical blocks), the way
//   the shell's 编辑锁 page does: acquire its lock (POST /locks), on 423 retry every 0.5 s until the holder lets go
//   (the wait is the "lock wait time"), rename it, release (DELETE /locks). Several users want the same few blocks, so
//   they really wait for each other.
// - An architect takes a baseline every --baseline-every seconds (default 120).
// - Every 15 s: heap in use and live data after GC, Hikari active/pending connections, from /actuator/metrics (the
//   backend must expose it: MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics); skipped when not exposed.
//
// Errors: any 5xx or unexpected 4xx (423 while waiting for a lock is the expected answer), GraphQL errors, error
// payloads, network failures, a lock not obtained within --lock-timeout seconds, a failed baseline. At the end: no lost
// update (every own block carries its user's last name; every shared block carries the last name written under its
// lock), and the memory trend.
//
// Usage: node tests/perf/collab-load.mjs [--base http://localhost:8080] [--users 30] [--duration 1800] [--warmup 60]
//        [--think 500] [--conflict 0.1] [--baseline-every 120] [--lock-timeout 60] [--out build/perf/collab-load.json] [--keep]
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

const arg = (name, fallback) => {
  const i = process.argv.indexOf('--' + name);
  return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg('base', 'http://localhost:8080');
const USERS = Number(arg('users', 30));
const DURATION_S = Number(arg('duration', 1800));
const WARMUP_S = Number(arg('warmup', 60));
const THINK_MS = Number(arg('think', 500));
const CONFLICT = Number(arg('conflict', 0.1));
const BASELINE_EVERY_S = Number(arg('baseline-every', 120));
const LOCK_TIMEOUT_S = Number(arg('lock-timeout', 60));
const OUT = arg('out', 'build/perf/collab-load.json');
const KEEP = process.argv.includes('--keep');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const headers = user => ({ Origin: 'http://localhost:3000', 'Content-Type': 'application/json',
  ...(user ? { Cookie: 'sysarch_user=' + encodeURIComponent(user) } : { 'X-Sysarch-User': 'load-admin', 'X-Sysarch-Roles': 'global:admin' }) });
const pct = (sorted, p) => sorted.length === 0 ? 0 : sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)];
const round = x => +x.toFixed(1);
const stats = values => {
  const s = [...values].sort((a, b) => a - b);
  return { count: s.length, p50: round(pct(s, 0.5)), p95: round(pct(s, 0.95)), p99: round(pct(s, 0.99)), max: round(s[s.length - 1] ?? 0) };
};

async function gql(query, variables, user) {
  const r = await fetch(BASE + '/api/graphql', { method: 'POST', headers: headers(user), body: JSON.stringify({ query, variables }) });
  const body = await r.json();
  if (body.errors) throw new Error(JSON.stringify(body.errors));
  return body.data;
}
async function rest(method, path, body, user) {
  const r = await fetch(BASE + path, { method, headers: headers(user), body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: r.status, body: json };
}

async function openDiagram(ec, diagramId) {
  const state = { d: null };
  const ws = new WebSocket(BASE.replace(/^http/, 'ws') + '/subscriptions', ['graphql-ws']);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  ws.onmessage = m => { const p = JSON.parse(m.data).payload?.data?.diagramEvent; if (p?.diagram) state.d = p.diagram; };
  ws.send(JSON.stringify({ type: 'connection_init', payload: {} }));
  ws.send(JSON.stringify({ id: '1', type: 'start', payload: {
    query: `subscription($i:DiagramEventInput!){diagramEvent(input:$i){... on DiagramRefreshedEventPayload{diagram{nodes{id targetObjectId insideLabel{id text}}}}}}`,
    variables: { i: { id: randomUUID(), editingContextId: ec, diagramId } } } }));
  for (let i = 0; i < 200 && !state.d; i++) await sleep(25);
  return { close: () => ws.close(), nodes: () => state.d.nodes, label: n => n.insideLabel.text.split('\n')[0] };
}

async function metric(name, tag) {
  const r = await fetch(`${BASE}/actuator/metrics/${name}${tag ? '?tag=' + encodeURIComponent(tag) : ''}`);
  if (!r.ok) return null;
  const body = await r.json();
  const pick = s => body.measurements.find(m => m.statistic === s)?.value;
  return pick('VALUE') ?? pick('MAX') ?? pick('TOTAL') ?? null;
}

let projectId;
let diagram;
const report = { users: USERS, durationS: DURATION_S, warmupS: WARMUP_S, thinkMs: THINK_MS, conflictShare: CONFLICT,
  baselineEveryS: BASELINE_EVERY_S, startedAt: new Date().toISOString() };
const baselinesTaken = [];
try {
  // ---- setup (administrator): a demo project, its LA view, every user's own block
  projectId = (await gql(`mutation($i:CreateProjectInput!){createProject(input:$i){... on CreateProjectSuccessPayload{project{id}}}}`,
    { i: { id: randomUUID(), name: 'collab-load-' + Date.now(), natures: [] } })).createProject.project.id;
  const ec = (await gql(`query($p:ID!){viewer{project(projectId:$p){currentEditingContext{id}}}}`, { p: projectId })).viewer.project.currentEditingContext.id;
  await gql(`mutation($i:CreateDocumentInput!){createDocument(input:$i){__typename}}`,
    { i: { id: randomUUID(), editingContextId: ec, stereotypeId: 'sysarch.demo.domain-controller', name: '域控演示' } });
  for (const session of (await rest('GET', `/api/v1/sessions?projectId=${projectId}`)).body.sessions ?? []) {
    await rest('DELETE', `/api/v1/sessions/${session.id}`); // the administrator's own session: the 30 places are the users'
  }
  const objectIds = {};
  for (const id of ['PD-001', 'PD-002', 'PD-003', 'PD-004', 'PD-005', 'PD-006', 'PD-007', 'PD-008']) {
    const loc = await rest('GET', `/api/v1/projects/${projectId}/elements/${id}/location`);
    if (loc.status === 200) objectIds[loc.body.objectId] = id;
  }
  const root = Object.keys(objectIds).find(o => objectIds[o] === 'PD-001');
  const ds = (await gql(`query($e:ID!,$o:ID!){viewer{editingContext(editingContextId:$e){representationDescriptions(objectId:$o){edges{node{id label}}}}}}`,
    { e: ec, o: root })).viewer.editingContext.representationDescriptions.edges.map(e => e.node);
  const la = ds.find(d => d.label.includes('LA'));
  const laId = (await gql(`mutation($i:CreateRepresentationInput!){createRepresentation(input:$i){... on CreateRepresentationSuccessPayload{representation{id}}}}`,
    { i: { id: randomUUID(), editingContextId: ec, objectId: root, representationDescriptionId: la.id, representationName: 'LA' } })).createRepresentation.representation.id;
  diagram = await openDiagram(ec, laId);
  // the shared blocks: the demo's blocks drawn in LA
  const shared = diagram.nodes().filter(n => objectIds[n.targetObjectId]).map(n => ({ elementId: objectIds[n.targetObjectId], nodeId: n.id,
    labelId: n.insideLabel.id, last: diagram.label(n), writes: 0 }));
  if (shared.length < 2) throw new Error('expected the demo blocks in LA, found ' + shared.length);
  const own = [];
  for (let i = 1; i <= USERS; i++) {
    const before = new Set(diagram.nodes().map(n => n.id));
    await gql(`mutation($input:InvokeSingleClickOnDiagramElementToolInput!){invokeSingleClickOnDiagramElementTool(input:$input){__typename}}`,
      { input: { id: randomUUID(), editingContextId: ec, representationId: laId, diagramElementId: laId, toolId: la.id + '/tool/createFunction',
        startingPositionX: 0, startingPositionY: 0, variables: [] } }, 'user-' + i);
    let node;
    for (let t = 0; t < 200 && !node; t++) { await sleep(25); node = diagram.nodes().find(n => !before.has(n.id)); }
    if (!node) throw new Error('user-' + i + ': no new block');
    own.push({ user: 'user-' + i, labelId: node.insideLabel.id, nodeId: node.id, last: diagram.label(node) });
  }
  report.sharedBlocks = shared.map(s => s.elementId);

  // ---- counters
  let m;
  const reset = () => {
    m = { edits: [], lockAcquire: [], lockWaits: [], contended: 0, lockTimeouts: 0, sharedEdits: 0, baselines: [], status: {}, errors: 0,
      errorSamples: [], requests: 0 };
  };
  reset();
  const error = (what) => {
    m.errors++;
    if (m.errorSamples.length < 10) m.errorSamples.push(what.slice(0, 300));
  };
  const count = status => {
    m.requests++;
    m.status[status] = (m.status[status] ?? 0) + 1;
  };
  let rev = 0;
  const rename = async (user, labelId) => {
    const name = `${user} r${++rev}`;
    const t = performance.now();
    try {
      const r = await fetch(BASE + '/api/graphql', { method: 'POST', headers: headers(user), body: JSON.stringify({
        query: `mutation($input:EditLabelInput!){editLabel(input:$input){__typename ... on ErrorPayload{messages{body}}}}`,
        variables: { input: { id: randomUUID(), editingContextId: ec, representationId: laId, labelId, newText: name } } }) });
      m.edits.push(performance.now() - t);
      count(r.status);
      const body = await r.json();
      if (r.status !== 200 || body.errors || body.data?.editLabel?.__typename === 'ErrorPayload') {
        error(`editLabel ${r.status} ${JSON.stringify(body)}`);
        return null;
      }
      return name;
    } catch (e) {
      count('network');
      error('editLabel ' + e.message);
      return null;
    }
  };
  const editShared = async (user) => {
    const target = shared[Math.floor(Math.random() * shared.length)];
    const start = performance.now();
    let lock = null;
    let waited = false;
    while (!lock) {
      const t = performance.now();
      let r;
      try {
        r = await rest('POST', '/api/v1/locks', { projectId, elementIds: [target.elementId] }, user);
      } catch (e) {
        count('network');
        error('lock ' + e.message);
        return;
      }
      m.lockAcquire.push(performance.now() - t);
      count(r.status);
      if (r.status === 201) {
        lock = r.body.locks[0];
      } else if (r.status === 423) {
        waited = true;
        if (performance.now() - start > LOCK_TIMEOUT_S * 1000) {
          m.lockTimeouts++;
          error(`lock on ${target.elementId} not obtained within ${LOCK_TIMEOUT_S} s: ${r.body?.message}`);
          return;
        }
        await sleep(500);
      } else {
        error(`lock ${r.status} ${JSON.stringify(r.body)}`);
        return;
      }
    }
    if (waited) m.contended++;
    m.lockWaits.push(performance.now() - start);
    const name = await rename(user, target.labelId);
    if (name) {
      target.last = name; // under the lock: writes to this block are serialized, the last one must survive
      target.writes++;
      m.sharedEdits++;
    }
    await sleep(THINK_MS * (0.5 + Math.random())); // a person looks at what they changed before letting go
    const released = await rest('DELETE', `/api/v1/locks/${lock.id}`, undefined, user);
    count(released.status);
    if (released.status !== 204) error(`release ${released.status} ${JSON.stringify(released.body)}`);
  };
  const user = async (o, index, end) => {
    await sleep((index * THINK_MS) / USERS);
    while (Date.now() < end) {
      if (Math.random() < CONFLICT) {
        await editShared(o.user);
      } else {
        const name = await rename(o.user, o.labelId);
        if (name) o.last = name;
      }
      await sleep(THINK_MS * (0.5 + Math.random()));
    }
  };
  const architect = async (end) => {
    let next = Date.now() + Math.min(BASELINE_EVERY_S * 1000, 30_000);
    while (Date.now() < end) {
      if (Date.now() >= next) {
        const t = performance.now();
        const r = await rest('POST', '/api/v1/baselines', { projectId, label: 'load ' + new Date().toISOString() }, 'user-1');
        m.baselines.push(performance.now() - t);
        count(r.status);
        if (r.status === 201) baselinesTaken.push(r.body.id); else error(`baseline ${r.status} ${JSON.stringify(r.body)}`);
        next += BASELINE_EVERY_S * 1000;
      }
      await sleep(1000);
    }
  };
  const samples = [];
  const sampler = async (end, started) => {
    while (Date.now() < end) {
      const sample = { t: Math.round((Date.now() - started) / 1000),
        heapUsedMb: await metric('jvm.memory.used', 'area:heap'), liveDataMb: await metric('jvm.gc.live.data.size'),
        hikariActive: await metric('hikaricp.connections.active'), hikariPending: await metric('hikaricp.connections.pending'),
        hikariMax: await metric('hikaricp.connections.max'), threads: await metric('jvm.threads.live') };
      if (sample.heapUsedMb != null) {
        sample.heapUsedMb = round(sample.heapUsedMb / 1048576);
        sample.liveDataMb = round((sample.liveDataMb ?? 0) / 1048576);
        samples.push(sample);
      }
      await sleep(15_000);
    }
  };
  const phase = async seconds => {
    const started = Date.now();
    const end = started + seconds * 1000;
    await Promise.all([...own.map((o, i) => user(o, i, end)), architect(end), sampler(end, started)]);
  };

  // ---- warm-up (not counted), then the measured run
  if (WARMUP_S > 0) {
    await phase(WARMUP_S);
    report.warmup = { requests: m.requests, errors: m.errors, editP99: stats(m.edits).p99 };
    samples.length = 0;
    reset();
  }
  await phase(DURATION_S);
  await sleep(2000);

  // ---- results
  const finalLabels = new Map(diagram.nodes().map(n => [n.id, diagram.label(n)]));
  const lost = [...own.map(o => ({ who: o.user, node: o.nodeId, last: o.last })), ...shared.map(s => ({ who: s.elementId, node: s.nodeId, last: s.last }))]
    .filter(x => finalLabels.get(x.node) !== x.last).map(x => `${x.who}: ${finalLabels.get(x.node)} ≠ ${x.last}`);
  const fiveXX = Object.entries(m.status).filter(([s]) => s.startsWith('5')).reduce((n, [, c]) => n + c, 0);
  const third = Math.max(1, Math.floor(samples.length / 3));
  const avg = xs => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0;
  const live = samples.map(s => s.liveDataMb).filter(x => x > 0);
  const liveFirst = avg(live.slice(0, third));
  const liveLast = avg(live.slice(-third));
  // least-squares slope of live data after GC, MB per hour
  const xs = samples.filter(s => s.liveDataMb > 0).map(s => s.t / 3600);
  const xm = avg(xs);
  const ym = avg(live);
  const slope = xs.length > 1 ? xs.reduce((a, x, i) => a + (x - xm) * (live[i] - ym), 0) / Math.max(1e-9, xs.reduce((a, x) => a + (x - xm) ** 2, 0)) : 0;
  Object.assign(report, {
    projectId,
    requests: m.requests,
    requestsPerSecond: round(m.requests / DURATION_S),
    status: m.status,
    fiveXX,
    errors: m.errors,
    errorRate: +(m.errors / Math.max(1, m.requests)).toFixed(5),
    errorSamples: m.errorSamples,
    editLatencyMs: stats(m.edits),
    lockAcquireLatencyMs: stats(m.lockAcquire),
    lockWaitMs: { ...stats(m.lockWaits), contended: m.contended, timeouts: m.lockTimeouts },
    sharedEdits: m.sharedEdits,
    sharedWrites: Object.fromEntries(shared.map(s => [s.elementId, s.writes])),
    baselineMs: stats(m.baselines),
    lostUpdates: lost,
    memory: samples.length ? { samples: samples.length, heapUsedMaxMb: Math.max(...samples.map(s => s.heapUsedMb)),
      liveDataFirstThirdMb: round(liveFirst), liveDataLastThirdMb: round(liveLast), liveDataSlopeMbPerHour: round(slope),
      threadsMax: Math.max(...samples.map(s => s.threads ?? 0)) } : 'not exposed (set MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics)',
    connectionPool: samples.length ? { max: samples[0].hikariMax, activeMax: Math.max(...samples.map(s => s.hikariActive ?? 0)),
      pendingMax: Math.max(...samples.map(s => s.hikariPending ?? 0)) } : null,
    samples,
  });
  const memoryOk = !samples.length || liveLast <= Math.max(liveFirst * 1.2, liveFirst + 50);
  report.pass = fiveXX === 0 && report.errorRate < 0.005 && lost.length === 0 && m.lockTimeouts === 0 && memoryOk;
  report.memoryOk = memoryOk;
} catch (e) {
  report.pass = false;
  report.failure = e.stack ?? String(e);
} finally {
  diagram?.close();
  if (projectId && !KEEP) {
    for (const id of baselinesTaken) await rest('DELETE', `/api/v1/baselines/${id}`).catch(() => {});
    await gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId } }).catch(() => {});
  }
}
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(report, null, 2));
const { samples: _omit, ...summary } = report;
console.log(JSON.stringify(summary, null, 2));
process.exit(report.pass ? 0 : 1);
