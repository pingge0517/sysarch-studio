import { cpSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { createHash } from 'node:crypto';

const directory=resolve(process.argv[2]??'');
if(!directory.startsWith(resolve('build/deployments')+sep)) throw new Error('Invalid deployment directory');
const manifestPath=resolve(directory,'manifest.json');
const manifest=JSON.parse(readFileSync(manifestPath,'utf8').replace(/^\uFEFF/,''));
if(manifest.modelStoreReadEnabled!==true) throw new Error('Not a model read deployment');
const source=resolve('backend/build/libs/backend-0.1.0-SNAPSHOT.jar');
const digest=createHash('sha256').update(readFileSync(source)).digest('hex');
const filename='candidate-'+digest.slice(0,12)+'.jar';
cpSync(source,resolve(directory,filename));
manifest.candidateHistory??=[];
manifest.candidateHistory.push({file:manifest.candidateFile??'candidate.jar',sha256:manifest.candidateSha256});
manifest.candidateFile=filename; manifest.candidateSha256=digest; manifest.phase='PREPARED_UPDATE';
writeFileSync(manifestPath,JSON.stringify(manifest,null,2));
console.log(JSON.stringify({directory,candidateFile:filename,candidateSha256:digest,phase:manifest.phase}));
