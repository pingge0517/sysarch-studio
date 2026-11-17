// Real local deployment smoke test. Uses the existing administrator PAT, creates only uniquely named test data,
// and removes only the exact model, binding and Work Item created by this run. This is NOT a two-account test.
import assert from 'node:assert/strict';
import { createHmac, randomBytes, randomUUID } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { readLocalEnv } from '../../tools/local-env.mjs';

const config=readLocalEnv();
const dir=resolve(process.argv[2] ?? '');
assert(dir.startsWith(resolve('build/deployments')+sep),'Pass the restricted deployment directory');
const manifestPath=resolve(dir,'manifest.json');
const manifest=JSON.parse(readFileSync(manifestPath,'utf8').replace(/^\uFEFF/,''));
const secrets=JSON.parse(readFileSync(resolve(dir,'runtime-private.json'),'utf8'));
const base='http://localhost:8080';
const origin='http://localhost:3000';
const pp='sysarch';
const token=config.POLARION_TOKEN;
const auth={Authorization:`Bearer ${token}`,Origin:origin};
const results=[];
const stamp=new Date().toISOString().replace(/\D/g,'').slice(0,14);
const elementId='DEPLOY-'+stamp;
let project;
let workItem;
let ownsBinding=false;
let socket;
const start=new Date().toISOString();
const save=()=>writeFileSync(resolve(dir,'live-smoke.json'),JSON.stringify({startedAt:start,finishedAt:new Date().toISOString(),
  kind:'real-local-admin-single-account',results,createdProject:project,createdWorkItem:workItem,ownsBinding},null,2));
function pass(name){results.push({name,passed:true});console.log('PASS '+name);save();}
async function request(method,path,{body,raw,headers=auth,type}={}) {
  const response=await fetch(base+path,{method,headers:{...headers,...(body!==undefined?{'Content-Type':'application/json'}:{}),...(type?{'Content-Type':type}:{})},
    body:body!==undefined?JSON.stringify(body):raw,redirect:'manual',signal:AbortSignal.timeout(30000)});
  const text=await response.text();
  let json;try{json=JSON.parse(text);}catch{}
  return {response,status:response.status,json,text};
}
async function gql(query,variables) {
  const r=await request('POST','/api/graphql',{body:{query,variables}});
  assert.equal(r.status,200,'GraphQL HTTP status');
  assert(!r.json?.errors,'GraphQL execution returned errors');
  return r.json.data;
}
async function poll(action,predicate,seconds=25) {
  for(let i=0;i<seconds*2;i++){const value=await action();if(predicate(value))return value;await new Promise(r=>setTimeout(r,500));}
  throw new Error('Timed out waiting for deployed operation');
}
async function polarion(method,path,body) {
  const response=await fetch((config.POLARION_BASE_URL||'http://localhost/polarion')+'/rest/v1'+path,{
    method,headers:{Authorization:`Bearer ${token}`,Accept:'application/json',...(body?{'Content-Type':'application/json'}:{})},
    body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
  let json;try{json=await response.json();}catch{}
  return {status:response.status,json};
}
if(process.argv.includes('--verify-cleanup')) {
  const previous=JSON.parse(readFileSync(resolve(dir,'live-smoke.json'),'utf8'));
  const projects=(await gql('{viewer{projects(first:100){edges{node{id}}}}}')).viewer.projects.edges.map(e=>e.node.id);
  assert(!projects.includes(previous.createdProject),'Temporary model still exists');
  const bindings=(await request('GET','/api/v1/project-bindings')).json;
  assert(!bindings.some(b=>b.siriusProject===previous.createdProject),'Temporary binding still exists');
  if(previous.createdWorkItem) assert.equal((await polarion('GET',`/projects/${pp}/workitems/${previous.createdWorkItem}`)).status,404,'Temporary Work Item still exists');
  assert.equal((await request('GET','/actuator/health',{headers:{}})).json.status,'UP');
  console.log('PASS independent cleanup verification: model absent, binding absent, Work Item 404, backend UP');
} else {
try {
  assert.equal((await request('GET','/actuator/health',{headers:{}})).json?.status,'UP');
  assert.equal((await request('GET','/api/v1/auth/me',{headers:{}})).json?.anonymousRead,false);
  assert.equal((await request('POST','/api/graphql',{headers:{},body:{query:'{viewer{projects(first:1){edges{node{id}}}}}'}})).status,401);
  pass('production mode and anonymous-read rejection');
  const login=await request('POST','/api/v1/auth/login',{headers:{Origin:origin},body:{token}});
  assert.equal(login.status,200);assert.equal(login.json.user,'admin');
  const cookie=login.response.headers.get('set-cookie')?.split(';')[0];
  assert(cookie?.startsWith('sysarch_session='));
  assert(login.response.headers.get('set-cookie').includes('HttpOnly'));
  const session=await request('GET','/api/v1/auth/me',{headers:{Cookie:cookie,Origin:origin}});
  assert.equal(session.json.user,login.json.user);
  pass('real Polarion PAT login and HttpOnly session');
  const originalProjects=(await gql('{viewer{projects(first:100){edges{node{id name}}}}}')).viewer.projects.edges.map(e=>e.node);
  assert(originalProjects.length>0);
  pass('existing model projects remain readable');
  if(config.SYSARCH_POLARION_SSO_SECRET) {
    const now=Math.floor(Date.now()/1000);
    const payload=Buffer.from(JSON.stringify({sub:login.json.user,iat:now,exp:now+60,nonce:randomBytes(16).toString('hex'),aud:'sysarch'})).toString('base64url');
    const assertion='v1.'+payload+'.'+createHmac('sha256',config.SYSARCH_POLARION_SSO_SECRET).update('v1.'+payload).digest('base64url');
    const form=new URLSearchParams({assertion,return:'/sysarch'}).toString();
    const sso=await request('POST','/api/v1/auth/polarion-sso',{headers:{Origin:'http://localhost'},raw:form,type:'application/x-www-form-urlencoded'});
    assert.equal(sso.status,303);
    assert.equal((await request('POST','/api/v1/auth/polarion-sso',{headers:{Origin:'http://localhost'},raw:form,type:'application/x-www-form-urlencoded'})).status,401);
    pass('SSO assertion for the verified real account; replay rejected');
  }
  const bindings=(await request('GET','/api/v1/project-bindings')).json;
  assert(!bindings.some(b=>b.polarionProject===pp),'The test project is already bound; refusing to alter its existing binding');
  const imported=await request('POST','/api/v1/imports/sysmlv2?'+new URLSearchParams({projectName:'Deployment smoke '+stamp,fileName:'deployment-probe.sysml',defaultLayer:'LOGICAL'}),{
    raw:`package DeployProbe { part def <'${elementId}'> 'Deployment probe'; }`,type:'text/plain;charset=utf-8'});
  assert.equal(imported.status,202);
  const job=await poll(()=>request('GET','/api/v1/imports/'+imported.json.id),r=>['DONE','FAILED'].includes(r.json?.status));
  assert.equal(job.json.status,'DONE','Import must finish');
  project=job.json.projectId;save();
  pass('isolated SysML model import on the deployed backend');
  const mapped=await request('PUT','/api/v1/project-bindings/'+pp,{body:{siriusProject:project}});
  assert(mapped.status<300);ownsBinding=true;save();
  const workspace=await request('GET',`/api/v1/projects/${project}/sync-workspace`);
  assert.equal(workspace.status,200);assert.equal(workspace.json.polarionProject,pp);
  assert(workspace.json.elements.some(e=>e.id===elementId));
  pass('new sync workspace route resolves the real Polarion binding');
  const dry=await request('POST','/api/v1/sync/push',{headers:{...auth,'Idempotency-Key':randomUUID()},body:{projectId:pp,elementIds:[elementId],dryRun:true}});
  assert.equal(dry.status,200);assert.equal(dry.json.errors.length,0);assert.equal(dry.json.pushed,1);
  assert(!(await request('GET','/api/v1/links?projectId='+pp)).json.some(l=>l.elementId===elementId));
  pass('real Polarion preflight creates no binding or Work Item');
  const pushed=await request('POST','/api/v1/sync/push',{headers:{...auth,'Idempotency-Key':randomUUID()},body:{projectId:pp,elementIds:[elementId],dryRun:false}});
  const owned=(await request('GET','/api/v1/links?projectId='+pp)).json.find(l=>l.elementId===elementId);
  if(owned){workItem=owned.workItemId;save();}
  assert.equal(pushed.status,200);assert.equal(pushed.json.errors.length,0);assert(workItem);
  const wi=await polarion('GET',`/projects/${pp}/workitems/${workItem}?fields[workitems]=id,title,sysarchElementId`);
  assert.equal(wi.status,200);assert.equal(wi.json.data.attributes.sysarchElementId,elementId);
  pass('actual push creates the uniquely identified test Work Item');
  assert.equal((await request('POST','/api/v1/sync/pull',{body:{projectId:pp}})).status,200);
  const report=await request('GET','/api/v1/consistency/report?projectId='+pp);
  assert.equal(report.status,200);assert(Array.isArray(report.json.issues));
  pass('real pull and online consistency report');
  const exported=await request('POST','/api/v1/export/sysmlv2',{body:{projectId:project,scope:'PROJECT'}});
  assert.equal(exported.status,200);assert(exported.text.includes(elementId));
  const baseline=await request('POST','/api/v1/baselines',{body:{projectId:project,label:'Deployment smoke baseline'}});
  assert.equal(baseline.status,201);
  assert.equal((await request('GET',`/api/v1/baselines/${baseline.json.id}/diff?against=CURRENT`)).status,200);
  pass('project-scoped export, baseline creation and diff');
  const metric=await request('GET','/actuator/prometheus',{headers:{Authorization:'Bearer '+(config.SYSARCH_METRICS_TOKEN||secrets.metricsToken)}});
  assert.equal(metric.status,200);assert(metric.text.includes('sysarch_'));
  assert.equal((await request('GET','/actuator/prometheus',{headers:{}})).status,401);
  pass('protected deployment metrics');
  socket=new WebSocket('ws://localhost:8080/subscriptions',{protocols:['graphql-ws'],headers:{Origin:origin,Cookie:cookie}});
  const messages=[];
  socket.addEventListener('message',event=>messages.push(JSON.parse(event.data)));
  await new Promise((ok,fail)=>{const t=setTimeout(()=>fail(new Error('WebSocket open timeout')),8000);socket.addEventListener('open',()=>{clearTimeout(t);ok();});socket.addEventListener('error',()=>{clearTimeout(t);fail(new Error('WebSocket connection failed'));});});
  socket.send(JSON.stringify({type:'connection_init',payload:{}}));
  await poll(async()=>messages,items=>items.some(m=>m.type==='connection_ack'),8);
  socket.send(JSON.stringify({id:'probe',type:'start',payload:{query:'subscription($input:ProjectEventInput!){projectEvent(input:$input){__typename}}',variables:{input:{id:randomUUID(),projectId:project}}}}));
  await new Promise(r=>setTimeout(r,600));
  await gql('mutation($input:RenameProjectInput!){renameProject(input:$input){__typename}}',{input:{id:randomUUID(),projectId:project,newName:'Deployment smoke verified '+stamp}});
  await poll(async()=>messages,items=>items.some(m=>m.type==='data'&&m.payload?.data?.projectEvent),8);
  assert(!messages.some(m=>m.type==='error'||m.payload?.errors));
  pass('native WebSocket project subscription with the real login session');
  const front=await fetch('http://localhost:3000/');assert.equal(front.status,200);
  const html=await front.text();assert(html.includes('/assets/index-'));
  pass('frontend production assets reachable');
  const existing=(await gql('{viewer{projects(first:100){edges{node{id}}}}}')).viewer.projects.edges.map(e=>e.node.id);
  assert(originalProjects.every(p=>existing.includes(p.id)));
  pass('all pre-existing models preserved');
} catch(error) {
  results.push({name:'live deployment smoke',passed:false,error:error.message});
  console.error('FAIL '+error.message);process.exitCode=1;
} finally {
  socket?.close();
  try {
    if(workItem) {
      const wi=await polarion('GET',`/projects/${pp}/workitems/${workItem}?fields[workitems]=sysarchElementId`);
      assert.equal(wi.json?.data?.attributes?.sysarchElementId,elementId,'Refusing to delete a Work Item not owned by this test');
      const removed=await polarion('DELETE',`/projects/${pp}/workitems`,{data:[{type:'workitems',id:`${pp}/${workItem}`}]});
      assert([200,204].includes(removed.status));
      assert.equal((await request('DELETE','/api/v1/links/'+encodeURIComponent(elementId))).status,204);
    }
    if(ownsBinding) {
      const current=(await request('GET','/api/v1/project-bindings')).json.find(b=>b.polarionProject===pp);
      assert.equal(current?.siriusProject,project,'Binding changed owner; refusing to remove it');
      assert.equal((await request('DELETE','/api/v1/project-bindings/'+pp)).status,204);
      ownsBinding=false;
    }
    if(project) await gql('mutation($input:DeleteProjectInput!){deleteProject(input:$input){__typename}}',{input:{id:randomUUID(),projectId:project}});
    pass('owned test Work Item, binding and model cleaned up');
  } catch(error) {results.push({name:'test data cleanup',passed:false,error:error.message});console.error('FAIL cleanup: '+error.message);process.exitCode=1;}
  save();
  manifest.phase=results.every(r=>r.passed)?'VERIFIED':'SMOKE_FAILED';
  manifest.verifiedAt=new Date().toISOString();
  manifest.smokeReport=resolve(dir,'live-smoke.json');
  manifest.twoAccountVerification='pending-second-credential';
  writeFileSync(manifestPath,JSON.stringify(manifest,null,2));
  console.log(`${results.filter(r=>r.passed).length}/${results.length} live deployment checks passed; single real account.`);
}
}
