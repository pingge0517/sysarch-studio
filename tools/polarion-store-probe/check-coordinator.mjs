// Cross-process database arbitration test, scoped to one synthetic key. No model data and no Polarion DB access.
import { readLocalEnv } from '../local-env.mjs';
import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
const config = readLocalEnv();
if (config.POSTGRES_URL && config.POSTGRES_URL !== 'jdbc:postgresql://localhost:5432/sysarch') throw new Error('Unexpected runtime DB');
const key = 'm0-coordinator-fixture:' + randomUUID();
const command = randomUUID();
const checks = [];
async function sql(statement) {
  return new Promise((resolve, reject) => {
    const child = spawn('C:/PostgreSQL/16/bin/psql.exe', ['-X','-q','-t','-A','-h','localhost','-p','5432','-U',config.POSTGRES_USER,
      '-d','sysarch','-v','ON_ERROR_STOP=1','-c',statement], { windowsHide: true, env: { ...process.env, PGPASSWORD: config.POSTGRES_PASSWORD }, stdio: ['ignore','pipe','pipe'] });
    let output=''; child.stdout.on('data', chunk => { output += chunk; }); child.stderr.resume();
    child.on('error', () => reject(new Error('Coordinator fixture process failed')));
    child.on('close', code => code === 0 ? resolve(output.trim()) : reject(new Error('Coordinator fixture SQL failed')));
  });
}
function check(name, passed) { checks.push({ name, passed }); if (!passed) throw new Error(name); }
const insert = () => `INSERT INTO sysarch_probe_m0.command_gate(model_key,request_id,command_id,actor,state) VALUES `
  + `('${key}','${randomUUID()}','${command}','fixture','ACTIVE') ON CONFLICT (model_key) DO NOTHING RETURNING request_id`;
try {
  const contenders = await Promise.all([sql(insert()),sql(insert()),sql(insert()),sql(insert())]);
  check('four independent PG clients have exactly one winner', contenders.filter(Boolean).length === 1);
  await sql(`UPDATE sysarch_probe_m0.command_gate SET state='UNKNOWN' WHERE model_key='${key}'`);
  check('UNKNOWN state survives a fresh client connection', await sql(`SELECT state FROM sysarch_probe_m0.command_gate WHERE model_key='${key}'`) === 'UNKNOWN');
  check('UNKNOWN does not automatically expire or reopen writes', await sql(insert()) === '');
  await sql(`DELETE FROM sysarch_probe_m0.command_gate WHERE model_key='${key}' AND state='ACTIVE'`);
  check('ordinary release cannot erase an UNKNOWN reservation', await sql(`SELECT COUNT(*) FROM sysarch_probe_m0.command_gate WHERE model_key='${key}'`) === '1');
} finally {
  // This synthetic key cannot correspond to a Polarion model or request.
  await sql(`DELETE FROM sysarch_probe_m0.command_gate WHERE model_key='${key}' AND actor='fixture'`);
  const reservations = await sql('SELECT COUNT(*) FROM sysarch_probe_m0.command_gate');
  const registrations = await sql('SELECT COUNT(*) FROM sysarch_probe_m0.model_registry');
  const report = { checkedAt: new Date().toISOString(), checks, remainingReservations: Number(reservations), remainingModelRegistrations: Number(registrations), polarionDatabaseTouched: false };
  writeFileSync('build/polarion-store/coordinator-verification.json', JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report,null,2));
  if (checks.some(c => !c.passed) || Number(reservations) !== 0 || Number(registrations) !== 0) process.exitCode=1;
}
