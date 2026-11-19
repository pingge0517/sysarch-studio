import { readLocalEnv } from './local-env.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';

// Fixed dedicated target. Reports contain status and check names only, never credentials or response bodies.
const url='http://localhost/polarion/sysarch-model/identity';
const checks=[];
for(const account of ['a','b']) {
  try {
    const config=readLocalEnv(`build/private/polarion-test-${account}.env`);
    if(config.POLARION_BASE_URL?.replace(/\/+$/,'')!=='http://localhost/polarion') throw new Error('Unexpected target');
    const response=await fetch(url,{headers:{Authorization:'Bearer '+config.POLARION_TOKEN},redirect:'error',signal:AbortSignal.timeout(5000)});
    const body=response.status===200?await response.json():{};
    if(response.status!==200) await response.body?.cancel();
    checks.push({check:'identity-'+account,passed:response.status===200 && body.protocol==='sysarch-model-read/1'
      && body.authentication==='sdk-access-token' && body.actor==='sysarch_test_'+account,status:response.status});
  } catch { checks.push({check:'identity-'+account,passed:false}); }
}
for(const [check,options,status] of [
  ['anonymous',{},401],['invalid-token',{headers:{Authorization:'Bearer invalid-fixture'}},401],
  ['post-forbidden',{method:'POST'},405],['cookie-not-identity',{headers:{Cookie:'sysarch_user=sysarch_test_a'}},401],
]) {
  try {
    const response=await fetch(url,{...options,redirect:'manual',signal:AbortSignal.timeout(5000)});
    await response.body?.cancel(); checks.push({check,passed:response.status===status,status:response.status});
  } catch { checks.push({check,passed:false}); }
}
mkdirSync('build/polarion-store',{recursive:true});
writeFileSync('build/polarion-store/production-identity-acceptance.json',JSON.stringify({checkedAt:new Date().toISOString(),checks},null,2)+'\n');
console.log(JSON.stringify({passed:checks.filter(check=>check.passed).length,total:checks.length,checks},null,2));
if(checks.some(check=>!check.passed)) process.exitCode=1;
