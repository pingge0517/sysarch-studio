// P4 acceptance against a running backend in dev mode (SYSARCH_AUTH_MODE=dev), as far as it runs without a browser,
// a second Polarion or a cluster. Node >= 22, no dependencies. Usage: node tests/acceptance/p4-acceptance.mjs
//   P4-AC-1  an administrator binds a Polarion project to a model through the API: effective at once (embed target
//            200, no restart); an architect is refused 403 and the refusal is audited; unbinding takes it away again;
//            deleting the model removes its binding
//   P4-AC-5  (part) /actuator/prometheus is not open without the metrics token
//   P4-AC-7  (part) security headers on every response (security review F2)
// A throw-away project is created and deleted; the Polarion project id used is fictional (no Polarion is called).
import { randomUUID } from 'node:crypto';

const BASE = process.env.BASE ?? 'http://localhost:8080';
const ORIGIN = { Origin: 'http://localhost:3000' };
const ADMIN = { ...ORIGIN, 'X-Sysarch-User': 'p4-admin', 'X-Sysarch-Roles': 'global:admin' };
const ARCHITECT = { ...ORIGIN, 'X-Sysarch-User': 'p4-architect', 'X-Sysarch-Roles': '' };
const POLARION = 'p4acc' + Date.now().toString(36);

let failed = 0;
let passed = 0;
function check(name, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}  ${detail ?? ''}`);
  ok ? passed++ : failed++;
}

async function call(method, path, headers, body) {
  const r = await fetch(BASE + path, {
    method,
    headers: { ...headers, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    // not JSON
  }
  return { status: r.status, json, text, headers: r.headers };
}

async function gql(query, variables) {
  const r = await call('POST', '/api/graphql', ADMIN, { query, variables });
  if (r.status !== 200 || r.json?.errors) throw new Error('GraphQL ' + r.status + ' ' + r.text.slice(0, 200));
  return r.json.data;
}

const name = 'p4-acceptance-' + Date.now().toString(36);
let projectId = null;
try {
  const health = await call('GET', '/actuator/health', {});
  check('health', health.status === 200 && health.json?.status === 'UP', `${health.status}`);

  const me = await call('GET', '/api/v1/auth/me', ADMIN);
  if (me.json?.mode !== 'dev') {
    throw new Error('the backend must run with SYSARCH_AUTH_MODE=dev (mode is ' + me.json?.mode + ')');
  }

  const created = (await gql(
    `mutation($i:CreateProjectInput!){createProject(input:$i){__typename ... on CreateProjectSuccessPayload{project{id}} ... on ErrorPayload{messages{body}}}}`,
    { i: { id: randomUUID(), name, natures: [] } },
  )).createProject;
  if (created.__typename !== 'CreateProjectSuccessPayload') throw new Error('createProject: ' + JSON.stringify(created));
  projectId = created.project.id;

  // P4-AC-1
  const before = await call('GET', `/api/v1/embed/target?polarionProject=${POLARION}`, ADMIN);
  check('P4-AC-1 unbound', before.status === 404, `embed target ${before.status}`);

  const refused = await call('PUT', `/api/v1/project-bindings/${POLARION}`, ARCHITECT, { siriusProject: name });
  check('P4-AC-1 non-admin', refused.status === 403, `${refused.status} ${refused.json?.detail ?? refused.json?.message ?? ''}`);
  const audit = await call('GET', '/api/v1/audit?action=ACCESS_DENIED', ADMIN);
  const denial = (audit.json ?? []).find((e) => e.actor === 'p4-architect');
  check('P4-AC-1 audited', !!denial, denial ? `${denial.action} ${denial.detail}` : `audit ${audit.status}`);

  const bound = await call('PUT', `/api/v1/project-bindings/${POLARION}`, ADMIN, { siriusProject: name });
  check('P4-AC-1 bind', bound.status === 200 && bound.json?.siriusProject === projectId, `${bound.status} → ${bound.json?.siriusProject}`);
  const after = await call('GET', `/api/v1/embed/target?polarionProject=${POLARION}`, ADMIN);
  check('P4-AC-1 no restart', after.status === 200 && after.json?.siriusProjectId === projectId, `embed target ${after.status} ${after.json?.projectName ?? ''}`);

  const twice = await call('PUT', `/api/v1/project-bindings/${POLARION}x`, ADMIN, { siriusProject: name });
  check('P4-AC-1 one model, one project', twice.status === 409, `${twice.status}`);

  const unbound = await call('DELETE', `/api/v1/project-bindings/${POLARION}`, ADMIN);
  const gone = await call('GET', `/api/v1/embed/target?polarionProject=${POLARION}`, ADMIN);
  check('P4-AC-1 unbind', unbound.status === 204 && gone.status === 404, `delete ${unbound.status}, embed target ${gone.status}`);

  await call('PUT', `/api/v1/project-bindings/${POLARION}`, ADMIN, { siriusProject: name });
  await gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId } });
  projectId = null;
  let left = true;
  for (let i = 0; i < 20 && left; i++) {
    await new Promise((r) => setTimeout(r, 250)); // the cleanup runs after the deletion's commit, on its own thread
    const list = await call('GET', '/api/v1/project-bindings', ADMIN);
    left = (list.json ?? []).some((b) => b.polarionProject === POLARION);
  }
  check('P4-AC-1 model deleted', !left, left ? 'binding still listed' : 'binding removed with the model');

  // P4-AC-5 (part)
  const metrics = await call('GET', '/actuator/prometheus', {});
  check('P4-AC-5 metrics closed', metrics.status === 404 || metrics.status === 401, `without token ${metrics.status}`);

  // P4-AC-7 (part)
  const headers = (await call('GET', '/api/v1/auth/me', {})).headers;
  const security = ['content-security-policy', 'x-content-type-options', 'referrer-policy'].map((h) => `${h}: ${headers.get(h)}`);
  check('P4-AC-7 headers', security.every((s) => !s.endsWith('null')), security.join('; '));
} catch (e) {
  check('run', false, e.message);
} finally {
  if (projectId) {
    await gql(`mutation($i:DeleteProjectInput!){deleteProject(input:$i){__typename}}`, { i: { id: randomUUID(), projectId } }).catch((e) => console.log('cleanup: ' + e));
  }
}
console.log(`${passed}/${passed + failed} passed`);
process.exit(failed ? 1 : 0);
