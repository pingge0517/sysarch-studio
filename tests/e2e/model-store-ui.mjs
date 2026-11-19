import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import assert from 'node:assert/strict';
import { readLocalEnv } from '../../tools/local-env.mjs';

const chrome=process.argv[2]; assert(chrome && existsSync(chrome),'Pass a Chrome executable');
const history=process.argv[3]==='--live-requirement';
const matrixMode=process.argv[3]==='--live-matrix';
const canvasMode=process.argv[3]==='--live-canvas';
const revocationMode=process.argv[3]==='--live-revocation';
const canvasFixture=process.argv[3]==='--canvas-fixture';
const live=history || matrixMode || canvasMode || revocationMode || process.argv[3]==='--live';
const liveRoot=process.argv[4];
if(liveRoot) assert(/^[A-Za-z0-9_-]{1,128}$/.test(liveRoot),'Invalid fixture root');
if(history) assert(/^[0-9]{1,20}$/.test(process.argv[5]??''),'Pass the real pinned revision');
const dist=resolve('frontend/dist'),calls=[];
const summary={title:'System',location:{server:'https://untrusted.invalid',root:{project:'alpha',workItem:'A-1',revision:null},modelUuid:'fixture-model'}};
const snapshot={subject:'alice',version:1,commitId:'fixture-commit',model:{modelUuid:'fixture-model',documents:[],nodes:[
  {uuid:'node-1',type:'PartDefinition',attributes:{name:['System'],description:['<img src=x onerror=alert(1)>']},references:{ownedParts:['node-2']}},
  {uuid:'node-2',type:'Part',attributes:{name:['Component']},references:{}},
]}};
const canvas=canvasFixture?JSON.parse(readFileSync('backend/build/polarion-read-view-fixture.json','utf8')):null;
if(canvasFixture) snapshot.views=[{id:'bdd',label:'块定义图',kind:'DIAGRAM',rootType:'PartDefinition'}];
const server=createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost');
  if(url.pathname.startsWith('/api/')) {
    calls.push({path:url.pathname,method:req.method,hasCookie:!!req.headers.cookie,hasToken:!!req.headers.authorization});
    res.setHeader('Content-Type','application/json');
    if(req.headers.authorization!=='Bearer fixture-token') {res.writeHead(403);res.end(JSON.stringify({detail:'DENIED'}));return;}
    res.end(JSON.stringify(url.pathname.includes('/views/')?{subject:'alice',version:1,commitId:'fixture-commit',representation:canvas}:url.pathname.endsWith('/snapshot')?snapshot:{models:url.searchParams.get('page')==='2'?[]:[summary],page:Number(url.searchParams.get('page')),pageSize:25,mayHaveMore:url.searchParams.get('page')!=='2'}));return;
  }
  const path=resolve(dist,'.'+url.pathname);
  const file=path.startsWith(dist+sep) && existsSync(path) && extname(path)?path:resolve(dist,'index.html');
  res.setHeader('Content-Type',({'.html':'text/html','.js':'text/javascript','.css':'text/css'})[extname(file)]??'application/octet-stream');res.end(readFileSync(file));
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
mkdirSync('build',{recursive:true});const profile=mkdtempSync(resolve('build/model-store-ui-'));
const browser=spawn(chrome,['--headless=new','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--no-first-run','--disable-gpu','about:blank'],{windowsHide:true,stdio:'ignore'});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let ws;const checks=[];
try {
  for(let n=0;n<100 && !existsSync(resolve(profile,'DevToolsActivePort'));n++) await sleep(100);
  const port=readFileSync(resolve(profile,'DevToolsActivePort'),'utf8').split('\n')[0].trim();
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();
  ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,reject)=>{ws.addEventListener('open',r,{once:true});ws.addEventListener('error',reject,{once:true});});
  let sequence=0;const pending=new Map();
  ws.addEventListener('message',({data})=>{const result=JSON.parse(data);pending.get(result.id)?.(result);pending.delete(result.id);});
  const send=(method,params={})=>new Promise((r,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout'));},10000);pending.set(id,result=>{clearTimeout(timer);r(result);});ws.send(JSON.stringify({id,method,params}));});
  const js=async expression=>{const response=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!response.result?.exceptionDetails,'Browser evaluation failed');return response.result?.result?.value;};
  const wait=async(expression,attempts=80)=>{for(let n=0;n<attempts;n++){if(await js(expression))return;await sleep(100);}throw Error('UI condition timed out');};
  const input=async(selector,value)=>js(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
  const click=async text=>js(`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(text)}).click()`);
  const pass=name=>{checks.push({name,passed:true});console.log('PASS '+name);};
  await send('Page.navigate',{url:live?'http://localhost:3000/polarion-models':`http://127.0.0.1:${server.address().port}/polarion-models`});
  await wait("!!document.querySelector('input[type=password]')");assert.equal(calls.length,0);pass('standalone page needs no global-directory login');
  if(live) {
    const a=readLocalEnv('build/private/polarion-test-a.env'),b=readLocalEnv('build/private/polarion-test-b.env');
    for(const config of [a,b]) assert.equal(config.POLARION_BASE_URL.replace(/\/+$/,''),'http://localhost/polarion');
    await input('form input:not([type=password])','sysarch_probe_a');await input('input[type=password]',a.POLARION_TOKEN);
    await js("document.querySelector('form').requestSubmit()");await wait("document.body.innerText.includes('第 1 页')");pass('real account A can browse its project through the deployed backend');
    if(liveRoot) {
      const root=JSON.stringify(liveRoot);
      await wait(`[...document.querySelectorAll('li')].some(e=>e.querySelector('small')?.textContent===${root})`);
      await js(`[...document.querySelectorAll('li')].find(e=>e.querySelector('small')?.textContent===${root}).querySelector('button').click()`);
      if(revocationMode) {
        await wait("document.body.innerText.includes('读取用户：sysarch_test_a')");
        await js("(()=>{const e=document.querySelector('select[aria-label=模型元素]');e.value=[...e.options].find(o=>o.textContent.includes('(Requirement)')).value;e.dispatchEvent(new Event('change',{bubbles:true}));})()");
        await wait("document.body.innerText.includes('Historical requirement V1')");pass('real dependency content is initially readable');
        writeFileSync(`build/polarion-store/revocation-ready-${liveRoot}.json`,JSON.stringify({ready:true}));
        await wait("!!document.querySelector('[role=alert]') && !document.body.innerText.includes('Historical requirement V1') && !document.body.innerText.includes('读取用户：sysarch_test_a')",350);
        pass('real dependency deletion clears the already displayed model automatically');
      } else if(canvasMode) {
        await wait("document.body.innerText.includes('读取用户：sysarch_test_a')");
        const labels=await js("[...document.querySelectorAll('button')].filter(e=>e.textContent.endsWith('（只读）')&&!e.textContent.includes('矩阵')).map(e=>e.textContent)");
        assert.equal(labels.length,5);
        for(const label of labels) {
          await click(label);
          await wait("document.querySelectorAll('[data-testid=native-read-canvas] .react-flow__node').length>0");
          assert.equal(await js("document.querySelectorAll('[data-testid=native-read-canvas] .react-flow__node.draggable').length"),0);
          pass('real native read-only canvas: '+label);
        }
        await send('Network.enable');
        await send('Network.emulateNetworkConditions',{offline:true,latency:0,downloadThroughput:0,uploadThroughput:0});
        await wait("!!document.querySelector('[role=alert]') && !document.querySelector('[data-testid=native-read-canvas]') && !document.body.innerText.includes('读取用户：sysarch_test_a')");
        pass('real browser disconnect immediately clears the model and native canvas');
        await send('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});
      } else if(matrixMode) {
        await wait("document.body.innerText.includes('读取用户：sysarch_test_a')");
        await js("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('分配矩阵') && e.textContent.endsWith('（只读）')).click()");
        await wait("document.querySelectorAll('.matrix .cell.marked').length===2");
        assert.equal(await js("[...document.querySelectorAll('.matrix .cell')].every(e=>e.disabled)"),true);pass('real allocation matrix shows two UUID-based links with disabled edit cells');
        await js("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('需求追溯矩阵') && e.textContent.endsWith('（只读）')).click()");
        await wait("document.querySelectorAll('.matrix .cell.marked').length===1 && document.querySelector('.matrix').innerText.includes('Trace requirement')");pass('real requirement trace matrix is reconstructed from the authorized model');
      } else if(history) {
        await wait("document.body.innerText.includes('读取用户：sysarch_test_a') && document.body.innerText.includes('元素：1')");
        await js("(()=>{const e=document.querySelector('select[aria-label=模型元素]');e.value=[...e.options].find(o=>o.textContent.includes('(Requirement)')).value;e.dispatchEvent(new Event('change',{bubbles:true}));})()");
        await wait("document.body.innerText.includes('Historical requirement V1')");
        assert.equal(await js("document.body.innerText.includes('Current requirement V2')"),false);pass('real browser displays pinned historical requirement V1 after HEAD changed');
        await wait(`document.body.innerText.includes(${JSON.stringify('固定修订：'+process.argv[5])}) && document.body.innerText.includes('需求来源：sysarch_probe_a')`);pass('real browser shows requirement provenance and the actual selected revision');
      } else {
      await wait("document.body.innerText.includes('读取用户：sysarch_test_a') && document.body.innerText.includes('元素：2')");
      await js("(()=>{const e=document.querySelector('select[aria-label=模型元素]');e.value=[...e.options].find(o=>o.textContent.includes('(PartDefinition)')).value;e.dispatchEvent(new Event('change',{bubbles:true}));})()");
      await wait("document.body.innerText.includes('Real Polarion System') && document.body.innerText.includes('PD-M2')");pass('real model opens through browser, backend, identity bridge and Polarion attachments');
      await click('Component');await wait("[...document.querySelectorAll('h3')].some(e=>e.textContent==='Part')");pass('real browser follows the reconstructed containment reference');
      }
    }
    await input('input[type=password]',b.POLARION_TOKEN);assert.equal(await js("document.body.innerText.includes('第 1 页')"),false);
    await js("document.querySelector('form').requestSubmit()");await wait("document.querySelector('[role=alert]')?.textContent.includes('DENIED')");pass('real account B is refused project A and old results are cleared');
    await input('form input:not([type=password])','sysarch_probe_b');await js("document.querySelector('form').requestSubmit()");await wait("document.body.innerText.includes('第 1 页')");pass('real account B can browse its own project');
    await click('清除');assert.equal(await js("document.querySelector('input[type=password]').value"),'');assert.equal(await js('localStorage.length+sessionStorage.length'),0);pass('real browser retains no credentials in web storage');
    writeFileSync(revocationMode?'build/model-store-revocation-live-ui-report.json':canvasMode?'build/model-store-canvas-live-ui-report.json':matrixMode?'build/model-store-matrix-ui-report.json':history?'build/model-store-history-ui-report.json':'build/model-store-live-ui-report.json',JSON.stringify({checks},null,2));
  } else {
  await input('form input:not([type=password])','alpha');await input('input[type=password]','fixture-token');
  await js("document.querySelector('form').requestSubmit()");await wait("[...document.querySelectorAll('button')].some(e=>e.textContent==='System')");
  await click('System');await wait("document.body.innerText.includes('读取用户：alice')");
  if(canvasFixture) {
    await click('块定义图（只读）');
    await wait("document.querySelectorAll('[data-testid=native-read-canvas] .react-flow__node').length===3");
    assert.equal(await js("document.querySelectorAll('[data-testid=native-read-canvas] .react-flow__edge').length"),1);pass('published Sirius renderer displays actual native nodes and edges');
    assert.equal(await js("document.querySelectorAll('[data-testid=native-read-canvas] .react-flow__node.draggable').length"),0);pass('native readOnly mode prevents node dragging');
    assert(calls.every(call=>call.method==='GET'));pass('native canvas creates no server mutation requests');
    await click('清除');await wait("!document.querySelector('[data-testid=native-read-canvas]')");pass('clearing credentials unmounts the native canvas');
    writeFileSync('build/model-store-canvas-fixture-report.json',JSON.stringify({checks,calls},null,2));
  } else {
  await js("const e=document.querySelector('select[aria-label=模型元素]');e.value='node-1';e.dispatchEvent(new Event('change',{bubbles:true}));");
  await wait("document.body.innerText.includes('<img src=x onerror=alert(1)>')");assert.equal(await js("document.querySelectorAll('td img').length"),0);pass('element text is escaped and never rendered as HTML');
  await click('Component');await wait("document.body.innerText.includes('UUID：node-2')");pass('reference navigation selects the linked model element');
  await click('下一页');await wait("document.body.innerText.includes('第 2 页')");assert.equal(await js("document.body.innerText.includes('读取用户：alice')"),false);pass('pagination clears the previous model');
  await input('input[type=password]','invalid-fixture');assert.equal(await js("document.body.innerText.includes('System')"),false);
  await js("document.querySelector('form').requestSubmit()");await wait("document.querySelector('[role=alert]')?.textContent.includes('DENIED')");pass('credential change and denial retain no earlier model data');
  await click('清除');assert.equal(await js("document.querySelector('input[type=password]').value"),'');
  assert.equal(await js('localStorage.length+sessionStorage.length'),0);
  assert(calls.every(call=>call.method==='GET' && call.hasToken && !call.hasCookie));pass('reads use only PAT GETs and clearing removes credentials');
  writeFileSync('build/model-store-ui-report.json',JSON.stringify({checks,calls},null,2));
  }
  }
} finally {ws?.close();browser.kill();server.closeAllConnections();server.close();}
