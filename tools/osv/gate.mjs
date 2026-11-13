// CI gate for the dependency scan (P4 T8, security review F6; spec P4 §5.2: high-severity vulnerabilities block the
// build). Reads OSV-Scanner's JSON output (ignored entries of osv-scanner.toml are already left out) and fails when a
// vulnerability has a CVSS score >= 7.0, or no score at all (unknown counts as high). Medium and low ones are listed
// but do not fail. Usage: node tools/osv/gate.mjs <osv-scanner json>
import { readFileSync } from 'node:fs';

const THRESHOLD = 7.0;
const file = process.argv[2];
if (!file) {
  console.error('usage: node tools/osv/gate.mjs <osv-scanner json>');
  process.exit(2);
}
const report = JSON.parse(readFileSync(file, 'utf8'));
const findings = [];
for (const result of report.results ?? []) {
  for (const pkg of result.packages ?? []) {
    for (const group of pkg.groups ?? []) {
      const id = group.ids[0];
      const vuln = (pkg.vulnerabilities ?? []).find((v) => v.id === id) ?? {};
      const score = Number.parseFloat(group.max_severity);
      findings.push({
        id,
        score: Number.isNaN(score) ? null : score,
        pkg: `${pkg.package.name}@${pkg.package.version}`,
        source: result.source?.path ?? '?',
        summary: vuln.summary ?? '',
      });
    }
  }
}
const blocking = findings.filter((f) => f.score === null || f.score >= THRESHOLD);
const line = (f) => `  ${f.score ?? '?'}\t${f.id}\t${f.pkg}\t${f.summary}`;
console.log(`OSV-Scanner: ${findings.length} vulnerabilities not on the ignore list, ${blocking.length} high (CVSS >= ${THRESHOLD} or unscored)`);
for (const f of findings.filter((f) => !blocking.includes(f))) console.log(line(f));
if (blocking.length > 0) {
  console.log('Blocking:');
  for (const f of blocking) console.log(line(f));
  console.log('Upgrade the dependency, or add it to tools/osv/osv-scanner.toml with a reason and a review date.');
  process.exit(1);
}
