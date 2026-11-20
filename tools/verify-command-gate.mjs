// Reads only local SysArch database credentials into child environment, never argv or reports.
import { spawn } from 'node:child_process';
import { readLocalEnv } from './local-env.mjs';
const config = readLocalEnv();
if (config.POSTGRES_URL && config.POSTGRES_URL !== 'jdbc:postgresql://localhost:5432/sysarch') throw Error('Unexpected SysArch database');
if (!config.POSTGRES_USER || !config.POSTGRES_PASSWORD) throw Error('Local SysArch credentials required');
const child = spawn('cmd.exe', ['/d','/c','gradlew.bat','--offline',':backend:commandGateLiveAcceptance'], {
  windowsHide: true, stdio: 'inherit', env: { ...process.env, POSTGRES_URL: 'jdbc:postgresql://localhost:5432/sysarch',
    POSTGRES_USER: config.POSTGRES_USER, POSTGRES_PASSWORD: config.POSTGRES_PASSWORD },
});
child.on('error', () => { console.error('Command gate verification could not start'); process.exitCode = 1; });
child.on('close', code => { process.exitCode = code ?? 1; });
