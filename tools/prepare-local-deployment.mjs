import { readLocalEnv } from './local-env.mjs';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = resolve('.');
const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');
const dir = resolve('build/deployments', stamp);
mkdirSync(dir, { recursive: true });
const config = readLocalEnv();
const run = (exe, args, env = process.env) => {
  const result = spawnSync(exe, args, { cwd: root, env, windowsHide: true, encoding: 'utf8' });
  if (result.status !== 0 || result.error) throw new Error(`${exe} failed: ${result.error?.message ?? result.stderr}`);
  return result.stdout?.trim();
};
cpSync('backend/build/libs/backend-0.1.0-SNAPSHOT.jar', resolve(dir, 'candidate.jar'));
cpSync('frontend/dist', resolve(dir, 'frontend-dist'), { recursive: true });
run('git', ['archive', '--format=zip', '--output', resolve(dir, 'rollback-source.zip'), 'HEAD']);
const dump = resolve(dir, 'sysarch.dump');
run('C:/PostgreSQL/16/bin/pg_dump.exe', ['-h', '127.0.0.1', '-p', '5432', '-U', config.POSTGRES_USER, '-Fc', '-f', dump, 'sysarch'],
  { ...process.env, PGPASSWORD: config.POSTGRES_PASSWORD });
const hash = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');
const manifest = {
  createdAt: new Date().toISOString(), directory: dir,
  rollbackCommit: run('git', ['rev-parse', 'HEAD']),
  candidateSha256: hash(resolve(dir, 'candidate.jar')),
  databaseBackupSha256: hash(dump),
  databaseBackup: dump, backendUrl: 'http://localhost:8080', frontendUrl: 'http://localhost:3000',
  phase: 'PREPARED', note: 'Rollback JAR must be built from rollback-source.zip before replacing the running backend.',
};
writeFileSync(resolve(dir, 'manifest.json'), JSON.stringify(manifest, null, 2));
console.log(JSON.stringify(manifest, null, 2));
