// P2 T2 acceptance: "压测 30 并发写入，无 5xx，P99 < 500ms", against a running backend (Node >= 22, no dependencies).
//
// 30 users (identity cookie user-1..user-30), each on its own block, rename their block in Sirius Web over GraphQL
// (editLabel, the canvas' direct edit) as fast as a person types: one edit, then a pause of THINK_MS ± 50 %, for
// DURATION_S, after WARMUP_S of the same load that is not counted. Latency is the HTTP round trip of each mutation (the editing context processes inputs one at a time, so
// queueing shows up here). Afterwards: no lost update (each block carries its user's last name), 30 open sessions,
// user-31 refused ("当前项目编辑人数已满") in Sirius and at /sessions (429).
//
// Usage: node tests/perf/concurrent-writes.mjs [--base http://localhost:8080] [--users 30] [--duration 60] [--warmup 15]
//        [--think 500] [--out build/perf/concurrent-writes.json] [--keep]
// Creates a project from the domain-controller demo template and deletes it at the end (--keep keeps it).
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

const arg = (name, fallback) => {
  const i = process.argv.indexOf('--' + name);
  return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg('base', 'http://localhost:8080');
const USERS = Number(arg('users', 30));
const DURATION_S = Number(arg('duration', 60));
const THINK_MS = Number(arg('think', 500));
const WARMUP_S = Number(arg('warmup', 15));
const OUT = arg('out', 'build/perf/concurrent-writes.json');
const KEEP = process.argv.includes('--keep');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const headers = user => ({ Origin: 'http://localhost:3000', 'Content-Type': 'application/json', ...(user ? { Cookie: 'sysarch_user=' + encodeURIComponent(user) } : {}) });

async function gql(query, variables, user) {
  const r = await fetch(BASE + '/api/graphql', { method: 'POST', headers: headers(user), body: JSON.stringify({ query, variables }) });
  const body = await r.json();
  if (body.errors) throw new Error(JSON.stringify(body.errors));
  return body.data;
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

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)];

let projectId;
let diagram;
const report = { users: USERS, durationS: DURATION_S, warmupS: WARMUP_S, thinkMs: THINK_MS, startedAt: new Date().toISOString() };
try {
  projectId = (await gql(`mutation($i:CreateProjectInput!){createProject(input:$i){... on CreateProjectSuccessPayload{project{id}}}}`,
    { i: { id: randomUUID(), name: 'perf-writes-' + Date.now(), natures: [] } })).createProject.project.id;
  const ec = (await gql(`query($p:ID!){viewer{project(projectId:$p){currentEditingContext{id}}}}`, { p: projectId })).viewer.project.currentEditingContext.id;
  await gql(`mutation($i:CreateDocumentInput!){createDocument(input:$i){__typename}}`,
    { i: { id: randomUUID(), editingContextId: ec, stereotypeId: 'sysarch.demo.domain-controller', name: '域控演示' } });
  const root = (await (await fetch(`${BASE}/api/v1/projects/${projectId}/elements/PD-001/location`, { headers: headers() })).json()).objectId;
  const ds = (await gql(`query($e:ID!,$o:ID!){viewer{editingContext(editingContextId:$e){representationDescriptions(objectId:$o){edges{node{id label}}}}}}`,
    { e: ec, o: root })).viewer.editingContext.representationDescriptions.edges.map(e => e.node);
  const la = ds.find(d => d.label.includes('LA'));
  const laId = (await gql(`mutation($i:CreateRepresentationInput!){createRepresentation(input:$i){... on CreateRepresentationSuccessPayload{representation{id}}}}`,
    { i: { id: randomUUID(), editingContextId: ec, objectId: root, representationDescriptionId: la.id, representationName: 'LA' } })).createRepresentation.representation.id;
  diagram = await openDiagram(ec, laId);

  // Each user creates its own block (LA "新建逻辑组件"): this opens the user's session and takes the block's lease.
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

  // Load: everyone renames their own block, concurrently. A warm-up phase first (not counted): a fresh JVM has not
  // compiled the hot paths yet, and production runs warm.
  let latencies = [];
  let status = {};
  let errors = 0;
  let errorSamples = [];
  let rev = 0;
  const phase = seconds => {
    const end = Date.now() + seconds * 1000;
    return Promise.all(own.map(async (o, index) => {
    await sleep((index * THINK_MS) / USERS); // spread the starts
    while (Date.now() < end) {
      const name = `${o.user} 组件 r${++rev}`;
      const t = performance.now();
      try {
        const r = await fetch(BASE + '/api/graphql', { method: 'POST', headers: headers(o.user), body: JSON.stringify({
          query: `mutation($input:EditLabelInput!){editLabel(input:$input){__typename ... on ErrorPayload{messages{body}}}}`,
          variables: { input: { id: randomUUID(), editingContextId: ec, representationId: laId, labelId: o.labelId, newText: name } } }) });
        latencies.push(performance.now() - t);
        status[r.status] = (status[r.status] ?? 0) + 1;
        const body = await r.json();
        if (body.errors || body.data?.editLabel?.__typename === 'ErrorPayload') {
          errors++;
          if (errorSamples.length < 5) errorSamples.push(JSON.stringify(body).slice(0, 300));
        } else {
          o.last = name;
        }
      } catch (e) {
        latencies.push(performance.now() - t);
        status.network = (status.network ?? 0) + 1;
        errors++;
      }
      await sleep(THINK_MS * (0.5 + Math.random()));
    }
  }));
  };
  if (WARMUP_S > 0) {
    await phase(WARMUP_S);
    report.warmup = { requests: latencies.length, errors, p99: +[...latencies].sort((a, b) => a - b)[Math.ceil(latencies.length * 0.99) - 1].toFixed(1) };
    latencies = [];
    status = {};
    errors = 0;
    errorSamples = [];
  }
  await phase(DURATION_S);
  await sleep(1500);

  const sorted = [...latencies].sort((a, b) => a - b);
  const total = latencies.length;
  const fiveXX = Object.entries(status).filter(([s]) => s.startsWith('5')).reduce((n, [, c]) => n + c, 0);
  const finalLabels = new Map(diagram.nodes().map(n => [n.id, diagram.label(n)]));
  const lost = own.filter(o => finalLabels.get(o.nodeId) !== o.last).map(o => `${o.user}: ${finalLabels.get(o.nodeId)} ≠ ${o.last}`);
  const sessions = (await (await fetch(`${BASE}/api/v1/sessions?projectId=${projectId}`, { headers: headers() })).json());

  // The 31st user, on a block nobody holds (so only the limit can refuse it): refused in Sirius and at /sessions.
  const free = diagram.nodes().find(n => diagram.label(n) === '感知融合单元');
  await gql(`mutation($input:EditLabelInput!){editLabel(input:$input){__typename}}`,
    { input: { id: randomUUID(), editingContextId: ec, representationId: laId, labelId: free.insideLabel.id, newText: 'user-31 改的' } }, 'user-31');
  await sleep(800);
  const refusedInSirius = diagram.label(diagram.nodes().find(n => n.id === free.id)) === '感知融合单元';
  const open31 = await fetch(BASE + '/api/v1/sessions', { method: 'POST', headers: headers('user-31'), body: JSON.stringify({ projectId }) });
  const open31Body = await open31.json();

  Object.assign(report, {
    projectId,
    requests: total,
    requestsPerSecond: +(total / DURATION_S).toFixed(1),
    status,
    errors,
    errorSamples,
    fiveXX,
    errorRate: +(errors / Math.max(1, total)).toFixed(4),
    latencyMs: { p50: +pct(sorted, 0.5).toFixed(1), p95: +pct(sorted, 0.95).toFixed(1), p99: +pct(sorted, 0.99).toFixed(1), max: +sorted[total - 1].toFixed(1) },
    lostUpdates: lost,
    openSessions: sessions.sessions.length,
    sessionLimit: sessions.limit,
    user31: { sirius: refusedInSirius ? 'refused (rolled back)' : 'NOT refused', sessionsApi: `${open31.status} ${open31Body.message ?? ''}` },
  });
  report.pass = fiveXX === 0 && errors === 0 && report.latencyMs.p99 < 500 && lost.length === 0 && report.openSessions === USERS
    && refusedInSirius && open31.status === 429;
} catch (e) {
  report.pass = false;
  report.failure = e.stack ?? String(e);
} finally {
  diagram?.close();
  if (projectId && !KEEP) {
    await gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId } }).catch(() => {});
  }
}
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
process.exit(report.pass ? 0 : 1);
