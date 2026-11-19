// M2 diff-only preflight. Unreadable metadata is UNKNOWN, never assumed absent. No writes or raw bodies logged.
import { readLocalEnv } from './local-env.mjs';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

function kindOf(field) {
  const kind=field?.type?.kind;
  if(typeof kind==='string') return kind;
  if(Array.isArray(kind?.enum) && kind.enum.length===1 && typeof kind.enum[0]==='string') return kind.enum[0];
  return null;
}
async function metadataJson(response) {
  const chunks=[];let length=0;
  for await(const chunk of response.body) {
    length+=chunk.length;if(length>2*1024*1024) throw new Error('metadata-size-limit');chunks.push(Buffer.from(chunk));
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
export async function inspectSchema({schema,project,token,baseUrl='http://localhost/polarion',fetchImpl=fetch}) {
  if(!project?.match(/^[A-Za-z0-9_-]{1,128}$/) || baseUrl!=='http://localhost/polarion' || !token) throw new Error('Invalid preflight scope');
  const types=[];
  for(const [targetType,fields] of Object.entries(schema.types)) {
    const expected={...schema.common,...fields};let status=null,attributes=null;
    try {
      const response=await fetchImpl(baseUrl+'/rest/v1/projects/'+project+'/actions/getFieldsMetadata?'
        +new URLSearchParams({resourceType:'workitems',targetType}),{method:'GET',redirect:'error',signal:AbortSignal.timeout(15000),
        headers:{Authorization:'Bearer '+token,Accept:'application/json'}});
      status=response.status;
      if(response.ok && /json/i.test(response.headers.get('content-type')??'')) {
        const body=await metadataJson(response);
        if(body.data?.attributes && typeof body.data.attributes==='object' && !Array.isArray(body.data.attributes)) attributes=body.data.attributes;
      } else await response.body?.cancel();
    } catch { /* Keep credentials and response bodies out of error reports. */ }
    const readable=attributes!==null;
    const present=readable?Object.keys(expected).filter(field=>Object.hasOwn(attributes,field)):[];
    const conflicts=present.filter(field=>kindOf(attributes[field])!==null && kindOf(attributes[field])!==expected[field])
      .map(field=>({field,expected:expected[field],actual:kindOf(attributes[field])}));
    types.push({targetType,status,metadataReadable:readable,presentFields:present,
      missingFields:readable?Object.keys(expected).filter(field=>!present.includes(field)):[],unknownFields:readable?[]:Object.keys(expected),
      typeConflicts:conflicts,unknownFieldKinds:present.filter(field=>kindOf(attributes[field])===null)});
  }
  return {project,types};
}
export function schemaFieldsReady(projects) {
  return projects.length>0 && projects.every(project=>project.types.length>0 && project.types.every(type=>
    type.metadataReadable && type.missingFields.length===0 && type.unknownFields.length===0
    && type.typeConflicts.length===0 && type.unknownFieldKinds.length===0));
}
async function main() {
  const schema=JSON.parse(readFileSync('backend/src/main/resources/storage/polarion-store-schema.json','utf8'));
  const args=process.argv.slice(2);
  if(args.length!==0 && !(args.length===2 && args[0]==='--env-file')) throw new Error('Use optional --env-file <local-reader-file>');
  const projects=[];
  for(const account of ['a','b']) {
    const config=readLocalEnv(args.length?args[1]:`build/private/polarion-test-${account}.env`);
    if(config.POLARION_BASE_URL && config.POLARION_BASE_URL.replace(/\/+$/,'')!=='http://localhost/polarion') throw new Error('Unexpected target');
    projects.push(await inspectSchema({schema,project:'sysarch_probe_'+account,token:config.POLARION_TOKEN}));
  }
  const readable=projects.every(project=>project.types.every(type=>type.metadataReadable));
  const ready=schemaFieldsReady(projects);
  const report={schemaVersion:schema.schemaVersion,mode:'diff-only',checkedAt:new Date().toISOString(),projects,allMetadataReadable:readable,allRequiredFieldsCompatible:ready,
    note:'Metadata does not prove type existence, workflow, enumeration or uniform ACL acceptance. No configuration was changed.'};
  mkdirSync('build/polarion-store',{recursive:true});
  writeFileSync('build/polarion-store/schema-preflight.json',JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({report:'build/polarion-store/schema-preflight.json',allMetadataReadable:readable,allRequiredFieldsCompatible:ready,
    projects:projects.map(project=>({project:project.project,types:project.types.map(type=>({type:type.targetType,status:type.status,
      missing:type.missingFields.length,unknown:type.unknownFields.length,conflicts:type.typeConflicts.length}))}))},null,2));
  if(!ready) process.exitCode=1;
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(()=>{console.error('Schema preflight failed; no configuration changed or credentials logged.');process.exitCode=1;});
}
