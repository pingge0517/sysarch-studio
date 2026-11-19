import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectSchema, schemaFieldsReady } from '../../../tools/inspect-polarion-store-schema.mjs';
const schema={common:{sysarchManaged:'boolean'},types:{sysarchModel:{sysarchModelUuid:'string'}}};
test('readable metadata yields exact missing fields and kind conflicts',async()=>{
  const calls=[];
  const result=await inspectSchema({schema,project:'sysarch_probe_a',token:'fixture-token',fetchImpl:async(url,options)=>{
    calls.push({url,options});return Response.json({data:{attributes:{sysarchManaged:{type:{kind:'string'}}}}});
  }});
  assert.equal(calls.length,1);assert.equal(calls[0].options.method,'GET');assert.equal(calls[0].options.redirect,'error');
  assert.deepEqual(result.types[0].missingFields,['sysarchModelUuid']);
  assert.deepEqual(result.types[0].typeConflicts,[{field:'sysarchManaged',expected:'boolean',actual:'string'}]);
  assert.equal(JSON.stringify(result).includes('fixture-token'),false);
});
test('403 is not misreported as missing configuration',async()=>{
  const result=await inspectSchema({schema,project:'sysarch_probe_b',token:'fixture-token',fetchImpl:async()=>new Response('private',{status:403})});
  assert.equal(result.types[0].metadataReadable,false);assert.deepEqual(result.types[0].missingFields,[]);
  assert.equal(result.types[0].unknownFields.length,2);assert.equal(JSON.stringify(result).includes('private'),false);
});
test('invalid shape and HTML remain unknown',async()=>{
  for(const response of [Response.json({data:[]}),new Response('<html>login</html>',{headers:{'Content-Type':'text/html'}})]) {
    const result=await inspectSchema({schema,project:'sysarch_probe_a',token:'fixture-token',fetchImpl:async()=>response});
    assert.equal(result.types[0].metadataReadable,false);assert.deepEqual(result.types[0].missingFields,[]);
  }
});
test('enum-shaped primitive kind is recognized',async()=>{
  const result=await inspectSchema({schema,project:'sysarch_probe_a',token:'fixture-token',fetchImpl:async()=>Response.json({data:{attributes:{
    sysarchManaged:{type:{kind:{enum:['boolean']}}},sysarchModelUuid:{type:{kind:'string'}}}}})});
  assert.deepEqual(result.types[0].missingFields,[]);assert.deepEqual(result.types[0].typeConflicts,[]);
});
test('network errors and invalid scope do not leak credentials or send writes',async()=>{
  const result=await inspectSchema({schema,project:'sysarch_probe_a',token:'fixture-token',fetchImpl:()=>{throw new Error('fixture-token');}});
  assert.equal(result.types[0].status,null);assert.equal(result.types[0].metadataReadable,false);
  assert.equal(JSON.stringify(result).includes('fixture-token'),false);
  await assert.rejects(inspectSchema({schema,project:'../elsewhere',token:'fixture-token',fetchImpl:()=>assert.fail('must not request')}));
});
test('readable metadata alone cannot pass the configuration gate',()=>{
  const good={metadataReadable:true,missingFields:[],unknownFields:[],typeConflicts:[],unknownFieldKinds:[]};
  assert.equal(schemaFieldsReady([{types:[good]}]),true);
  assert.equal(schemaFieldsReady([]),false);
  assert.equal(schemaFieldsReady([{types:[]}]),false);
  for(const key of ['missingFields','unknownFields','typeConflicts','unknownFieldKinds']) {
    assert.equal(schemaFieldsReady([{types:[{...good,[key]:['incompatible']}]}]),false);
  }
  assert.equal(schemaFieldsReady([{types:[{...good,metadataReadable:false}]}]),false);
});
