import { cpSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';

// Preserve the exact currently deployed executable, rather than rebuilding an older source revision.
const previous=resolve('build/deployments/20261116T134907Z');
const old=JSON.parse(readFileSync(resolve(previous,'manifest.json'),'utf8').replace(/^\uFEFF/,''));
const rollback=resolve(previous,'candidate.jar');
const hash=path=>createHash('sha256').update(readFileSync(path)).digest('hex');
if(hash(rollback)!==old.candidateSha256) throw new Error('Previous executable no longer matches its manifest');
const prepared=spawnSync(process.execPath,['tools/prepare-local-deployment.mjs'],{windowsHide:true,encoding:'utf8'});
if(prepared.status!==0) throw new Error('Could not prepare protected deployment and database backup');
const manifest=JSON.parse(prepared.stdout);
cpSync(rollback,resolve(manifest.directory,'rollback.jar'));
cpSync(resolve(previous,'runtime-private.json'),resolve(manifest.directory,'runtime-private.json'));
manifest.rollbackSha256=hash(resolve(manifest.directory,'rollback.jar'));
manifest.previousDeployment=previous;
manifest.modelStoreReadEnabled=true;
manifest.pendingMigrations=['sysarch-019-model-storage'];
manifest.note='Candidate adds detached Polarion reads; writes disabled. Exact prior executable and database snapshot preserved. Source includes uncommitted workspace changes.';
writeFileSync(resolve(manifest.directory,'manifest.json'),JSON.stringify(manifest,null,2));
console.log(JSON.stringify({directory:manifest.directory,candidateSha256:manifest.candidateSha256,rollbackSha256:manifest.rollbackSha256,
  databaseBackupSha256:manifest.databaseBackupSha256,modelStoreReadEnabled:true,phase:'PREPARED'}));
