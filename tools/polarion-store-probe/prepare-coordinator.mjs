// Prepare only SysArch runtime coordination. Never connects to Polarion's database; no credentials in argv/logs.
import { readLocalEnv } from '../local-env.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const config = readLocalEnv();
if (config.POSTGRES_URL && config.POSTGRES_URL !== 'jdbc:postgresql://localhost:5432/sysarch') throw new Error('Unexpected SysArch database URL');
if (!config.POSTGRES_USER || !config.POSTGRES_PASSWORD) throw new Error('Missing local SysArch database credentials');
const sql = `CREATE SCHEMA IF NOT EXISTS sysarch_probe_m0;
CREATE TABLE IF NOT EXISTS sysarch_probe_m0.command_gate (
  model_key text PRIMARY KEY, request_id varchar(36) NOT NULL UNIQUE,
  command_id varchar(36) NOT NULL, actor text NOT NULL,
  state varchar(16) NOT NULL CHECK (state IN ('ACTIVE','UNKNOWN')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS sysarch_probe_m0.model_registry (
  model_uuid varchar(36) PRIMARY KEY, project_id text NOT NULL,
  request_id varchar(36) NOT NULL UNIQUE, actor text NOT NULL,
  root_id text, state varchar(16) NOT NULL CHECK (state IN ('RESERVED','PUBLISHED','UNKNOWN')),
  created_at timestamptz NOT NULL DEFAULT now()
);`;
const result = spawnSync('C:/PostgreSQL/16/bin/psql.exe', ['-X','-h','localhost','-p','5432','-U',config.POSTGRES_USER,'-d','sysarch','-v','ON_ERROR_STOP=1','-c',sql],
  { env: { ...process.env, PGPASSWORD: config.POSTGRES_PASSWORD }, encoding: 'utf8', windowsHide: true });
if (result.status !== 0) throw new Error('Runtime coordination schema preparation failed; no credentials logged');
const escape = value => String(value).replace(/\\/g, '\\\\').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/ /g, '\\ ');
mkdirSync('build/private', { recursive: true });
writeFileSync('build/private/m0-command-gate.properties', 'url=jdbc:postgresql://localhost:5432/sysarch\n'
  + 'user=' + escape(config.POSTGRES_USER) + '\npassword=' + escape(config.POSTGRES_PASSWORD) + '\n');
console.log(JSON.stringify({ database: 'sysarch', schema: 'sysarch_probe_m0', credentialsLogged: false, polarionDatabaseTouched: false }));
