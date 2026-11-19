// Local deployment launcher. Secrets are read from .env / the restricted deployment directory, never argv or logs.
import { readLocalEnv } from './local-env.mjs';
import { createHash, randomBytes } from 'node:crypto';
import { existsSync, openSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { spawn } from 'node:child_process';

const root = resolve('.');
const dir = resolve(process.argv[2] ?? '');
const mode = process.argv[3] ?? 'candidate';
if (!dir.startsWith(resolve('build/deployments') + sep) || !['candidate', 'rollback'].includes(mode)) throw new Error('Invalid deployment directory or mode');
const manifestPath = resolve(dir, 'manifest.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8').replace(/^\uFEFF/, ''));
const filename = mode === 'candidate' ? (manifest.candidateFile ?? 'candidate.jar') : 'rollback.jar';
if (!/^[A-Za-z0-9._-]+\.jar$/.test(filename)) throw new Error('Invalid reviewed artifact filename');
const jar = resolve(dir, filename);
const expected = mode === 'candidate' ? manifest.candidateSha256 : manifest.rollbackSha256;
if (!expected || createHash('sha256').update(readFileSync(jar)).digest('hex') !== expected) throw new Error('JAR hash differs from deployment manifest');
const config = readLocalEnv();
const privatePath = resolve(dir, 'runtime-private.json');
const secrets = existsSync(privatePath) ? JSON.parse(readFileSync(privatePath, 'utf8')) : {
  sessionSecret: randomBytes(48).toString('hex'), metricsToken: randomBytes(32).toString('hex'),
};
if (!existsSync(privatePath)) writeFileSync(privatePath, JSON.stringify(secrets));
const env = { ...process.env, ...config,
  POSTGRES_URL: config.POSTGRES_URL || 'jdbc:postgresql://localhost:5432/sysarch',
  POLARION_BASE_URL: config.POLARION_BASE_URL || 'http://localhost/polarion',
  // Existing local PAT is used for SSO role lookups; manual sync still sends the individual caller's PAT.
  POLARION_SYNC_TOKEN: config.POLARION_SYNC_TOKEN || config.POLARION_TOKEN,
  SYSARCH_AUTH_MODE: 'polarion', SYSARCH_ANONYMOUS_READ: 'false',
  SYSARCH_SESSION_SECRET: config.SYSARCH_SESSION_SECRET || secrets.sessionSecret,
  SYSARCH_METRICS_TOKEN: config.SYSARCH_METRICS_TOKEN || secrets.metricsToken,
  SYSARCH_SCHEDULED_PULL: 'false',
  SYSARCH_POLARION_STORE_ENABLED: mode === 'candidate' && manifest.modelStoreReadEnabled === true ? 'true' : 'false',
  SYSARCH_REPORT_DIR: config.SYSARCH_REPORT_DIR || resolve('data/reports'),
};
const python = 'C:/SysArchSolver/python/python.exe';
if (!env.SYSARCH_SOLVER_PYTHON && existsSync(python)) env.SYSARCH_SOLVER_PYTHON = python;
const stdout = openSync(resolve(dir, mode + '-stdout.log'), 'a');
const stderr = openSync(resolve(dir, mode + '-stderr.log'), 'a');
const java = 'C:/jdk-21.0.11.10-hotspot/bin/java.exe';
const proc = spawn(java, ['-jar', jar], { cwd: root, env, windowsHide: true, detached: true, stdio: ['ignore', stdout, stderr] });
proc.on('error', () => { console.error('Could not start the backend process'); process.exitCode = 1; });
proc.once('spawn', () => {
  manifest.backendPid = proc.pid;
  manifest.activeArtifact = mode;
  manifest.phase = 'STARTING';
  manifest.authMode = 'polarion';
  manifest.anonymousRead = false;
  manifest.scheduledPullEnabled = false;
  manifest.startedAt = new Date().toISOString();
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  console.log(JSON.stringify({ pid: proc.pid, mode, url: manifest.backendUrl, logDirectory: dir }));
  proc.unref();
});
