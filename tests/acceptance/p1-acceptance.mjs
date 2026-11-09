// P1 acceptance against a running backend (spec P1 §6): P1-AC-1, AC-2, AC-3 (model and matrix side), AC-4 and the
// health part of AC-7, on a throw-away project created from the domain-controller demo template and deleted at the end.
// Node >= 22, no dependencies. Usage: node tests/acceptance/p1-acceptance.mjs [http://localhost:8080] [--keep]
//
// Not covered here (see docs/layers-and-trace.md, "验收"): the Polarion half of AC-3 and AC-6 (TraceAndScheduleIT;
// they write to Polarion), AC-5 (needs SysON), and the browser-only parts of P0's AC-2..AC-6.
import { randomUUID } from 'node:crypto';

const BASE = (process.argv.find(a => a.startsWith('http')) ?? 'http://localhost:8080').replace(/\/$/, '');
const KEEP = process.argv.includes('--keep');
const HEAD = { Origin: 'http://localhost:3000', 'X-Sysarch-User': 'p1-acceptance' };
const results = [];

function check(id, ok, detail) {
  results.push({ id, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${detail}`);
}

async function gql(query, variables) {
  const r = await fetch(BASE + '/api/graphql', { method: 'POST', headers: { ...HEAD, 'Content-Type': 'application/json' }, body: JSON.stringify({ query, variables }) });
  const j = await r.json();
  if (j.errors) throw new Error(JSON.stringify(j.errors));
  return j.data;
}

async function rest(method, path, body) {
  const r = await fetch(BASE + path, { method, headers: { ...HEAD, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  return { status: r.status, body: text ? JSON.parse(text) : null };
}

const ok = (payload, name) => {
  if (payload.__typename === 'ErrorPayload') throw new Error(name + ': ' + JSON.stringify(payload.messages));
  return payload;
};

/** An open diagram: keeps the latest refresh; until(predicate) waits for one that matches. */
async function openDiagram(ec, diagramId) {
  const state = { diagram: null, waiters: [] };
  const ws = new WebSocket(BASE.replace(/^http/, 'ws') + '/subscriptions', ['graphql-ws']);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = m => {
    const msg = JSON.parse(m.data);
    const p = msg.payload?.data?.diagramEvent;
    if (msg.type === 'data' && p?.__typename === 'DiagramRefreshedEventPayload') {
      state.diagram = p.diagram;
      state.waiters = state.waiters.filter(w => !(w.predicate(p.diagram) && (w.resolve(p.diagram), true)));
    }
  };
  ws.send(JSON.stringify({ type: 'connection_init', payload: {} }));
  ws.send(JSON.stringify({ id: '1', type: 'start', payload: {
    query: `subscription($i:DiagramEventInput!){diagramEvent(input:$i){__typename ... on DiagramRefreshedEventPayload{diagram{id nodes{id targetObjectId insideLabel{id text}} edges{id}}}}}`,
    variables: { i: { id: randomUUID(), editingContextId: ec, diagramId } } } }));
  return {
    labels: () => state.diagram.nodes.map(n => n.insideLabel.text.split('\n')[0]).sort(),
    until(predicate, ms = 10000) {
      if (state.diagram && predicate(state.diagram)) return Promise.resolve(state.diagram);
      return new Promise((resolve, reject) => {
        state.waiters.push({ predicate, resolve });
        setTimeout(() => reject(new Error('timed out waiting for diagram ' + diagramId)), ms);
      });
    },
    close: () => ws.close(),
  };
}

let projectId;
const opened = [];
try {
  // ------------------------------------------------------------------ AC-7 (P0 AC-1): health
  const health = await rest('GET', '/actuator/health');
  check('AC-7/P0-AC-1', health.status === 200 && health.body.status === 'UP', `health ${health.status} ${health.body?.status}`);

  // ------------------------------------------------------------------ project from the demo template
  const name = 'p1-acceptance-' + Date.now();
  projectId = ok((await gql(`mutation($i:CreateProjectInput!){createProject(input:$i){__typename ... on CreateProjectSuccessPayload{project{id}} ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), name, natures: [] } })).createProject, 'createProject').project.id;
  const ec = (await gql(`query($p:ID!){viewer{project(projectId:$p){currentEditingContext{id}}}}`, { p: projectId })).viewer.project.currentEditingContext.id;
  ok((await gql(`mutation($i:CreateDocumentInput!){createDocument(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), editingContextId: ec, stereotypeId: 'sysarch.demo.domain-controller', name: '域控演示' } })).createDocument, 'createDocument');
  console.log(`      project ${name} (${projectId})`);

  const objectId = async id => (await rest('GET', `/api/v1/projects/${projectId}/elements/${id}/location`)).body.objectId;
  const matrix = async () => (await rest('GET', `/api/v1/projects/${projectId}/tables/allocation-matrix`)).body;
  const layerOf = (m, label) => m.rows.filter(h => h.label === label).map(h => h.group);
  async function representation(rootId, labelPart) {
    const root = await objectId(rootId);
    const ds = (await gql(`query($e:ID!,$o:ID!){viewer{editingContext(editingContextId:$e){representationDescriptions(objectId:$o){edges{node{id label}}}}}}`, { e: ec, o: root }))
      .viewer.editingContext.representationDescriptions.edges.map(e => e.node);
    const d = ds.find(n => n.label.includes(labelPart));
    const created = ok((await gql(`mutation($i:CreateRepresentationInput!){createRepresentation(input:$i){__typename ... on CreateRepresentationSuccessPayload{representation{id}} ... on ErrorPayload{messages{body}}}}`,
      { i: { id: randomUUID(), editingContextId: ec, objectId: root, representationDescriptionId: d.id, representationName: d.label } })).createRepresentation, 'createRepresentation');
    const view = await openDiagram(ec, created.representation.id);
    opened.push(view);
    await view.until(() => true);
    return { id: created.representation.id, descriptionId: d.id, view };
  }
  async function clickBackground(rep, tool) {
    return ok((await gql(`mutation($i:InvokeSingleClickOnDiagramElementToolInput!){invokeSingleClickOnDiagramElementTool(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
      { i: { id: randomUUID(), editingContextId: ec, representationId: rep.id, diagramElementId: rep.id, toolId: rep.descriptionId + '/tool/' + tool,
        startingPositionX: 40, startingPositionY: 40, variables: [] } })).invokeSingleClickOnDiagramElementTool, tool);
  }

  // ------------------------------------------------------------------ P1-AC-1: LA shows LOGICAL only; new element is LOGICAL
  const la = await representation('PD-001', 'LA');
  const pa = await representation('PD-005', 'PA');
  const before = await matrix();
  const laLabels = la.view.labels();
  const allLogical = laLabels.every(l => layerOf(before, l).every(g => g === 'LOGICAL'));
  check('P1-AC-1 view', laLabels.length === 4 && allLogical, `LA shows ${laLabels.join('、')}`);
  await clickBackground(la, 'createFunction');
  const laAfter = await la.view.until(d => d.nodes.length === 5);
  const created = laAfter.nodes.map(n => n.insideLabel.text.split('\n')[0]).find(l => !laLabels.includes(l));
  const createdLayers = layerOf(await matrix(), created);
  const paLabels = pa.view.labels();
  check('P1-AC-1 create', createdLayers.length > 0 && createdLayers.every(g => g === 'LOGICAL') && !paLabels.includes(created),
    `new "${created}" is ${[...new Set(createdLayers)].join('/')}; PA shows ${paLabels.join('、')}`);

  // ------------------------------------------------------------------ P1-AC-2: SA → PA refused, "跳过 LA"
  const sa = await representation('PD-001', 'SA');
  await clickBackground(sa, 'createFunction');
  await sa.view.until(d => d.nodes.length === 1);
  const m2 = await matrix();
  const saBlock = m2.rows.find(h => h.group === 'SYSTEM');
  const soc = m2.columns.find(h => h.label === '域控 SoC');
  const createLink = m2.actions.find(a => !a.onMarked);
  const skip = await rest('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`, { actionId: createLink.id, rowId: saBlock.id, columnId: soc.id });
  const skipMessage = JSON.stringify(skip.body?.errors ?? skip.body);
  check('P1-AC-2', skip.status === 422 && skipMessage.includes('跳过 LA'), `${skip.status} ${skip.body?.errors?.[0]?.message ?? skipMessage}`);

  // ------------------------------------------------------------------ P1-AC-3 (model side): satisfy from the trace matrix
  const trace = (await rest('GET', `/api/v1/projects/${projectId}/tables/requirement-trace`)).body;
  const rq4 = trace.rows.find(h => h.id === 'RQ-004');
  const satisfy = trace.actions.find(a => a.id === 'satisfy');
  const linked = await rest('POST', `/api/v1/projects/${projectId}/tables/requirement-trace/actions`, { actionId: satisfy.id, rowId: rq4.id, columnId: soc.id });
  const marked = linked.body?.matrix?.cells?.some(c => c.rowId === 'RQ-004' && c.columnId === soc.id && c.links.includes('satisfy'));
  const traceApi = (await rest('GET', `/api/v1/trace/matrix?projectId=${projectId}`)).body;
  const rq4Api = traceApi.requirements.find(r => r.id === 'RQ-004');
  check('P1-AC-3 matrix', linked.status === 200 && marked && rq4Api.satisfied,
    `cell marked ${marked}; /trace/matrix RQ-004 satisfiedBy ${rq4Api.satisfiedBy.map(r => r.name).join('、')}; summary ${JSON.stringify(traceApi.summary)}`);

  // ------------------------------------------------------------------ P1-AC-4: rename after a baseline = 1 modified
  const baseline = await rest('POST', '/api/v1/baselines', { projectId, label: 'p1-acceptance' });
  const mcu = (await pa.view.until(() => true)).nodes.find(n => n.insideLabel.text.startsWith('安全 MCU'));
  ok((await gql(`mutation($i:EditLabelInput!){editLabel(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), editingContextId: ec, representationId: pa.id, labelId: mcu.insideLabel.id, newText: '安全 MCU（ASIL-D）' } })).editLabel, 'editLabel');
  await pa.view.until(d => d.nodes.some(n => n.insideLabel.text.startsWith('安全 MCU（ASIL-D）')));
  const diff = (await rest('GET', `/api/v1/baselines/${baseline.body.id}/diff`)).body;
  check('P1-AC-4', baseline.status === 201 && diff.added.length === 0 && diff.removed.length === 0 && diff.modified.length === 1
    && diff.modified[0].elementId === 'PD-006' && diff.modified[0].fields.join() === 'name',
    `baseline ${baseline.body.elementCount} elements; diff added ${diff.added.length} removed ${diff.removed.length} modified ${JSON.stringify(diff.modified)}`);

  // ------------------------------------------------------------------ P0 AC-5 regression: an invalid edit is still vetoed
  const sameLayer = await rest('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`,
    { actionId: createLink.id, rowId: m2.rows.find(h => h.label === '感知融合单元').id, columnId: m2.columns.find(h => h.label === '决策规划单元').id });
  check('AC-7/P0 constraints', sameLayer.status === 422, `LA → LA allocation ${sameLayer.status} ${sameLayer.body?.errors?.[0]?.message ?? ''}`);
} catch (e) {
  check('run', false, e.stack ?? String(e));
} finally {
  opened.forEach(v => v.close());
  if (projectId && !KEEP) {
    await gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId } }).catch(e => console.log('cleanup: ' + e));
  }
}
const failed = results.filter(r => !r.ok).length;
console.log(`\n${results.length - failed}/${results.length} passed${projectId && !KEEP ? ', project deleted' : ''}`);
process.exit(failed ? 1 : 0);
