import { readLocalEnv } from './local-env.mjs';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';

const dir=resolve(process.argv[2]??'');
if(!dir.startsWith(resolve('build/deployments')+sep)) throw new Error('Invalid deployment directory');
const config=readLocalEnv();
const file=resolve(dir,'sysarch-verified.dump');
const backup=spawnSync('C:/PostgreSQL/16/bin/pg_dump.exe',['-h','127.0.0.1','-p','5432','-U',config.POSTGRES_USER,'-Fc','-f',file,'sysarch'],{
  env:{...process.env,PGPASSWORD:config.POSTGRES_PASSWORD},windowsHide:true,encoding:'utf8'});
if(backup.status!==0) throw new Error('Post-deployment database checkpoint failed');
const path=resolve(dir,'manifest.json');
const manifest=JSON.parse(readFileSync(path,'utf8').replace(/^\uFEFF/,''));
manifest.verifiedBackup=file;
manifest.verifiedBackupSha256=createHash('sha256').update(readFileSync(file)).digest('hex');
manifest.checkpointAt=new Date().toISOString();
manifest.databaseSchemaChanged=false;
writeFileSync(path,JSON.stringify(manifest,null,2));
console.log(JSON.stringify({phase:manifest.phase,backendPid:manifest.backendPid,checkpointAt:manifest.checkpointAt,backup:file,
  backupSha256:manifest.verifiedBackupSha256,twoAccountVerification:manifest.twoAccountVerification}));
