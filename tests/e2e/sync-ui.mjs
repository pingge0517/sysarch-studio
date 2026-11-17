// Browser regression for the Polarion sync page. All API responses are local fixtures: no Polarion data is changed.
// Build frontend first, then: node tests/e2e/sync-ui.mjs "C:/Program Files/Google/Chrome/Application/chrome.exe"
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync } from 'node:fs';
import { resolve, extname } from 'node:path';
import assert from 'node:assert/strict';

const chrome = process.argv[2];
assert(chrome && existsSync(chrome), 'Pass the path of a Chrome executable');
const dist = resolve('frontend/dist');
assert(existsSync(resolve(dist, 'index.html')), 'Run npm run build in frontend first');
const calls = [];
let bindings = [];
let repaired = false;
let conflict = false;
const result = { pushed: 1, pulled: 0, skipped: 0, errors: [], conflicts: [] };
const me = { user: 'alice', name: 'Alice', mode: 'polarion', source: 'polarion-sso', globalRoles: [], projectRoles: {} };
const elements = ['PD-1', 'PD-2'].map((id) => ({ id, name: id === 'PD-1' ? 'Controller' : 'Sensor', type: 'PartDefinition', layer: 'LOGICAL', workItemType: 'architecture_element', errors: [] }));
const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    let body = '';
    for await (const chunk of req) body += chunk;
    const data = body ? JSON.parse(body) : null;
    if (url.pathname.startsWith('/api/')) {
      // Record only test fixture credentials' presence, not header values.
      calls.push({ path: url.pathname, method: req.method, data, hasCookie: !!req.headers.cookie, hasToken: !!req.headers.authorization });
      const send = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)); };
      if (url.pathname === '/api/v1/auth/me') return send({ ...me, user: req.headers.authorization === 'Bearer wrong-fixture' ? 'bob' : 'alice' });
      if (url.pathname === '/api/graphql') return send({ data: { viewer: { projects: { edges: [{ node: { id: 'model', name: 'Demo' } }] } } } });
      if (url.pathname === '/api/v1/access') return send({ user: 'alice', role: 'ARCHITECT', roleLabel: '架构师', permissions: ['SYNC'] });
      if (url.pathname === '/api/v1/branches' || url.pathname === '/api/v1/tables') return send([]);
      if (url.pathname.endsWith('/sync-workspace')) {
        if (url.pathname.includes('/branch/')) return send({ detail: '分支不能直接同步到 Polarion' }, 409);
        return send({ siriusProjectId: 'model', polarionProject: 'sysarch', elements });
      }
      if (url.pathname === '/api/v1/sync/status') return send({ lastPushAt: null, lastPullAt: null, lastCheckAt: null, schedule: { enabled: false, paused: false, lastError: null } });
      if (url.pathname === '/api/v1/sync/unbound') return send([{ workItemId: 'SYS-9', title: 'Existing sensor', workItemType: 'architecture_element' }]);
      if (url.pathname === '/api/v1/links') {
        if (req.method === 'POST') { bindings.push({ elementId: data.elementId, workItemId: data.workItemId, state: 'ACTIVE' }); return send({}, 201); }
        return send(bindings);
      }
      if (url.pathname.startsWith('/api/v1/links/') && req.method === 'DELETE') {
        bindings = bindings.filter((b) => b.elementId !== decodeURIComponent(url.pathname.split('/').pop()));
        return send({});
      }
      if (url.pathname === '/api/v1/sync/push') {
        if (conflict) return send({ ...result, pushed: 0, conflicts: [{ elementId: 'PD-1', fields: ['name'] }] }, 409);
        return send(result);
      }
      if (url.pathname === '/api/v1/sync/pull') return send({ ...result, pushed: 0, pulled: 1 });
      if (url.pathname === '/api/v1/consistency/report') return send({ projectId: 'sysarch', generatedAt: new Date().toISOString(), issues: repaired ? [] : [{ id: 'R5:PD-1', rule: 'R5', severity: 'MINOR', elementId: 'PD-1', message: 'Controller 未绑定', repairable: true }] });
      if (url.pathname === '/api/v1/consistency/repair') { repaired = true; return send({ actions: [{ issueId: 'R5:PD-1', status: 'DONE', message: '已处理' }] }); }
      return send({ detail: `No fixture for ${req.method} ${url.pathname}` }, 404);
    }
    const file = resolve(dist, '.' + url.pathname);
    const asset = file.startsWith(dist + '\\') || file.startsWith(dist + '/') ? file : '';
    const target = asset && existsSync(asset) && extname(asset) ? asset : resolve(dist, 'index.html');
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
    res.writeHead(200, { 'Content-Type': types[extname(target)] ?? 'application/octet-stream' });
    res.end(readFileSync(target));
  } catch (error) { res.writeHead(500); res.end(error.message); }
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;
const out = resolve('build');
mkdirSync(out, { recursive: true });
const profile = mkdtempSync(resolve(out, 'sync-ui-browser-'));
const proc = spawn(chrome, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run', '--disable-gpu', 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let browserError;
proc.on('error', (e) => { browserError = e; });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let ws;
let count = 0;
try {
  for (let n = 0; n < 100 && !existsSync(resolve(profile, 'DevToolsActivePort')); n++) {
    if (browserError) throw browserError;
    await sleep(100);
  }
  const port = readFileSync(resolve(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0].trim();
  console.log('Headless Chrome started');
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((r, reject) => {
    const timeout = setTimeout(() => reject(new Error('CDP connection timed out')), 10000);
    ws.addEventListener('open', () => { clearTimeout(timeout); r(); });
    ws.addEventListener('error', () => { clearTimeout(timeout); reject(new Error('CDP connection failed')); });
  });
  const pending = new Map();
  let seq = 0;
  const errors = [];
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) { pending.get(message.id)?.(message); pending.delete(message.id); }
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
  });
  const send = (method, params = {}) => new Promise((r, reject) => {
    const id = ++seq;
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timed out: ${method}`)); }, 10000);
    pending.set(id, (message) => { clearTimeout(timeout); r(message); });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const js = async (expression) => {
    const message = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (message.result?.exceptionDetails) throw new Error(message.result.exceptionDetails.text);
    return message.result?.result?.value;
  };
  const wait = async (expression) => {
    for (let n = 0; n < 80; n++) { if (await js(expression)) return; await sleep(100); }
    throw new Error('Timed out waiting for ' + expression);
  };
  const pass = (label) => { count++; console.log('PASS ' + label); };
  const click = async (label) => {
    const code = `[...document.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)})`;
    await wait(`!!(${code}) && !(${code}).disabled`);
    await js(`(${code}).click()`);
  };
  const input = async (label, value) => {
    await js(`(() => { const e = document.querySelector('[aria-label=${JSON.stringify(label)}]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(e, ${JSON.stringify(value)}); e.dispatchEvent(new Event('input', {bubbles:true})); })()`);
  };
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');
  await send('Network.setCookie', { name: 'sysarch_session', value: 'fixture-only', url: origin, path: '/' });
  await send('Page.navigate', { url: origin + '/?embed=1&project=model&view=sync' });
  await wait("document.body.innerText.includes('目标项目：sysarch')");
  pass('SSO session opens the model-bound sync page');
  await input('Polarion 个人访问令牌', 'wrong-fixture');
  await click('验证当前账号令牌');
  await wait("document.body.innerText.includes('令牌属于 bob')");
  assert.equal(calls.filter((c) => c.path.endsWith('/push')).length, 0);
  pass('another accounts PAT is rejected before any sync call');
  await input('Polarion 个人访问令牌', 'valid-fixture');
  await click('验证当前账号令牌');
  await wait("document.body.innerText.includes('已验证账号：alice')");
  assert(calls.some((c) => c.path.endsWith('/auth/me') && c.hasToken && !c.hasCookie));
  pass('PAT identity is checked without the SSO cookie');
  await js(`document.querySelector('[aria-label="推送 PD-1"]').click()`);
  await click('预检推送');
  await wait("document.body.innerText.includes('确认推送到 sysarch')");
  assert.equal(calls.filter((c) => c.path.endsWith('/push') && !c.data.dryRun).length, 0);
  pass('preflight does not perform an actual push');
  await js(`document.querySelector('[aria-label="推送 PD-1"]').click()`);
  await wait("!document.querySelector('[aria-label=\"确认推送\"]')");
  pass('changing the selection invalidates the previous confirmation');
  await js(`document.querySelector('[aria-label="推送 PD-1"]').click()`);
  await click('预检推送');
  await click('确认推送到 sysarch');
  await wait("document.body.innerText.includes('推送结果') && !document.querySelector('[aria-busy=true]')");
  assert.equal(calls.filter((c) => c.path.endsWith('/push') && !c.data.dryRun).length, 1);
  pass('only an explicit confirmation performs the selected write');
  conflict = true;
  await click('预检推送');
  await wait("document.body.innerText.includes('冲突 PD-1')");
  assert.equal(await js("!!document.querySelector('[aria-label=\"确认推送\"]')"), false);
  conflict = false;
  pass('field conflicts remain visible and prevent push confirmation');
  await click('拉取…');
  assert.equal(calls.filter((c) => c.path.endsWith('/pull')).length, 0);
  await click('确认执行');
  await wait("document.body.innerText.includes('拉取结果') && !document.querySelector('[aria-busy=true]')");
  assert.equal(calls.filter((c) => c.path.endsWith('/pull')).length, 1);
  pass('pull requires its own confirmation');
  await js(`(() => { const e = document.querySelector('[aria-label="绑定模型元素"]'); e.value='PD-2'; e.dispatchEvent(new Event('change', {bubbles:true})); })()`);
  await input('绑定 Work Item ID', 'SYS-9');
  await click('建立绑定');
  await wait("document.body.innerText.includes('已建立绑定') && !document.querySelector('[aria-busy=true]')");
  assert.equal(bindings[0].workItemId, 'SYS-9');
  await click('解除绑定…');
  assert.equal(bindings.length, 1);
  await click('确认执行');
  await wait("document.body.innerText.includes('已解除 PD-2') && !document.querySelector('[aria-busy=true]')");
  assert.equal(bindings.length, 0);
  pass('binding and confirmed unbinding do not delete a Work Item');
  await click('在线一致性检查');
  await click('修复此项…');
  assert.equal(repaired, false);
  await click('确认执行');
  await wait("document.body.innerText.includes('0 个问题') && !document.querySelector('[aria-busy=true]')");
  const repairCall = calls.find((c) => c.path.endsWith('/repair'));
  assert.deepEqual(repairCall.data.issueIds, ['R5:PD-1']);
  pass('consistency repair confirms and sends one issue only');
  assert.equal(await js('localStorage.length + sessionStorage.length'), 0);
  await click('清除令牌');
  await wait("!!document.querySelector('[aria-label=\"Polarion 个人访问令牌\"]')");
  assert.equal(await js(`document.querySelector('[aria-label="Polarion 个人访问令牌"]').value`), '');
  pass('credential clearing leaves no token in browser storage or the input');
  await send('Page.navigate', { url: origin + '/?embed=1&project=branch&view=sync' });
  await wait("document.body.innerText.includes('分支不能直接同步')");
  assert.equal(await js("!!document.querySelector('[aria-label=\"Polarion 个人访问令牌\"]')"), false);
  pass('branch sync is refused without reusing the trunk credential');
  assert.deepEqual(errors, []);
  console.log(`${count} browser checks passed; no uncaught browser exceptions; all APIs were fixtures.`);
} finally {
  ws?.close();
  proc.kill();
  server.closeAllConnections();
  await new Promise((r) => server.close(r));
}
