import { readLocalEnv } from './local-env.mjs';
const config = readLocalEnv();
const auth = { Authorization: `Bearer ${config.POLARION_TOKEN}` };
for (const path of ['/actuator/health', '/api/v1/auth/me', '/api/v1/project-bindings']) {
  try {
    const response = await fetch('http://localhost:8080' + path, { headers: auth, signal: AbortSignal.timeout(10000) });
    const body = await response.json();
    if (path.endsWith('/me')) console.log(JSON.stringify({ path, status: response.status, user: body.user, mode: body.mode, globalRoles: body.globalRoles, projects: Object.keys(body.projectRoles ?? {}) }));
    else console.log(JSON.stringify({ path, status: response.status, body }));
  } catch (error) { console.log(JSON.stringify({ path, error: error.message })); }
}
const base = config.POLARION_BASE_URL || 'http://localhost/polarion';
for (const path of ['/rest/v1/projects?page[size]=100', '/rest/v1/openapi.json']) {
  try {
    const response = await fetch(base + path, { headers: auth, signal: AbortSignal.timeout(15000) });
    const body = await response.json();
    console.log(JSON.stringify({ polarionPath: path, status: response.status,
      ...(path.includes('openapi') ? { credentialPaths: Object.keys(body.paths ?? {}).filter((p) => /users|tokens|roles/.test(p)) }
        : { projects: (body.data ?? []).map((p) => ({ id: p.id, name: p.attributes?.name })) }) }));
  } catch (error) { console.log(JSON.stringify({ polarionPath: path, error: error.message })); }
}
