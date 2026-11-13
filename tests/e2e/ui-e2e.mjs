// UI end-to-end test in headless Chrome over the DevTools protocol (no extension, no dependencies), against a running
// instance in production configuration and a project kept by full-e2e.mjs --keep. Signs in by putting the session cookie
// of an API login into the browser (no token is typed into a page). Collects console errors and CSP violations per page.
// Usage: POLARION_TOKEN=... node tests/e2e/ui-e2e.mjs <chrome.exe> <profile dir> <project id> [screenshot dir]
import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';

const [chrome, profile, projectId, shots] = process.argv.slice(2);
const SHELL = process.env.SHELL_URL ?? 'http://localhost:3000';
const API = process.env.BASE ?? 'http://localhost:8080';
const TOKEN = process.env.POLARION_TOKEN;
const PP = process.env.POLARION_PROJECT ?? 'sysarch';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
function check(id, ok, detail = '') {
  results.push({ id, ok: !!ok, detail: String(detail).slice(0, 300) });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${String(detail).slice(0, 200)}`);
}

// ------------------------------------------------------------------------------------------------ browser
const proc = spawn(chrome, ['--headless=new', '--remote-debugging-port=9334', `--user-data-dir=${profile}`, '--no-first-run', '--disable-gpu', '--window-size=1600,1000', 'about:blank'], { stdio: 'ignore' });
let version;
for (let i = 0; i < 60 && !version; i++) {
  await sleep(250);
  version = await fetch('http://127.0.0.1:9334/json/version').then((r) => r.json()).catch(() => null);
}
const target = await fetch('http://127.0.0.1:9334/json/new?about:blank', { method: 'PUT' }).then((r) => r.json());
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r));
let seq = 0;
const pending = new Map();
const events = [];
ws.addEventListener('message', (m) => {
  const msg = JSON.parse(m.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  } else if (msg.method) events.push(msg);
});
const send = (method, params = {}) => new Promise((resolve) => {
  const id = ++seq;
  pending.set(id, (msg) => resolve(msg.result ?? { error: msg.error }));
  ws.send(JSON.stringify({ id, method, params }));
});
await send('Page.enable');
await send('Runtime.enable');
await send('Log.enable');
await send('Network.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1000, deviceScaleFactor: 1, mobile: false });
await send('Page.addScriptToEvaluateOnNewDocument', { source: "addEventListener('securitypolicyviolation', e => console.error('CSP-VIOLATION ' + e.violatedDirective + ' ' + e.blockedURI))" });

async function js(expression) {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error('page: ' + (r.exceptionDetails.exception?.description ?? r.exceptionDetails.text));
  return r.result?.value;
}
async function waitFor(expression, ms = 20000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const v = await js(expression).catch(() => null);
    if (v) return v;
    await sleep(300);
  }
  return null;
}
const pageProblems = [];
async function open(url, label) {
  events.length = 0;
  await send('Page.navigate', { url });
  await sleep(1500);
  return label;
}
function collect(label) {
  const msgs = [];
  for (const e of events) {
    if (e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error') msgs.push(e.params.args.map((a) => a.value ?? a.description ?? '').join(' ').slice(0, 200));
    if (e.method === 'Runtime.exceptionThrown') msgs.push('EXCEPTION ' + (e.params.exceptionDetails.exception?.description ?? e.params.exceptionDetails.text).slice(0, 200));
  }
  const csp = msgs.filter((m) => m.includes('CSP-VIOLATION') && !m.includes('script-src eval')); // Sirius' caught global-this shim (security review §5)
  const errors = msgs.filter((m) => !m.includes('CSP-VIOLATION'));
  pageProblems.push({ page: label, csp, errors });
  return { csp, errors };
}
async function shot(name) {
  if (!shots) return;
  const r = await send('Page.captureScreenshot', { format: 'png' });
  if (r.data) writeFileSync(`${shots}/${name}.png`, Buffer.from(r.data, 'base64'));
}
async function mouse(type, x, y, buttons = 1, button = 'left') {
  await send('Input.dispatchMouseEvent', { type, x, y, button, buttons: type === 'mouseReleased' ? 0 : buttons, clickCount: 1 });
}
const leaf = (text) => `[...document.querySelectorAll('*')].find(e => e.children.length === 0 && e.textContent.trim() === ${JSON.stringify(text)})`;

// ------------------------------------------------------------------------------------------------ data
const gql = async (query) => (await (await fetch(API + '/api/graphql', { method: 'POST', headers: { Origin: SHELL, Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }, body: JSON.stringify({ query }) })).json()).data;
const project = (await gql(`{viewer{project(projectId:"${projectId}"){name currentEditingContext{representations(first:50){edges{node{id label}}}}}}}`)).viewer.project;
const rep = (part) => project.currentEditingContext.representations.edges.map((e) => e.node).find((n) => n.label.includes(part))?.id;
const api = async (path, init = {}) => (await fetch(API + path, { ...init, headers: { Origin: SHELL, Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json', ...(init.headers ?? {}) } })).json();

try {
  // ---------------------------------------------------------------------------------------------- signed out
  await open(`${SHELL}/projects`, 'projects (signed out)');
  const gate = await waitFor("document.querySelector('.signin-gate') && document.body.innerText", 15000);
  check('未登录：先显示登录页，不加载 Sirius 应用', !!gate && gate.includes('请先登录') && !(await js("document.body.innerText.includes('" + project.name + "')")), gate?.replace(/\s+/g, ' ').slice(0, 90));
  await shot('01-signed-out');

  // ---------------------------------------------------------------------------------------------- sign in
  const login = await fetch(API + '/api/v1/auth/login', { method: 'POST', headers: { Origin: SHELL, 'Content-Type': 'application/json' }, body: JSON.stringify({ token: TOKEN }) });
  const value = (login.headers.get('set-cookie') ?? '').split(';')[0].split('=').slice(1).join('=');
  const set = await send('Network.setCookie', { name: 'sysarch_session', value, domain: 'localhost', path: '/', httpOnly: true });
  check('会话 Cookie 写入浏览器', set.success !== false, 'sysarch_session (HttpOnly)');

  await open(`${SHELL}/projects`, 'projects');
  const listed = await waitFor(`document.body.innerText.includes(${JSON.stringify(project.name)}) && document.body.innerText`, 20000);
  check('项目浏览器（Sirius 应用 + SysArch 导航链接）', !!listed && listed.includes('SysArch 表格与报告'), `${project.name} listed`);
  let p = collect('projects');
  check('项目浏览器：无控制台错误、无 CSP 违规', p.errors.length === 0 && p.csp.length === 0, JSON.stringify(p).slice(0, 200));
  await shot('02-projects');

  // ---------------------------------------------------------------------------------------------- diagram editor
  const bdd = rep('BDD');
  await open(`${SHELL}/projects/${projectId}/edit/${bdd}`, 'BDD');
  const nodes = await waitFor("document.querySelectorAll('.react-flow__node').length >= 6 && document.querySelectorAll('.react-flow__node').length", 25000);
  const edges = await js("document.querySelectorAll('.react-flow__edge').length");
  check('编辑器打开 BDD：节点和边画出来', nodes >= 6 && edges >= 3, `${nodes} nodes, ${edges} edges`);
  const tree = await js("document.body.innerText.includes('域控演示') && document.body.innerText.includes('e2e-behaviour')");
  check('资源管理器显示两份文档', !!tree, tree ? '域控演示, e2e-behaviour.json' : 'documents not listed');
  await shot('03-bdd');

  // drag a node, reload, position kept
  const before = await js(`(() => { const n = document.querySelectorAll('.react-flow__node')[1]; const r = n.getBoundingClientRect(); return { id: n.getAttribute('data-id'), x: r.x, y: r.y, w: r.width, h: r.height, t: n.style.transform }; })()`);
  const sx = before.x + before.w / 2;
  const sy = before.y + 8;
  await mouse('mouseMoved', sx, sy, 0);
  await mouse('mousePressed', sx, sy);
  for (let i = 1; i <= 10; i++) await mouse('mouseMoved', sx + i * 12, sy + i * 6);
  await mouse('mouseReleased', sx + 120, sy + 60);
  await sleep(2500);
  await open(`${SHELL}/projects/${projectId}/edit/${bdd}`, 'BDD (reload)');
  await waitFor("document.querySelectorAll('.react-flow__node').length >= 6", 25000);
  await sleep(1500);
  const after = await js(`(() => { const n = document.querySelector('.react-flow__node[data-id="${before.id}"]'); return n ? n.style.transform : null; })()`);
  check('拖动节点后重新打开，位置保留', after && after !== before.t, `${before.t} → ${after}`);

  // palette on the LA background (Sirius 2025.8 opens it with a right click): 建模工具 → 新建逻辑组件
  const la = rep('逻辑架构');
  await open(`${SHELL}/projects/${projectId}/edit/${la}`, 'LA');
  const n0 = await waitFor("document.querySelectorAll('.react-flow__node').length", 25000);
  await sleep(1000);
  const pane = await js("(() => { const r = document.querySelector('.react-flow__pane').getBoundingClientRect(); return { x: r.x + r.width - 120, y: r.y + r.height - 120 }; })()");
  await mouse('mouseMoved', pane.x, pane.y, 0);
  await mouse('mousePressed', pane.x, pane.y, 2, 'right');
  await mouse('mouseReleased', pane.x, pane.y, 2, 'right');
  const section = await waitFor(leaf('建模工具') + ' != null', 8000);
  if (section) await js(leaf('建模工具') + ".closest('[role=menuitem], li, button, div').click()");
  const palette = section && (await waitFor(leaf('新建逻辑组件') + ' != null', 5000));
  if (palette) await js(leaf('新建逻辑组件') + ".closest('[role=menuitem], li, button, div').click()");
  const n1 = await waitFor(`document.querySelectorAll('.react-flow__node').length > ${n0} && document.querySelectorAll('.react-flow__node').length`, 10000);
  check('画板（右键）：在 LA 背景上新建逻辑组件', !!palette && !!n1, `palette ${section ? 'shown' : 'not shown'}, tool ${palette ? 'found' : 'not found'}, nodes ${n0} → ${n1}`);
  await shot('04-la-palette');
  p = collect('editor');
  check('编辑器：无控制台错误、无 CSP 违规', p.errors.length === 0 && p.csp.length === 0, JSON.stringify(p).slice(0, 200));

  // state machine and the read-only sequence picture
  await open(`${SHELL}/projects/${projectId}/edit/${rep('状态机')}`, 'state machine');
  const sm = await waitFor("document.querySelectorAll('.react-flow__edge').length >= 3 && document.body.textContent", 25000);
  await sleep(1500);
  const smEdges = await js("document.querySelectorAll('.react-flow__edge').length");
  check('状态机图：迁移标签', !!sm && sm.includes('开门[速度 = 0]') && sm.includes('关门/解锁'), sm ? 'labels shown' : 'not drawn');
  await shot('05-state-machine');
  // the initial state is Sirius' ellipse node: it and the transition leaving it must be drawn too (4 transitions)
  check('状态机图：初始状态（椭圆节点）及其迁移', smEdges === 4, `${smEdges} of 4 transitions drawn`);
  await open(`${SHELL}/projects/${projectId}/edit/${rep('用例')}`, 'use case');
  await waitFor("document.querySelectorAll('.react-flow__node').length >= 2", 25000);
  await sleep(2000);
  const ucEdges = await js("document.querySelectorAll('.react-flow__edge').length");
  check('用例图：椭圆用例与参与者的关联', ucEdges === 1, `${ucEdges} of 1 association drawn`);
  await shot('05b-use-case');

  // ---------------------------------------------------------------------------------------------- the SysArch shell
  const shell = (view, extra = '') => `${SHELL}/sysarch?project=${projectId}${view ? '&view=' + view : ''}${extra}`;
  await open(shell('', '&table=allocation-matrix'), 'allocation matrix');
  const cells = await waitFor("document.querySelectorAll('.matrix .cell').length", 20000);
  const target = await js(`(() => { const c = [...document.querySelectorAll('.matrix .cell:not(.marked)')].find(c => /系统功能|功能/.test(c.title) && /决策规划单元|感知融合单元/.test(c.title.split('→')[1])); return c ? c.title : null; })()`);
  if (target) await js(`[...document.querySelectorAll('.matrix .cell')].find(c => c.title === ${JSON.stringify(target)}).click()`);
  const marked = target && (await waitFor(`[...document.querySelectorAll('.matrix .cell.marked')].some(c => c.title === ${JSON.stringify(target)})`, 10000));
  check('分配矩阵：点击空格建立分配', !!marked, `${cells} cells; ${target ?? 'no SA → LA cell'}`);
  if (marked) {
    await js(`[...document.querySelectorAll('.matrix .cell')].find(c => c.title === ${JSON.stringify(target)}).click()`);
    const menu = await waitFor("document.querySelector('.matrix .menu') !== null", 2000);
    if (menu) await js("[...document.querySelectorAll('.matrix .menu button')].find(b => b.textContent.includes('取消')).click()");
    const unmarked = await waitFor(`![...document.querySelectorAll('.matrix .cell.marked')].some(c => c.title === ${JSON.stringify(target)})`, 10000);
    check('分配矩阵：再点一次取消分配', !!unmarked, menu ? 'via the menu' : 'direct');
  }
  const skip = await js(`(() => { const c = [...document.querySelectorAll('.matrix .cell:not(.marked)')].find(c => /功能/.test(c.title) && /SoC|MCU/.test(c.title.split('→')[1])); if (c) c.click(); return c ? c.title : null; })()`);
  const refused = skip && (await waitFor("document.body.innerText.includes('跳过 LA') && document.body.innerText.match(/跨层跳过[^\\n]*/)[0]", 8000));
  check('分配矩阵：跨层被拒并显示原因', !!refused, refused ?? `no SA → PA cell (${skip})`);
  await shot('06-allocation-matrix');

  await open(shell('', '&table=requirement-trace'), 'trace matrix');
  const traceCells = await waitFor("document.querySelectorAll('.matrix .cell').length", 20000);
  const tmarked = await js("document.querySelectorAll('.matrix .cell.marked').length");
  check('需求追踪矩阵', traceCells > 0 && tmarked > 0, `${traceCells} cells, ${tmarked} marked`);

  const pages = [
    ['baselines', '基线', "document.body.innerText.includes('e2e 基线')"],
    ['branches', '分支', "document.body.innerText.includes('e2e-分支')"],
    ['reports', '报告', "document.body.innerText.includes('SRS') || document.body.innerText.includes('srs')"],
    ['sequence', '时序图', "document.querySelector('img[src*=\"sequence.svg\"], svg text') !== null || document.body.innerText.includes('开门交互')"],
    ['comments', '评论', "document.body.innerText.includes('e2e 评论')"],
    ['locks', '编辑锁', "document.body.innerText.includes('正在编辑的人')"],
    ['import', '导入', "document.body.innerText.includes('SysML') && document.body.innerText.includes('XMI')"],
    ['admin', '角色与审计', `document.body.innerText.includes('Polarion 项目与模型') && document.body.innerText.includes(${JSON.stringify(PP)})`],
  ];
  for (const [view, name, expr] of pages) {
    await open(shell(view), name);
    const okPage = await waitFor(expr, 15000);
    const probs = collect(name);
    check(`外壳页面「${name}」`, !!okPage && probs.errors.length === 0 && probs.csp.length === 0, `${okPage ? 'content shown' : 'content missing'}; errors ${probs.errors.length}, csp ${probs.csp.length} ${probs.errors[0] ?? ''}`);
    await shot('07-' + view);
  }

  // report download links from the reports page
  const link = await js("(() => { const a = [...document.querySelectorAll('a')].find(a => /files\\/pdf/.test(a.href)); return a ? a.href : null; })()");
  if (link) {
    const r = await fetch(link.replace(SHELL, API), { headers: { Authorization: 'Bearer ' + TOKEN } });
    check('报告页面：PDF 下载链接可用', r.status === 200 && r.headers.get('content-type').includes('pdf'), `${r.status} ${r.headers.get('content-type')}`);
  }

  // ---------------------------------------------------------------------------------------------- embed page
  await open(`${SHELL}/?embed=1&polarionProject=${PP}`, 'embed');
  const embed = await waitFor(`document.body.innerText.includes(${JSON.stringify(project.name)}) && document.body.innerText`, 15000);
  check('嵌入页（Polarion 项目 → 模型）', !!embed, embed?.replace(/\s+/g, ' ').slice(0, 120));
  await shot('08-embed');

  // ---------------------------------------------------------------------------------------------- sign out
  await open(shell('admin'), 'admin again');
  await waitFor("document.body.innerText.includes('退出')", 10000);
  await js("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '退出')?.click()");
  await sleep(1500);
  await open(`${SHELL}/projects`, 'after sign-out');
  const gateAgain = await waitFor("document.querySelector('.signin-gate') !== null", 10000);
  check('退出登录后回到登录页', !!gateAgain, gateAgain ? 'gate shown' : 'still signed in');
} catch (e) {
  check('run', false, e.stack ?? String(e));
} finally {
  ws.close();
  proc.kill();
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (process.env.REPORT_JSON) writeFileSync(process.env.REPORT_JSON, JSON.stringify({ results, pageProblems }, null, 2));
process.exit(failed.length ? 1 : 0);
