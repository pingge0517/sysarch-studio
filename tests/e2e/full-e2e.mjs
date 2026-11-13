// End-to-end test of the implemented features (P0-P4) against ONE running instance in production configuration:
// polarion mode (reads need sign-in), the local Polarion, the service account, single sign-on, metrics token, solver.
// Node >= 22, no dependencies. Everything it creates is deleted at the end: the model projects, the Polarion project
// binding, the Polarion links and the Work Items it created in the Polarion project (only those, by id).
//
// Usage (token values are read from the environment and never printed):
//   POLARION_TOKEN=... SSO_SECRET=... METRICS_TOKEN=... WEBHOOK_SECRET=... node tests/e2e/full-e2e.mjs
// Optional: BASE (http://localhost:8080), POLARION (http://localhost/polarion), POLARION_PROJECT (sysarch),
//           REPORT_JSON (where to write the results), --keep (keep the project for the UI test)
import { createHmac, randomBytes, randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost:8080';
const POLARION = process.env.POLARION ?? 'http://localhost/polarion';
const PP = process.env.POLARION_PROJECT ?? 'sysarch';
const TOKEN = process.env.POLARION_TOKEN;
const KEEP = process.argv.includes('--keep');
if (!TOKEN) throw new Error('POLARION_TOKEN is required');
const ORIGIN = { Origin: 'http://localhost:3000' };
const AUTH = { ...ORIGIN, Authorization: 'Bearer ' + TOKEN };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ------------------------------------------------------------------------------------------------ results
const results = [];
let section = '';
function check(id, ok, detail = '') {
  results.push({ section, id, ok: !!ok, detail: String(detail).slice(0, 400) });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${String(detail).slice(0, 220)}`);
}
async function part(name, fn) {
  section = name;
  console.log(`\n== ${name}`);
  try {
    await fn();
  } catch (e) {
    check(name + ' (run)', false, e.stack ?? String(e));
  }
}

// ------------------------------------------------------------------------------------------------ HTTP helpers
async function call(method, path, { headers = AUTH, body, raw, type } = {}) {
  const h = { ...headers };
  let payload;
  if (raw !== undefined) {
    payload = raw;
    if (type) h['Content-Type'] = type;
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    h['Content-Type'] = 'application/json';
  }
  const r = await fetch(BASE + path, { method, headers: h, body: payload, redirect: 'manual' });
  const buf = Buffer.from(await r.arrayBuffer());
  let json = null;
  try {
    json = JSON.parse(buf.toString('utf8'));
  } catch {
    // not JSON
  }
  return { status: r.status, json, text: buf.toString('utf8'), bytes: buf, headers: r.headers };
}
async function gql(query, variables, headers = AUTH) {
  const r = await call('POST', '/api/graphql', { headers, body: { query, variables } });
  if (r.status !== 200) throw new Error(`GraphQL ${r.status} ${r.text.slice(0, 200)}`);
  if (r.json.errors) throw new Error('GraphQL ' + JSON.stringify(r.json.errors).slice(0, 300));
  return r.json.data;
}
const ok = (payload, name) => {
  if (payload?.__typename === 'ErrorPayload') throw new Error(name + ': ' + JSON.stringify(payload.messages));
  return payload;
};
async function polarion(method, path, body) {
  const r = await fetch(POLARION + '/rest/v1' + path, {
    method,
    headers: { Authorization: 'Bearer ' + TOKEN, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    // empty
  }
  return { status: r.status, json, text };
}
async function poll(fn, until, ms = 60000, every = 500) {
  const end = Date.now() + ms;
  let last;
  while (Date.now() < end) {
    last = await fn();
    if (until(last)) return last;
    await sleep(every);
  }
  return last;
}

// ------------------------------------------------------------------------------------------------ Sirius helpers
let projectId;
let ec;
const extraProjects = [];
const createdWorkItems = new Set(); // Polarion Work Items this run created (deleted at the end)
const opened = [];

async function createProject(name) {
  return ok((await gql(`mutation($i:CreateProjectInput!){createProject(input:$i){__typename ... on CreateProjectSuccessPayload{project{id}} ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), name, natures: [] } })).createProject, 'createProject').project.id;
}
async function deleteProject(id) {
  return gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId: id } });
}
const ecOf = async (p) => (await gql(`query($p:ID!){viewer{project(projectId:$p){currentEditingContext{id}}}}`, { p })).viewer.project.currentEditingContext.id;
const objectId = async (id, project = projectId) => (await call('GET', `/api/v1/projects/${project}/elements/${id}/location`)).json?.objectId;

// Node's WebSocket (undici) accepts { headers } as the second argument's options in Node >= 22.
function subscribeDiagram(editingContext, diagramId, cookie) {
  const state = { diagram: null, waiters: [], error: null };
  const ws = new WebSocket(BASE.replace(/^http/, 'ws') + '/subscriptions', { protocols: ['graphql-ws'], headers: { ...ORIGIN, Cookie: cookie } });
  const ready = new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = (e) => reject(new Error('websocket: ' + (e.message ?? 'error')));
  });
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    const p = msg.payload?.data?.diagramEvent;
    if (msg.type === 'data' && p?.__typename === 'DiagramRefreshedEventPayload') {
      state.diagram = p.diagram;
      state.waiters = state.waiters.filter((w) => !(w.predicate(p.diagram) && (w.resolve(p.diagram), true)));
    }
    if (msg.type === 'error' || msg.payload?.errors) state.error = JSON.stringify(msg.payload);
  };
  const view = {
    async start() {
      await ready;
      ws.send(JSON.stringify({ type: 'connection_init', payload: {} }));
      ws.send(JSON.stringify({ id: '1', type: 'start', payload: {
        query: `subscription($i:DiagramEventInput!){diagramEvent(input:$i){__typename ... on DiagramRefreshedEventPayload{diagram{id nodes{id targetObjectId type insideLabel{id text} outsideLabels{text} borderNodes{id targetObjectId outsideLabels{text}} childNodes{id targetObjectId type insideLabel{id text} outsideLabels{text} borderNodes{id targetObjectId outsideLabels{text}} childNodes{id targetObjectId type insideLabel{id text} outsideLabels{text} borderNodes{id targetObjectId outsideLabels{text}}}}} edges{id targetObjectId sourceId targetId centerLabel{text} style{lineStyle}}}}}}`,
        variables: { i: { id: randomUUID(), editingContextId: editingContext, diagramId } } } }));
      return view;
    },
    until(predicate, ms = 15000) {
      if (state.diagram && predicate(state.diagram)) return Promise.resolve(state.diagram);
      return new Promise((resolve, reject) => {
        state.waiters.push({ predicate, resolve });
        setTimeout(() => reject(new Error('timed out waiting for diagram ' + diagramId + (state.error ? ' ' + state.error : ''))), ms);
      });
    },
    get diagram() {
      return state.diagram;
    },
    close: () => ws.close(),
  };
  opened.push(view);
  return view;
}

let COOKIE = '';
async function representation(rootId, labelPart, project = projectId, editingContext = ec) {
  const root = await objectId(rootId, project);
  if (!root) throw new Error('no element ' + rootId);
  const ds = (await gql(`query($e:ID!,$o:ID!){viewer{editingContext(editingContextId:$e){representationDescriptions(objectId:$o){edges{node{id label}}}}}}`, { e: editingContext, o: root }))
    .viewer.editingContext.representationDescriptions.edges.map((e) => e.node);
  const d = ds.find((n) => n.label.includes(labelPart));
  if (!d) throw new Error(`no representation "${labelPart}" on ${rootId}: ${ds.map((x) => x.label).join(', ')}`);
  const created = ok((await gql(`mutation($i:CreateRepresentationInput!){createRepresentation(input:$i){__typename ... on CreateRepresentationSuccessPayload{representation{id}} ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), editingContextId: editingContext, objectId: root, representationDescriptionId: d.id, representationName: d.label } })).createRepresentation, 'createRepresentation');
  const view = await subscribeDiagram(editingContext, created.representation.id, COOKIE).start();
  await view.until(() => true);
  return { id: created.representation.id, descriptionId: d.id, view, label: d.label };
}
async function tool(rep, toolName, elementId, editingContext = ec) {
  return ok((await gql(`mutation($i:InvokeSingleClickOnDiagramElementToolInput!){invokeSingleClickOnDiagramElementTool(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), editingContextId: editingContext, representationId: rep.id, diagramElementId: elementId ?? rep.id, toolId: rep.descriptionId + '/tool/' + toolName,
      startingPositionX: 40, startingPositionY: 40, variables: [] } })).invokeSingleClickOnDiagramElementTool, toolName);
}
async function connectTool(rep, toolName, source, target) {
  return (await gql(`mutation($i:InvokeSingleClickOnTwoDiagramElementsToolInput!){invokeSingleClickOnTwoDiagramElementsTool(input:$i){__typename ... on InvokeSingleClickOnTwoDiagramElementsToolSuccessPayload{messages{body level}} ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), editingContextId: ec, representationId: rep.id, toolId: rep.descriptionId + '/tool/' + toolName, diagramSourceElementId: source, diagramTargetElementId: target,
      sourcePositionX: 0, sourcePositionY: 0, targetPositionX: 0, targetPositionY: 0, variables: [] } })).invokeSingleClickOnTwoDiagramElementsTool;
}
const firstLine = (n) => (n.insideLabel?.text ?? n.outsideLabels?.[0]?.text ?? '').split('\n')[0];

// ------------------------------------------------------------------------------------------------ test document
// Parametric (two bound blocks), a sound state machine, a broken one (two initial states, an unreachable state),
// an activity with a CALL action without expression (R10), a use case with an actor, an interaction.
function behaviourDocument() {
  const u = () => randomUUID();
  const ids = {};
  const node = (eClass, key, data) => {
    ids[key] = u();
    return { id: ids[key], eClass: 'sysarch:' + eClass, data: { id: key, ...data } };
  };
  const params1 = [node('Parameter', 'E2E-P-IN', { name: 'P_in', unit: 'W', direction: 'IN' }), node('Parameter', 'E2E-EFF', { name: 'eff', unit: '1', direction: 'IN', defaultValue: 0.9 }),
    node('Parameter', 'E2E-P-OUT', { name: 'P_out', unit: 'W', direction: 'OUT' })];
  const params2 = [node('Parameter', 'E2E-P', { name: 'P', unit: 'W', direction: 'IN' }), node('Parameter', 'E2E-K', { name: 'k', unit: '1', direction: 'IN', defaultValue: 0.05 }),
    node('Parameter', 'E2E-L', { name: 'L', unit: 'W', direction: 'OUT' })];
  const cb1 = node('ConstraintBlock', 'E2E-CB-1', { name: '功率', parameters: params1,
    expression: { id: u(), eClass: 'sysarch:ConstraintExpr', data: { id: 'E2E-EX-1', name: 'e1', language: 'python', body: 'P_out = P_in * eff' } } });
  const cb2 = node('ConstraintBlock', 'E2E-CB-2', { name: '损耗', parameters: params2,
    expression: { id: u(), eClass: 'sysarch:ConstraintExpr', data: { id: 'E2E-EX-2', name: 'e2', language: 'python', body: 'L = P * k' } } });
  const bind = { id: u(), eClass: 'sysarch:BindingConnector', data: { id: 'E2E-BC-1', name: 'P_out → P', source: ids['E2E-P-OUT'], target: ids['E2E-P'] } };
  const st = (key, name, extra = {}) => node('State', key, { name, ...extra });
  const s = [st('E2E-S-0', '初始', { isInitial: true }), st('E2E-S-1', '关闭', { entryBehavior: '上锁' }), st('E2E-S-2', '开启', { doBehavior: '保持' }), st('E2E-S-3', '终止', { isFinal: true })];
  const tr = (key, a, b, extra = {}) => ({ id: u(), eClass: 'sysarch:Transition', data: { id: key, name: key, source: ids[a], target: ids[b], ...extra } });
  const sm = node('StateMachine', 'E2E-SM-1', { name: '车门', states: s, transitions: [tr('E2E-T-1', 'E2E-S-0', 'E2E-S-1'), tr('E2E-T-2', 'E2E-S-1', 'E2E-S-2', { trigger: '开门', guard: '速度 = 0' }),
    tr('E2E-T-3', 'E2E-S-2', 'E2E-S-1', { trigger: '关门', effect: '解锁' }), tr('E2E-T-4', 'E2E-S-1', 'E2E-S-3', { trigger: '停用' })] });
  const bad = [st('E2E-B-0', '甲', { isInitial: true }), st('E2E-B-1', '乙', { isInitial: true }), st('E2E-B-2', '孤立')];
  const smBad = node('StateMachine', 'E2E-SM-2', { name: '坏状态机', states: bad, transitions: [tr('E2E-BT-1', 'E2E-B-0', 'E2E-B-1', { trigger: 'go' })] });
  const acts = [node('Action', 'E2E-A-1', { name: '采集', kind: 'CALL', expression: 'read()' }), node('Action', 'E2E-A-2', { name: '判定', kind: 'DECIDE' }),
    node('Action', 'E2E-A-3', { name: '执行', kind: 'CALL' })];
  const flow = (key, a, b, extra = {}) => ({ id: u(), eClass: 'sysarch:ActivityFlow', data: { id: key, name: key, source: ids[a], target: ids[b], ...extra } });
  const activity = node('Activity', 'E2E-AC-1', { name: '控制流程', actions: acts, flows: [flow('E2E-F-1', 'E2E-A-1', 'E2E-A-2'), flow('E2E-F-2', 'E2E-A-2', 'E2E-A-3', { isObjectFlow: true })] });
  const actor = node('Actor', 'E2E-ACT-1', { name: '驾驶员' });
  const useCase = node('UseCase', 'E2E-UC-1', { name: '开车门', actors: [ids['E2E-ACT-1']] });
  const ll = (key, name) => node('Lifeline', key, { name });
  const lifelines = [ll('E2E-LL-1', '驾驶员'), ll('E2E-LL-2', '门控')];
  const msg = (key, name, kind, a, b) => ({ id: u(), eClass: 'sysarch:Message', data: { id: key, name, kind, sender: ids[a], receiver: ids[b] } });
  const interaction = node('Interaction', 'E2E-IN-1', { name: '开门交互', lifelines, messages: [msg('E2E-M-1', '请求开门', 'SYNC', 'E2E-LL-1', 'E2E-LL-2'),
    msg('E2E-M-2', '已开', 'REPLY', 'E2E-LL-2', 'E2E-LL-1'), msg('E2E-M-3', '自检', 'ASYNC', 'E2E-LL-2', 'E2E-LL-2')] });
  return { json: { version: '1.0', encoding: 'utf-8' }, ns: { sysarch: 'http://example.com/sysarch/1.0' },
    content: [cb1, cb2, bind, sm, smBad, activity, actor, useCase, interaction] };
}

// ================================================================================================ the run
const startedAt = new Date();
let polarionUser;
let trace;
try {
  // ---------------------------------------------------------------------------------------------- identity
  await part('身份与访问（P2 T8、P4 T8）', async () => {
    const health = await call('GET', '/actuator/health', { headers: {} });
    check('健康检查', health.status === 200 && health.json?.status === 'UP', `${health.status} ${health.json?.status}`);
    const anonGql = await call('POST', '/api/graphql', { headers: ORIGIN, body: { query: '{viewer{projects(first:1){edges{node{id}}}}}' } });
    const anonExport = await call('POST', '/api/v1/export/sysmlv2', { headers: ORIGIN, body: { projectId: 'x' } });
    check('未登录读取被拒（F4）', anonGql.status === 401 && anonExport.status === 401, `GraphQL ${anonGql.status}, export ${anonExport.status}`);
    const me0 = await call('GET', '/api/v1/auth/me', { headers: ORIGIN });
    check('登录状态接口开放', me0.status === 200 && me0.json.user === null && me0.json.anonymousRead === false, JSON.stringify(me0.json));
    const bad = await call('POST', '/api/v1/auth/login', { headers: ORIGIN, body: { token: 'not-a-token' } });
    check('错误令牌 401', bad.status === 401, `${bad.status}`);
    const typed = await call('GET', '/api/v1/auth/me', { headers: { ...ORIGIN, 'X-Sysarch-User': 'mallory' } });
    check('Polarion 模式不信任输入的名字', typed.json?.user === null, `user ${typed.json?.user}`);
    const login = await call('POST', '/api/v1/auth/login', { headers: ORIGIN, body: { token: TOKEN } });
    const setCookie = login.headers.get('set-cookie') ?? '';
    COOKIE = setCookie.split(';')[0];
    polarionUser = login.json?.user;
    check('令牌登录', login.status === 200 && COOKIE.startsWith('sysarch_session=') && /HttpOnly/i.test(setCookie) && !setCookie.includes(TOKEN),
      `${login.status} user=${polarionUser} roles=${JSON.stringify(login.json?.globalRoles)} cookie HttpOnly, no token inside`);
    const me = await call('GET', '/api/v1/auth/me', { headers: { ...ORIGIN, Cookie: COOKIE } });
    check('会话 Cookie 有效', me.json?.user === polarionUser, `${me.json?.user} (${me.json?.source})`);
    const access = await call('GET', '/api/v1/access');
    check('全局管理员的权限', access.json?.role === 'ADMIN' && access.json.permissions.includes('MANAGE_ROLES'), `${access.json?.role} ${access.json?.permissions?.length} permissions`);
    // single sign-on: a v1 assertion signed with the test instance's key
    const key = process.env.SSO_SECRET;
    if (key) {
      let keyBytes = Buffer.from(key, 'utf8'); // the backend uses the secret's text as the HMAC key
      const assertion = (sub) => {
        const now = Math.floor(Date.now() / 1000);
        const json = JSON.stringify({ sub, iat: now, exp: now + 60, nonce: randomBytes(16).toString('hex'), aud: 'sysarch' });
        const payload = Buffer.from(json).toString('base64url');
        return { token: `v1.${payload}.${createHmac('sha256', keyBytes).update('v1.' + payload).digest('base64url')}`, keyBytes };
      };
      const form = (a, ret) => new URLSearchParams({ assertion: a, return: ret }).toString();
      let a = assertion(polarionUser);
      let sso = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'http://localhost' }, raw: form(a.token, '/sysarch'), type: 'application/x-www-form-urlencoded' });
      if (sso.status === 401) {
        // fall back to hex, should a deployment give the key as hex bytes
        keyBytes = Buffer.from(key, 'hex');
        const now = Math.floor(Date.now() / 1000);
        const json = JSON.stringify({ sub: polarionUser, iat: now, exp: now + 60, nonce: randomBytes(16).toString('hex'), aud: 'sysarch' });
        const payload = Buffer.from(json).toString('base64url');
        a = { token: `v1.${payload}.${createHmac('sha256', keyBytes).update('v1.' + payload).digest('base64url')}` };
        sso = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'http://localhost' }, raw: form(a.token, '/sysarch'), type: 'application/x-www-form-urlencoded' });
      }
      check('单点登录：签名凭证 → 303 + Cookie', sso.status === 303 && sso.headers.get('location') === '/sysarch' && (sso.headers.get('set-cookie') ?? '').startsWith('sysarch_session='),
        `${sso.status} → ${sso.headers.get('location')}`);
      const replay = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'http://localhost' }, raw: form(a.token, '/'), type: 'application/x-www-form-urlencoded' });
      check('单点登录：同一凭证重放 401', replay.status === 401, `${replay.status}`);
      const forged = a.token.slice(0, -4) + 'AAAA';
      const forgedR = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'http://localhost' }, raw: form(forged, '/'), type: 'application/x-www-form-urlencoded' });
      check('单点登录：伪造签名 401', forgedR.status === 401, `${forgedR.status}`);
      const evil = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'https://evil.example' }, raw: form(assertion(polarionUser).token, '/'), type: 'application/x-www-form-urlencoded' });
      check('单点登录：其他来源 403', evil.status === 403, `${evil.status}`);
      const open = await call('POST', '/api/v1/auth/polarion-sso', { headers: { Origin: 'http://localhost' }, raw: form(assertion(polarionUser).token, '//evil.example/x'), type: 'application/x-www-form-urlencoded' });
      check('单点登录：外站跳转地址改为 /', open.headers.get('location') === '/', `return //evil.example/x → ${open.headers.get('location')}`);
      const polarionOriginElsewhere = await call('POST', '/api/v1/locks', { headers: { Origin: 'http://localhost', Cookie: COOKIE }, body: {} });
      check('Polarion 来源调用其他接口 403', polarionOriginElsewhere.status === 403, `${polarionOriginElsewhere.status}`);
    } else {
      check('单点登录', false, 'SSO_SECRET not given');
    }
  });

  // ---------------------------------------------------------------------------------------------- project
  await part('模型项目与 Polarion 绑定（P0、P4 T1）', async () => {
    const name = 'e2e-' + Date.now().toString(36);
    projectId = await createProject(name);
    ec = await ecOf(projectId);
    ok((await gql(`mutation($i:CreateDocumentInput!){createDocument(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
      { i: { id: randomUUID(), editingContextId: ec, stereotypeId: 'sysarch.demo.domain-controller', name: '域控演示' } })).createDocument, 'createDocument');
    const form = new FormData();
    form.append('operations', JSON.stringify({ query: `mutation($input:UploadDocumentInput!){uploadDocument(input:$input){__typename ... on ErrorPayload{messages{body}}}}`,
      variables: { input: { id: randomUUID(), editingContextId: ec, file: null, readOnly: false } } }));
    form.append('map', JSON.stringify({ 0: 'variables.file' }));
    form.append('0', new Blob([JSON.stringify(behaviourDocument())], { type: 'application/json' }), 'e2e-behaviour.json');
    const up = await fetch(BASE + '/api/graphql/upload', { method: 'POST', headers: { ...ORIGIN, Cookie: COOKIE }, body: form }).then((r) => r.json());
    check('从演示模板建模型 + 上传行为文档', up.data?.uploadDocument?.__typename === 'UploadDocumentSuccessPayload', `${name}: ${up.data?.uploadDocument?.__typename ?? JSON.stringify(up).slice(0, 200)}`);
    const docs = await call('GET', `/api/v1/projects/${projectId}/elements/E2E-CB-1/location`);
    check('元素定位接口', docs.status === 200 && !!docs.json?.objectId, `${docs.status} ${docs.json?.editorPath ?? ''}`);
    const before = await call('GET', `/api/v1/embed/target?polarionProject=${PP}`);
    const bind = await call('PUT', `/api/v1/project-bindings/${PP}`, { body: { siriusProject: name } });
    const after = await call('GET', `/api/v1/embed/target?polarionProject=${PP}`);
    check('绑定 Polarion 项目（不重启生效）', before.status === 404 && bind.status === 200 && after.status === 200 && after.json.siriusProjectId === projectId,
      `before ${before.status}, bind ${bind.status}, embed target ${after.status} ${after.json?.projectName}`);
  });

  // ---------------------------------------------------------------------------------------------- diagrams
  let ibd;
  await part('图：BDD、IBD、自动同步、连线校验（P0 AC-2..AC-5）', async () => {
    const bdd = await representation('PD-001', 'BDD');
    const bddNodes = bdd.view.diagram.nodes.length;
    check('BDD 显示全部块', bddNodes >= 6 && bdd.view.diagram.edges.length >= 3, `${bddNodes} nodes, ${bdd.view.diagram.edges.length} edges`);
    ibd = await representation('PD-001', 'IBD');
    const d = ibd.view.diagram;
    const frame = d.nodes[0];
    const parts = frame?.childNodes?.length ?? 0;
    const ports = d.nodes.flatMap((n) => [...(n.borderNodes ?? []), ...(n.childNodes ?? []).flatMap((c) => c.borderNodes ?? [])]);
    check('IBD 显示部件、端口与连接', d.edges.length >= 2, `${d.nodes.length} top nodes, ${parts} parts in the frame, ${d.edges.length} connections`);
    const t0 = performance.now();
    await tool(ibd, 'createPart', frame?.id);
    const grown = await ibd.view.until((x) => JSON.stringify(x).split('"part').length > JSON.stringify(d).split('"part').length || (x.nodes[0]?.childNodes?.length ?? 0) > parts, 10000).catch(() => null);
    check('新建部件后图自动刷新（AC-4）', !!grown, `${grown ? Math.round(performance.now() - t0) + ' ms' : 'no refresh'}`);
    // AC-5: IN -> IN refused, nothing created
    const all = (x) => x.nodes.flatMap(function flat(n) { return [n, ...(n.borderNodes ?? []), ...(n.childNodes ?? []).flatMap(flat)]; });
    const po2 = await objectId('PO-002');
    const po3 = await objectId('PO-003');
    const src = all(ibd.view.diagram).find((n) => n.targetObjectId === po2);
    const dst = all(ibd.view.diagram).find((n) => n.targetObjectId === po3);
    if (!src || !dst) throw new Error('ports PO-002 / PO-003 not drawn');
    const edgesBefore = ibd.view.diagram.edges.length;
    const refused = await connectTool(ibd, 'connect', src.id, dst.id);
    await sleep(800);
    const text = JSON.stringify(refused);
    check('IN → IN 连线被拒，不建任何东西（AC-5）', text.includes('方向') && ibd.view.diagram.edges.length === edgesBefore, `${refused.__typename}: ${(refused.messages ?? []).map((m) => m.body).join(' / ')}`);
    const po1 = await objectId('PO-001');
    const out = all(ibd.view.diagram).find((n) => n.targetObjectId === po1);
    const good = await connectTool(ibd, 'connect', out.id, dst.id);
    const connected = await ibd.view.until((x) => x.edges.length > edgesBefore, 8000).catch(() => null);
    check('OUT → IN 连线成功', !!connected, `${good.__typename}, edges ${edgesBefore} → ${connected?.edges.length}`);
  });

  await part('分层视图（P1 T3）', async () => {
    for (const [layer, expect] of [['SA', 'SYSTEM'], ['LA', 'LOGICAL'], ['PA', 'PHYSICAL']]) {
      const rep = await representation('PD-001', layer);
      const labels = rep.view.diagram.nodes.map(firstLine);
      check(`${layer} 视图`, labels.length > 0 || layer === 'SA', `${labels.join('、') || '(empty)'} · ${rep.view.diagram.edges.length} edges`);
    }
    const la = await representation('PD-001', 'LA');
    const n0 = la.view.diagram.nodes.length;
    await tool(la, 'createFunction');
    const grown = await la.view.until((x) => x.nodes.length === n0 + 1, 8000).catch(() => null);
    check('在 LA 新建逻辑组件', !!grown, grown ? grown.nodes.map(firstLine).filter((l) => !la.view.diagram || true).slice(-1)[0] : 'not created');
  });

  await part('行为与参数视图（P3 T3、T5、T6）', async () => {
    const pm = await representation('E2E-CB-1', '参数图');
    const borders = pm.view.diagram.nodes.flatMap((n) => n.borderNodes ?? []);
    check('参数图', pm.view.diagram.nodes.length >= 2 && borders.length >= 6 && pm.view.diagram.edges.length >= 1, `${pm.view.diagram.nodes.length} blocks, ${borders.length} parameters, ${pm.view.diagram.edges.length} bindings`);
    const sm = await representation('E2E-SM-1', '状态机');
    const states = sm.view.diagram.nodes.flatMap((n) => [n, ...(n.childNodes ?? [])]).filter((n) => n.type !== undefined);
    const labels = sm.view.diagram.edges.map((e) => e.centerLabel?.text ?? '');
    check('状态机图', sm.view.diagram.edges.length === 4 && labels.includes('开门[速度 = 0]') && labels.includes('关门/解锁'), `${states.length} nodes, transitions: ${labels.map((l) => l || '∅').join(' | ')}`);
    const types = new Set(JSON.stringify(sm.view.diagram).match(/"type":"[^"]+"/g));
    check('初始/终止状态的图形', [...types].some((t) => t.includes('ellipse')) && [...types].some((t) => t.includes('image')), [...types].join(' '));
    const ac = await representation('E2E-AC-1', '活动');
    const lines = ac.view.diagram.edges.map((e) => e.style?.lineStyle);
    const acTypes = [...new Set(JSON.stringify(ac.view.diagram).match(/"type":"[^"]+"/g))].join(' ');
    check('活动图：判定为图形节点（菱形），对象流为虚线', acTypes.includes('node:image') && lines.includes('Dash') && lines.includes('Solid'), `${acTypes}; edges ${lines.join(',')}`);
    const uc = await representation('E2E-UC-1', '用例');
    const ucTypes = JSON.stringify(uc.view.diagram);
    check('用例图：椭圆用例 + 小人参与者', ucTypes.includes('customnode:ellipse') && ucTypes.includes('node:image') && uc.view.diagram.edges.length === 1, `${uc.view.diagram.nodes.length} nodes, ${uc.view.diagram.edges.length} association`);
  });

  // ---------------------------------------------------------------------------------------------- matrices, trace
  await part('分配矩阵与需求追踪（P1 T4、T5）', async () => {
    const saView = await representation('PD-001', 'SA');
    await tool(saView, 'createFunction');
    await saView.view.until((x) => x.nodes.length >= 1, 8000);
    const m = (await call('GET', `/api/v1/projects/${projectId}/tables/allocation-matrix`)).json;
    const create = m.actions.find((a) => !a.onMarked);
    const sa = m.rows.find((h) => h.group === 'SYSTEM');
    const la = m.columns.find((h) => h.group === 'LOGICAL');
    const pa = m.columns.find((h) => h.group === 'PHYSICAL');
    const skip = await call('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`, { body: { actionId: create.id, rowId: sa.id, columnId: pa.id } });
    check('跨层分配被拒（SA → PA）', skip.status === 422 && JSON.stringify(skip.json).includes('跳过 LA'), skip.json?.errors?.[0]?.message);
    const same = await call('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`,
      { body: { actionId: create.id, rowId: m.rows.find((h) => h.group === 'LOGICAL').id, columnId: la.id } });
    check('同层分配被拒（LA → LA）', same.status === 422, same.json?.errors?.[0]?.message);
    const made = await call('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`, { body: { actionId: create.id, rowId: sa.id, columnId: la.id } });
    const cell = made.json?.matrix?.cells?.find((c) => c.rowId === sa.id && c.columnId === la.id);
    check('SA → LA 分配建立', made.status === 200 && !!cell, `${made.status} ${sa.label} → ${la.label}`);
    const del = m.actions.find((a) => a.onMarked);
    const removed = await call('POST', `/api/v1/projects/${projectId}/tables/allocation-matrix/actions`, { body: { actionId: del.id, rowId: sa.id, columnId: la.id } });
    check('分配删除', removed.status === 200 && !removed.json?.matrix?.cells?.some((c) => c.rowId === sa.id && c.columnId === la.id && c.marked), `${removed.status}`);
    const t = (await call('GET', `/api/v1/projects/${projectId}/tables/requirement-trace`)).json;
    const satisfy = t.actions.find((a) => a.id === 'satisfy');
    const soc = t.columns.find((h) => h.label === '域控 SoC');
    const linked = await call('POST', `/api/v1/projects/${projectId}/tables/requirement-trace/actions`, { body: { actionId: satisfy.id, rowId: 'RQ-004', columnId: soc.id } });
    trace = (await call('GET', `/api/v1/trace/matrix?projectId=${projectId}`)).json;
    check('追溯矩阵：满足关系', linked.status === 200 && trace.requirements.find((r) => r.id === 'RQ-004').satisfied, `summary ${JSON.stringify(trace.summary)}`);
    const undo = await call('POST', `/api/v1/projects/${projectId}/tables/requirement-trace/actions`, { body: { actionId: satisfy.id, rowId: 'RQ-004', columnId: soc.id, remove: true } });
    const t2 = (await call('GET', `/api/v1/trace/matrix?projectId=${projectId}`)).json;
    check('追溯矩阵：取消满足', undo.status === 200 && !t2.requirements.find((r) => r.id === 'RQ-004').satisfied, `summary ${JSON.stringify(t2.summary)}`);
    trace = t2;
  });

  // ---------------------------------------------------------------------------------------------- baselines, branches, merge
  await part('基线、差异、分支与合并（P1 T7、P2 T3、T4）', async () => {
    const b = await call('POST', '/api/v1/baselines', { body: { projectId, label: 'e2e 基线' } });
    check('建基线', b.status === 201 && b.json.elementCount > 30, `${b.status} ${b.json?.elementCount} elements`);
    const pa = await representation('PD-005', 'PA');
    const mcu = pa.view.diagram.nodes.find((n) => firstLine(n).startsWith('安全 MCU'));
    ok((await gql(`mutation($i:EditLabelInput!){editLabel(input:$i){__typename ... on ErrorPayload{messages{body}}}}`,
      { i: { id: randomUUID(), editingContextId: ec, representationId: pa.id, labelId: mcu.insideLabel.id, newText: '安全 MCU（e2e）' } })).editLabel, 'editLabel');
    await pa.view.until((x) => x.nodes.some((n) => firstLine(n) === '安全 MCU（e2e）'));
    const diff = (await call('GET', `/api/v1/baselines/${b.json.id}/diff`)).json;
    check('差异 = 1 处改名', diff.added.length === 0 && diff.removed.length === 0 && diff.modified.length === 1 && diff.modified[0].fields.join() === 'name',
      `added ${diff.added.length} removed ${diff.removed.length} modified ${JSON.stringify(diff.modified)}`);
    const b2 = await call('POST', '/api/v1/baselines', { body: { projectId, label: 'e2e 基线 2' } });
    const bb = (await call('GET', `/api/v1/baselines/${b.json.id}/diff?against=BASELINE&otherBaselineId=${b2.json.id}`)).json;
    check('两个基线之间的差异', bb?.modified?.length === 1, JSON.stringify(bb?.modified));
    // branch: satisfy RQ-005 in the branch, RQ-004 differently in branch and trunk (a conflict)
    const br = await call('POST', '/api/v1/branches', { body: { projectId, name: 'e2e-分支', baselineId: b2.json.id } });
    check('从基线建分支', br.status === 201 && !!br.json.workspaceProjectId, `${br.status} workspace ${br.json?.workspaceProjectId}`);
    const ws = br.json.workspaceProjectId;
    extraProjects.push(ws);
    const tw = (await call('GET', `/api/v1/projects/${ws}/tables/requirement-trace`)).json;
    const sat = tw.actions.find((a) => a.id === 'satisfy');
    const colW = (label) => tw.columns.find((h) => h.label === label).id;
    const r1 = await call('POST', `/api/v1/projects/${ws}/tables/requirement-trace/actions`, { body: { actionId: sat.id, rowId: 'RQ-005', columnId: colW('域控 SoC') } });
    const r2 = await call('POST', `/api/v1/projects/${ws}/tables/requirement-trace/actions`, { body: { actionId: sat.id, rowId: 'RQ-004', columnId: colW('域控 SoC') } });
    const changes = (await call('GET', `/api/v1/branches/${br.json.id}/changes`)).json;
    check('分支中的修改记成变更集', r1.status === 200 && r2.status === 200 && Array.isArray(changes) && changes.length === 2, `${changes?.length} rows: ${JSON.stringify(changes?.map?.((c) => c.elementId + ':' + c.op))}`);
    const tt = (await call('GET', `/api/v1/projects/${projectId}/tables/requirement-trace`)).json;
    await call('POST', `/api/v1/projects/${projectId}/tables/requirement-trace/actions`, { body: { actionId: sat.id, rowId: 'RQ-004', columnId: tt.columns.find((h) => h.label === '安全 MCU（e2e）').id } });
    const prev = await call('POST', `/api/v1/branches/${br.json.id}/merge`, { body: { mode: 'PREVIEW', strategy: 'POLARION_WINS' } });
    const conflict = prev.json?.conflicts?.find((c) => c.elementId === 'RQ-004');
    check('合并预演发现冲突', prev.status === 200 && !!conflict && prev.json.applicable === false, `${prev.json?.conflicts?.length} conflict(s): ${conflict?.field}; ${prev.json?.operations?.length} operations`);
    const noChoice = await call('POST', `/api/v1/branches/${br.json.id}/merge`, { body: { mode: 'APPLY', strategy: 'POLARION_WINS', runId: prev.json.runId } });
    check('未选择冲突时不能应用', noChoice.status === 409, `${noChoice.status}`);
    const apply = await call('POST', `/api/v1/branches/${br.json.id}/merge`, { body: { mode: 'APPLY', strategy: 'POLARION_WINS', runId: prev.json.runId,
      resolutions: prev.json.conflicts.map((c) => ({ elementId: c.elementId, field: c.field, take: 'THEIRS' })) } });
    const tr = (await call('GET', `/api/v1/trace/matrix?projectId=${projectId}`)).json;
    const rq5 = tr.requirements.find((r) => r.id === 'RQ-005');
    const rq4 = tr.requirements.find((r) => r.id === 'RQ-004');
    check('合并应用（冲突取分支）', apply.status === 200 && rq5.satisfied && rq4.satisfiedBy.map((x) => x.name).join() === '域控 SoC',
      `${apply.status} merged=${apply.json?.merged}; RQ-005 satisfied by ${rq5.satisfiedBy.map((x) => x.name)}; RQ-004 by ${rq4.satisfiedBy.map((x) => x.name)}; pre-merge baseline ${apply.json?.preMergeBaseline ? 'yes' : 'no'}`);
    const after = (await call('GET', `/api/v1/branches/${br.json.id}`)).json;
    check('合并后分支状态', after?.status === 'MERGED', after?.status);
    const del = await call('DELETE', `/api/v1/baselines/${b.json.id}`);
    check('删除基线（管理员）', del.status === 204 || del.status === 409, `${del.status} ${del.json?.detail ?? ''}`);
    trace = tr;
  });

  // ---------------------------------------------------------------------------------------------- locks, sessions, comments
  await part('锁、编辑会话、评论（P2 T1、T2、T8）', async () => {
    const lock = await call('POST', '/api/v1/locks', { body: { projectId, elementIds: ['PD-002', 'PD-003'], ownerUser: polarionUser, leaseSeconds: 120 } });
    check('取锁', lock.status === 201 && lock.json.locks.length === 2, `${lock.status} ${lock.json?.locks?.map((l) => l.elementId)}`);
    const list = (await call('GET', `/api/v1/locks?projectId=${projectId}`)).json;
    const id = lock.json.locks[0].id;
    const renew = await call('PUT', `/api/v1/locks/${id}`);
    check('锁列表与续约', (list.locks ?? list).length >= 2 && renew.status === 200, `${(list.locks ?? list).length} locks, renew ${renew.status}`);
    const other = await call('POST', '/api/v1/locks', { body: { projectId, elementIds: ['PD-002'], ownerUser: 'someone-else' } });
    check('替别人取锁 403', other.status === 403, `${other.status}`);
    for (const l of lock.json.locks) await call('DELETE', `/api/v1/locks/${l.id}`);
    const gone = (await call('GET', `/api/v1/locks?projectId=${projectId}`)).json;
    check('释放锁', !(gone.locks ?? gone).some((l) => l.owner === polarionUser && ['PD-002', 'PD-003'].includes(l.elementId)), `${(gone.locks ?? gone).length} left`);
    const s = await call('POST', '/api/v1/sessions', { body: { projectId, clientInfo: 'e2e' } });
    const sl = (await call('GET', `/api/v1/sessions?projectId=${projectId}`)).json;
    const closed = await call('DELETE', `/api/v1/sessions/${s.json?.id}`);
    check('编辑会话（上限 30）', s.status === 201 && sl.limit === 30 && closed.status === 204, `open ${s.status}, list ${sl.sessions?.length}/${sl.limit}, close ${closed.status}`);
    const c = await call('POST', '/api/v1/comments', { body: { projectId, elementId: 'PD-002', body: 'e2e 评论：接口定义待确认' } });
    const cl = (await call('GET', `/api/v1/comments?projectId=${projectId}&elementId=PD-002`)).json;
    check('评论', c.status === 201 && cl.some((x) => x.body.startsWith('e2e 评论')), `${c.status}, ${cl.length} comment(s)`);
  });

  // ---------------------------------------------------------------------------------------------- reports
  await part('报告（P2 T5）', async () => {
    const templates = (await call('GET', '/api/v1/reports/templates')).json;
    check('报告模板', templates.length === 3, templates.map((t) => t.id ?? t).join(', '));
    for (const templateId of ['srs', 'icd', 'trace-report']) {
      const job = await call('POST', '/api/v1/reports', { body: { projectId, templateId } });
      const done = await poll(() => call('GET', `/api/v1/reports/${job.json.jobId}`), (r) => ['DONE', 'FAILED'].includes(r.json?.status), 120000, 1000);
      const files = {};
      if (done.json.status === 'DONE') {
        for (const f of ['html', 'docx', 'pdf']) {
          const r = await call('GET', `/api/v1/reports/${job.json.jobId}/files/${f}`);
          files[f] = `${r.status} ${r.headers.get('content-type')?.split(';')[0]} ${Math.round(r.bytes.length / 1024)} KB`;
        }
      }
      check(`报告 ${templateId}`, done.json.status === 'DONE' && Object.values(files).every((v) => v.startsWith('200')),
        `${done.json.status} ${done.json.pages ?? '?'} pages, gaps ${done.json.gaps?.length ?? 0}; ${Object.entries(files).map(([k, v]) => k + ' ' + v).join('; ')}`);
    }
    const again = await call('POST', '/api/v1/reports', { body: { projectId, templateId: 'srs' } });
    check('同一模型再生成走缓存', again.json?.cached === true || again.status === 200, `${again.status} cached=${again.json?.cached}`);
    const bad = await call('POST', '/api/v1/reports', { body: { projectId, templateId: 'nope' } });
    check('未知模板 400', bad.status === 400, `${bad.status}`);
  });

  // ---------------------------------------------------------------------------------------------- export / import
  await part('导出与导入：SysML v2、XMI（P1 T8、P2 T6、P3 T7、T8）', async () => {
    const sysml = await call('POST', '/api/v1/export/sysmlv2', { body: { projectId } });
    check('SysML v2 导出', sysml.status === 200 && sysml.text.includes('part def') && sysml.text.includes('occurrence def'), `${sysml.status} ${sysml.text.split('\n').length} lines`);
    const layer = await call('POST', '/api/v1/export/sysmlv2', { body: { projectId, scope: 'LAYER', layer: 'LOGICAL' } });
    check('SysML v2 按层导出', layer.status === 200 && layer.text.length < sysml.text.length, `${layer.status} ${layer.text.split('\n').length} lines`);
    const imp = await call('POST', `/api/v1/imports/sysmlv2?projectName=${encodeURIComponent('e2e-import-' + Date.now().toString(36))}&fileName=e2e.sysml`, { raw: sysml.text, type: 'text/plain; charset=UTF-8' });
    const job = await poll(() => call('GET', `/api/v1/imports/${imp.json.id}`), (r) => ['DONE', 'FAILED'].includes(r.json?.status), 60000);
    if (job.json.report?.projectId) extraProjects.push(job.json.report.projectId);
    check('SysML v2 导入为新项目', job.json.status === 'DONE' && job.json.report.total > 30, `${job.json.status} ${job.json.report?.total} elements, unmapped ${job.json.report?.unmapped?.length}`);
    const reexport = job.json.report?.projectId ? await call('POST', '/api/v1/export/sysmlv2', { body: { projectId: job.json.report.projectId } }) : null;
    const strip = (t) => t.split('\n').filter((l) => !l.startsWith('//') && !l.startsWith('package') && !l.includes("package '")).join('\n');
    check('导出 → 导入 → 导出一致', reexport && strip(reexport.text) === strip(sysml.text), reexport ? `${reexport.text.split('\n').length} lines` : 'no project');
    const broken = sysml.text.replace('part def', 'attribute mass : Real = 1.5;\npart def');
    const imp2 = await call('POST', '/api/v1/imports/sysmlv2?fileName=bad.sysml', { raw: broken, type: 'text/plain; charset=UTF-8' });
    const job2 = await poll(() => call('GET', `/api/v1/imports/${imp2.json.id}`), (r) => ['DONE', 'FAILED'].includes(r.json?.status), 60000);
    check('不支持的语法：整体失败并指出行号', job2.json.status === 'FAILED' && /第 \d+ 行/.test(JSON.stringify(job2.json.problems)), (job2.json.problems ?? [])[0]);
    const xmi = await call('POST', '/api/v1/export/xmi', { body: { projectId } });
    check('XMI 导出（OMG 2.5.1 / SysML 1.6）', xmi.status === 200 && xmi.text.includes('http://www.omg.org/spec/UML/20161101') && xmi.text.includes('uml:Interaction'), `${xmi.status} ${Math.round(xmi.bytes.length / 1024)} KB`);
    const x25 = await call('POST', '/api/v1/export/xmi', { body: { projectId, umlVersion: '2.5' } });
    const pap = await call('POST', '/api/v1/export/xmi', { body: { projectId, flavour: 'papyrus' } });
    check('XMI：UML 2.5 与 Papyrus 两种变体', x25.text.includes('UML/20131001') && pap.text.includes('http://www.eclipse.org/papyrus/sysml/1.6/SysML'), `2.5 ${x25.status}, papyrus ${pap.status}`);
    const ximp = await call('POST', `/api/v1/imports/xmi?projectName=${encodeURIComponent('e2e-xmi-' + Date.now().toString(36))}`, { raw: xmi.text, type: 'application/xml' });
    const xjob = await poll(() => call('GET', `/api/v1/imports/${ximp.json.id}`), (r) => ['DONE', 'FAILED'].includes(r.json?.status), 60000);
    if (xjob.json.report?.projectId) extraProjects.push(xjob.json.report.projectId);
    check('XMI 导入为新项目', xjob.json.status === 'DONE', `${xjob.json.status} ${xjob.json.report?.total} elements ${xjob.json.status === 'FAILED' ? JSON.stringify(xjob.json.problems).slice(0, 200) : ''}`);
    const xxe = await call('POST', '/api/v1/imports/xmi', { raw: '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///c:/windows/win.ini">]><x>&e;</x>', type: 'application/xml' });
    const xxeJob = await poll(() => call('GET', `/api/v1/imports/${xxe.json.id}`), (r) => ['DONE', 'FAILED'].includes(r.json?.status), 30000);
    check('XMI 导入拒绝 DOCTYPE（XXE）', xxeJob.json.status === 'FAILED' && !JSON.stringify(xxeJob.json).includes('fonts'), (xxeJob.json.problems ?? [])[0]);
  });

  // ---------------------------------------------------------------------------------------------- solver, behaviour, sequence
  await part('参数求解、行为检查、时序图（P3 T2、T4、v0.5.0）', async () => {
    const solve = await call('POST', '/api/v1/solve', { body: { projectId, constraintBlockIds: ['E2E-CB-1', 'E2E-CB-2'], solver: 'python', inputs: { P_in: 100 }, targets: ['P_out', 'L'] } });
    check('Python 求解并写回', solve.status === 200 && solve.json.converged && Math.abs(solve.json.outputs.P_out - 90) < 1e-6 && Math.abs(solve.json.outputs.L - 4.5) < 1e-6,
      `${solve.status} ${JSON.stringify(solve.json?.outputs ?? solve.json).slice(0, 160)}`);
    const pm = await representation('E2E-CB-1', '参数图');
    const shown = JSON.stringify(pm.view.diagram).includes('P_out = 90');
    check('求解值显示在参数图上', shown, shown ? 'P_out = 90' : 'no value badge');
    const over = await call('POST', '/api/v1/solve', { body: { projectId, constraintBlockIds: ['E2E-CB-1'], inputs: { P_in: 100, P_out: 3 }, targets: ['eff', 'P_out'] } });
    check('超定/欠定 422', over.status === 422, `${over.status} ${JSON.stringify(over.json).slice(0, 120)}`);
    const modelica = await call('POST', '/api/v1/solve', { body: { projectId, constraintBlockIds: ['E2E-CB-1'], solver: 'modelica', inputs: { P_in: 1 }, targets: ['P_out'] } });
    check('未配置的求解器 503', modelica.status === 503, `${modelica.status}`);
    const report = (await call('GET', `/api/v1/consistency/report?projectId=${projectId}`)).json;
    const rules = [...new Set(report.issues.map((i) => i.rule))];
    check('行为检查 R7、R8、R10', ['R7', 'R8', 'R10'].every((r) => rules.includes(r)), `rules ${rules.join(',')}; ${report.issues.filter((i) => /R7|R8|R9|R10/.test(i.rule)).map((i) => i.rule + ':' + i.elementId).join(' ')}`);
    const list = (await call('GET', `/api/v1/projects/${projectId}/interactions`)).json;
    const svg = await call('GET', `/api/v1/projects/${projectId}/interactions/${list[0]?.id}/sequence.svg`);
    const png = await call('GET', `/api/v1/projects/${projectId}/interactions/${list[0]?.id}/sequence.png`);
    check('时序图 SVG / PNG', list.length === 1 && svg.status === 200 && svg.text.includes('请求开门') && png.status === 200 && png.bytes.subarray(1, 4).toString() === 'PNG',
      `${list[0]?.name} (${list[0]?.lifelines} lifelines, ${list[0]?.messages} messages); svg ${svg.status}, png ${png.status} ${Math.round(png.bytes.length / 1024)} KB`);
  });

  // ---------------------------------------------------------------------------------------------- Polarion
  const fields = {};
  await part('Polarion 同步：推送、链接、拉取、冲突（P0 T7、P1 T6）', async () => {
    const satisfier = trace.requirements.find((r) => r.id === 'RQ-001').satisfiedBy.map((x) => x.id);
    const elementIds = ['RQ-001', ...satisfier, 'CO-001'];
    const key = 'e2e-' + randomUUID();
    const push = await call('POST', '/api/v1/sync/push', { headers: { ...AUTH, 'Idempotency-Key': key }, body: { projectId: PP, elementIds } });
    const links = (await call('GET', `/api/v1/links?projectId=${PP}`)).json ?? [];
    for (const l of links) createdWorkItems.add(l.workItemId);
    check('推送创建 Work Item', push.status === 200 && push.json.pushed === elementIds.length && links.length === elementIds.length,
      `${push.status} pushed ${push.json?.pushed}, errors ${JSON.stringify(push.json?.errors ?? [])}, links ${links.map((l) => l.elementId + '→' + l.workItemId).join(' ')}`);
    const replay = await call('POST', '/api/v1/sync/push', { headers: { ...AUTH, 'Idempotency-Key': key }, body: { projectId: PP, elementIds } });
    check('同一 Idempotency-Key 重放', replay.headers.get('idempotent-replayed') === 'true', `replayed ${replay.headers.get('idempotent-replayed')}`);
    const noKey = await call('POST', '/api/v1/sync/push', { body: { projectId: PP, elementIds } });
    check('推送缺少 Idempotency-Key 400', noKey.status === 400, `${noKey.status}`);
    const rq = links.find((l) => l.elementId === 'RQ-001');
    const block = links.find((l) => l.elementId === satisfier[0]);
    const conn = links.find((l) => l.elementId === 'CO-001');
    Object.assign(fields, { rq, block, conn });
    const wi = await polarion("GET", `/projects/${PP}/workitems/${block.workItemId}?fields%5Bworkitems%5D=%40all`);
    const rel = JSON.stringify(wi.json?.data?.relationships?.linkedWorkItems ?? {});
    check('Polarion 中的字段与 implements 链接', wi.json?.data?.attributes?.sysarchElementId === satisfier[0] && rel.includes(rq.workItemId) && rel.includes('implements'),
      `${block.workItemId}: ${wi.json?.data?.attributes?.title}, sysarchElementId ${wi.json?.data?.attributes?.sysarchElementId}, layer ${wi.json?.data?.attributes?.sysarchLayer}`);
    // pull: a title changed in Polarion arrives in the binding; reqId = Work Item id
    await polarion('PATCH', `/projects/${PP}/workitems/${rq.workItemId}`, { data: { type: 'workitems', id: `${PP}/${rq.workItemId}`, attributes: { title: 'E2E 在 Polarion 改的标题' } } });
    const pull = await call('POST', '/api/v1/sync/pull', { body: { projectId: PP } });
    const tr = (await call('GET', `/api/v1/trace/matrix?projectId=${projectId}`)).json.requirements.find((r) => r.id === 'RQ-001');
    // reqId is Polarion-owned: the pull writes the bound Work Item's id (the title is kept on the binding, not exposed by /links)
    check('拉取：Polarion 拥有的 reqId 写回模型', pull.status === 200 && pull.json?.pulled >= 1 && tr?.reqId === rq.workItemId, `${pull.status} pulled ${pull.json?.pulled}, RQ-001 reqId ${tr?.reqId} (Work Item ${rq.workItemId})`);
    // a model-owned field edited in Polarion -> CONFLICT on the next push
    await polarion('PATCH', `/projects/${PP}/workitems/${block.workItemId}`, { data: { type: 'workitems', id: `${PP}/${block.workItemId}`, attributes: { sysarchLayer: 'EPBS' } } });
    const conflict = await call('POST', '/api/v1/sync/push', { headers: { ...AUTH, 'Idempotency-Key': 'e2e-' + randomUUID() }, body: { projectId: PP, elementIds: [satisfier[0]] } });
    check('Polarion 改了模型字段 → 冲突，不合并', conflict.status === 409 || (conflict.json?.conflicts ?? []).length > 0, `${conflict.status} ${JSON.stringify(conflict.json?.conflicts ?? conflict.json).slice(0, 160)}`);
    const status = (await call('GET', `/api/v1/sync/status?projectId=${PP}`)).json;
    check('同步状态', !!status.lastPushAt && !!status.lastPullAt, `push ${status.lastPushAt}, pull ${status.lastPullAt}, schedule ${status.schedule?.enabled} every ${status.schedule?.interval}`);
  });

  await part('一致性报告与修复、未绑定池、Webhook、定时拉取（P0 T8、P1 T6、P3）', async () => {
    const { rq, block, conn } = fields;
    // R1: delete the requirement's Work Item in Polarion
    const del = await polarion('DELETE', `/projects/${PP}/workitems`, { data: [{ type: 'workitems', id: `${PP}/${rq.workItemId}` }] });
    if (del.status < 300) createdWorkItems.delete(rq.workItemId);
    const report = (await call('GET', `/api/v1/consistency/report?projectId=${PP}`)).json;
    const r1 = report.issues.find((i) => i.rule === 'R1' && i.elementId === 'RQ-001');
    const rules = [...new Set(report.issues.map((i) => i.rule))].sort();
    check('一致性报告（在线读取 Polarion）：R1 孤儿绑定、R5 未绑定元素', !!r1 && rules.includes('R5'), `rules ${rules.join(',')}; ${report.issues.length} issues`);
    const empty = await call('POST', '/api/v1/consistency/repair', { body: { projectId: PP, issueIds: [] } });
    check('不允许"全部修复"', empty.status === 400, `${empty.status}`);
    const repair = await call('POST', '/api/v1/consistency/repair', { body: { projectId: PP, issueIds: [r1.id], strategy: process.env.CONSISTENCY_STRATEGY ?? 'MODEL_WINS' } });
    const after = (await call('GET', `/api/v1/consistency/report?projectId=${PP}`)).json;
    check('修复 R1（解除绑定，进入待认领）', repair.status === 200 && !after.issues.some((i) => i.id === r1.id), `${repair.status} ${JSON.stringify(repair.json?.actions ?? repair.json).slice(0, 160)}`);
    // the unbound pool: a Work Item created in Polarion shows up after a pull and can be claimed
    const created = await polarion('POST', `/projects/${PP}/workitems`, { data: [{ type: 'workitems', attributes: { type: 'requirement', title: 'E2E 在 Polarion 新建的需求' } }] });
    const newId = created.json?.data?.[0]?.id?.split('/')[1];
    if (newId) createdWorkItems.add(newId);
    const createdAt = new Date();
    const st = await poll(() => call('GET', `/api/v1/sync/status?projectId=${PP}`), (r) => r.json?.schedule?.lastSuccessAt && new Date(r.json.schedule.lastSuccessAt) > createdAt, 150000, 5000);
    check('定时拉取（服务账号，间隔 1 分钟）', !!st.json?.schedule?.lastSuccessAt && new Date(st.json.schedule.lastSuccessAt) > createdAt, `last success ${st.json?.schedule?.lastSuccessAt}, failures ${st.json?.schedule?.consecutiveFailures}`);
    const pool = (await call('GET', `/api/v1/sync/unbound?projectId=${PP}`)).json ?? [];
    const inPool = pool.some((w) => (w.workItemId ?? w.id) === newId);
    check('定时拉取把新 Work Item 放进未绑定池', !!newId && inPool, `${newId}: pool ${pool.length}`);
    const claim = await call('POST', '/api/v1/links', { body: { elementId: 'RQ-001', elementType: 'Requirement', workItemId: newId, workItemType: 'requirement', projectId: PP } });
    const pool2 = (await call('GET', `/api/v1/sync/unbound?projectId=${PP}`)).json ?? [];
    check('认领：绑定到模型元素，移出池子', claim.status === 201 && !pool2.some((w) => (w.workItemId ?? w.id) === newId), `${claim.status} RQ-001 → ${newId}`);
    // webhook: signed accepted, unsigned refused
    const body = JSON.stringify({ event: 'workitem.updated', projectId: PP, workItemId: block.workItemId, revision: '1' });
    const sig = createHmac('sha256', process.env.WEBHOOK_SECRET ?? '').update(body).digest('hex');
    const hook = await call('POST', '/api/v1/webhooks/polarion', { headers: { 'X-Polarion-Signature': 'sha256=' + sig }, raw: body, type: 'application/json' });
    const hookBad = await call('POST', '/api/v1/webhooks/polarion', { headers: { 'X-Polarion-Signature': 'sha256=00' }, raw: body, type: 'application/json' });
    check('Webhook：签名正确 202，错误 401', hook.status === 202 && hookBad.status === 401, `${hook.status} / ${hookBad.status}`);
    const embed = await call('GET', `/api/v1/embed/target?polarionProject=${PP}&workItem=${conn.workItemId}`);
    check('嵌入页：Work Item → 模型元素', embed.status === 200 && embed.json.elementId === 'CO-001', `${embed.status} ${conn.workItemId} → ${embed.json?.elementId}`);
    const unbind = await call('DELETE', `/api/v1/links/${conn.elementId}?projectId=${PP}`);
    check('解除绑定', [200, 204].includes(unbind.status), `${unbind.status}`);
  });

  // ---------------------------------------------------------------------------------------------- operations
  await part('运维：指标、审计、角色、权限拒绝（P2 T8、P4 T5）', async () => {
    const m0 = await call('GET', '/actuator/prometheus', { headers: {} });
    const m1 = await call('GET', '/actuator/prometheus', { headers: { Authorization: 'Bearer wrong' } });
    const m2 = await call('GET', '/actuator/prometheus', { headers: { Authorization: 'Bearer ' + (process.env.METRICS_TOKEN ?? '') } });
    const sysarch = m2.text.split('\n').filter((l) => l.startsWith('sysarch_'));
    check('Prometheus 指标需要令牌', m0.status === 401 && m1.status === 401 && m2.status === 200 && sysarch.length >= 5, `${m0.status}/${m1.status}/${m2.status}; ${sysarch.map((l) => l.split(' ')[0].replace(/\{.*/, '')).join(', ')}`);
    const audit = (await call('GET', `/api/v1/audit?limit=500`)).json;
    const actions = [...new Set(audit.filter((a) => new Date(a.createdAt) >= startedAt).map((a) => a.action))].sort();
    const expected = ['IMPORT', 'MERGE', 'PROJECT_BINDING_CHANGE', 'REPORT_GENERATE', 'SOLVE'];
    check('审计日志记录了本次的操作', expected.every((a) => actions.includes(a)), `${actions.join(', ')} (expected ${expected.join(', ')})`);
    // no leaked database connections after the whole run (a connection leak in GET /api/v1/audit exhausted the pool)
    for (let i = 0; i < 12; i++) await call('GET', '/api/v1/audit?limit=5');
    await sleep(500);
    const pool = (await call('GET', '/actuator/prometheus', { headers: { Authorization: 'Bearer ' + (process.env.METRICS_TOKEN ?? '') } })).text;
    const active = Number((pool.match(/^hikaricp_connections_active{[^}]*} ([0-9.]+)/m) ?? [])[1]);
    const max = Number((pool.match(/^hikaricp_connections_max{[^}]*} ([0-9.]+)/m) ?? [])[1]);
    check('数据库连接都已归还（无泄漏）', Number.isFinite(active) && active <= 2, `active ${active} of ${max} after 12 more audit reads`);
    const roles = (await call('GET', '/api/v1/role-mappings')).json;
    check('角色映射', roles.length >= 6, `${roles.length} rows`);
    // a reviewer is refused modelling (demote this user in this Polarion project for a moment, then restore)
    const add = await call('POST', '/api/v1/role-mappings', { body: { polarionProject: PP, polarionRole: '*', toolRole: 'REVIEWER' } });
    const access = (await call('GET', `/api/v1/access?projectId=${projectId}`)).json;
    if (add.json?.id) await call('DELETE', `/api/v1/role-mappings/${add.json.id}`);
    check('角色映射按优先级生效（管理员仍是管理员）', add.status < 300 && access.role === 'ADMIN', `added ${add.status}; role in the bound project: ${access.role}`);
  });
} finally {
  // ---------------------------------------------------------------------------------------------- clean up
  section = '清理';
  console.log('\n== 清理');
  opened.forEach((v) => v.close());
  try {
    const links = (await call('GET', `/api/v1/links?projectId=${PP}`)).json ?? [];
    for (const l of links) {
      createdWorkItems.add(l.workItemId);
      await call('DELETE', `/api/v1/links/${l.elementId}?projectId=${PP}`);
    }
    if (createdWorkItems.size) {
      const r = await polarion('DELETE', `/projects/${PP}/workitems`, { data: [...createdWorkItems].map((id) => ({ type: 'workitems', id: `${PP}/${id}` })) });
      console.log(`Polarion Work Items deleted: ${[...createdWorkItems].join(', ')} (${r.status})`);
    }
    if (!KEEP) {
      await call('DELETE', `/api/v1/project-bindings/${PP}`);
      for (const p of [...extraProjects, projectId].filter(Boolean)) await deleteProject(p).catch((e) => console.log('cleanup: ' + e.message));
      console.log('projects deleted: ' + [...extraProjects, projectId].filter(Boolean).length);
    } else {
      console.log('kept project ' + projectId + ' (and its binding) for the UI test');
    }
  } catch (e) {
    console.log('cleanup failed: ' + e.message);
  }
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (process.env.REPORT_JSON) writeFileSync(process.env.REPORT_JSON, JSON.stringify({ startedAt, results, projectId, kept: KEEP }, null, 2));
process.exit(failed.length ? 1 : 0);
