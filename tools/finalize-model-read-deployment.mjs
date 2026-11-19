import { readLocalEnv } from './local-env.mjs';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const directory=resolve(process.argv[2]??'');
if(!directory.startsWith(resolve('build/deployments')+sep)) throw new Error('Invalid deployment directory');
const file=resolve(directory,'manifest.json');
const manifest=JSON.parse(readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const hash=path=>createHash('sha256').update(readFileSync(path)).digest('hex');
const checks=[]; const check=(name,passed)=>checks.push({name,passed});
check('candidate-hash',hash(resolve(directory,manifest.candidateFile??'candidate.jar'))===manifest.candidateSha256);
check('rollback-hash',hash(resolve(directory,'rollback.jar'))===manifest.rollbackSha256);
check('database-backup-hash',hash(manifest.databaseBackup)===manifest.databaseBackupSha256);
const config=readLocalEnv();
const sql=spawnSync('C:/PostgreSQL/16/bin/psql.exe',['-X','-At','-h','127.0.0.1','-p','5432','-U',config.POSTGRES_USER,'-d','sysarch','-c',
  "select count(*) from databasechangelog where id='sysarch-019-model-storage';"],
  {windowsHide:true,encoding:'utf8',env:{...process.env,PGPASSWORD:config.POSTGRES_PASSWORD}});
check('registration-migration',sql.status===0 && sql.stdout.trim()==='1');
const health=await fetch('http://localhost:8080/actuator/health',{signal:AbortSignal.timeout(10000)});
check('backend-health',health.status===200 && (await health.json()).status==='UP');
const accounts={};
for(const account of ['a','b']) {
  accounts[account]=readLocalEnv(`build/private/polarion-test-${account}.env`);
  const response=await fetch(`http://localhost:8080/api/v1/model-store/projects/sysarch_probe_${account}/models`,{
    headers:{Authorization:'Bearer '+accounts[account].POLARION_TOKEN},redirect:'error',signal:AbortSignal.timeout(15000)});
  const body=await response.json();
  check('own-project-'+account,response.status===200 && Array.isArray(body.models) && response.headers.get('cache-control')==='no-store');
}
for(const [name,headers,expected] of [['anonymous',{},401],['cross-project',{Authorization:'Bearer '+accounts.b.POLARION_TOKEN},403]]) {
  const response=await fetch('http://localhost:8080/api/v1/model-store/projects/sysarch_probe_a/models',{headers,redirect:'manual',signal:AbortSignal.timeout(15000)});
  check(name,response.status===expected);await response.body?.cancel();
}
writeFileSync(resolve(directory,'model-read-deployment-verification.json'),JSON.stringify({checkedAt:new Date().toISOString(),checks},null,2));
if(checks.some(item=>!item.passed)) { console.log(JSON.stringify({verified:false,checks}));process.exitCode=1; }
else {
  manifest.phase='VERIFIED'; manifest.verifiedAt=new Date().toISOString();
  manifest.appliedMigrations=['sysarch-019-model-storage'];manifest.databaseSchemaChanged=true;
  manifest.remoteModelWritesEnabled=false;
  writeFileSync(file,JSON.stringify(manifest,null,2));
  console.log(JSON.stringify({verified:true,backendPid:manifest.backendPid,checks}));
}
