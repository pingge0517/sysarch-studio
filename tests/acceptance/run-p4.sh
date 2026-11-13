#!/usr/bin/env sh
# P4 acceptance (p4 spec §8, P4-AC-1..AC-10), as far as it runs without a browser, a clean Polarion, a cluster or the
# pilot team.
#   P4-AC-1   ProjectBindingsTest + p4-acceptance.mjs (bind without restart, non-admin 403 audited, unbind, model deleted)
#   P4-AC-2   install on a *clean* Polarion within 30 minutes: manual (docs/polarion-embedding.md; the update of an
#             existing install is verified, see CLAUDE.md "P4 T2")
#   P4-AC-3   single interface, Go: merge.test.ts (frontend) here; P0 AC-4/AC-5 inside the shell were checked in the
#             browser (CLAUDE.md "P4 T3")
#   P4-AC-4   Helm first install: needs a cluster (T4, postponed)
#   P4-AC-5   MetricsTokenFilterTest, SysarchMetricsTest, ReportServiceTest (size cap); metrics closed without token
#   P4-AC-6   restore drill: recorded in docs/runbook.md §4.1 (checked to exist)
#   P4-AC-7   ReadAccessFilterTest, PolarionSsoTest, AssertionsTest; security headers; docs/security-review-p4.md;
#             the dependency scan is CI gate 4 (job dependency-scan), run here too when OSV-Scanner is installed
#   P4-AC-8/9 pilot, probe B, Cameo: the user's (docs/probes/p3-probe-b-geometry.md, docs/xmi-export.md §6)
#   P4-AC-10  run-p3.sh (P3 acceptance and the P0-P2 regression against BASE)
# Usage: sh tests/acceptance/run-p4.sh     (needs a running backend at BASE in SYSARCH_AUTH_MODE=dev)
set -eu
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:8080}"

./gradlew -q :backend:test --tests "*ProjectBindingsTest*" --tests "*MetricsTokenFilterTest*" --tests "*SysarchMetricsTest*" \
  --tests "*ReportServiceTest*" --tests "*ReadAccessFilterTest*" --tests "*PolarionSsoTest*" --rerun
./gradlew -q :polarion-sso:test --rerun
(cd frontend && npm test --silent > /dev/null)
for doc in docs/security-review-p4.md docs/runbook.md docs/monitoring.md docs/sirius-upgrade-2026.11.md docs/polarion-embedding.md; do
  test -f "$doc"
done
grep -q "RTO" docs/runbook.md
echo "P4-AC-1, AC-5, AC-6, AC-7 tests PASS (AC-2 clean install, AC-4 Helm, AC-8/9 pilot are manual)"

BASE="$BASE" node tests/acceptance/p4-acceptance.mjs

SCANNER="${OSV_SCANNER:-/c/OSVScanner/osv-scanner.exe}"
if [ -x "$SCANNER" ]; then
  ./gradlew -q osvLockfile > /dev/null
  "$SCANNER" scan source --config tools/osv/osv-scanner.toml --format json \
    -L backend/build/osv/gradle.lockfile -L metamodel/build/osv/gradle.lockfile -L vsm-compiler/build/osv/gradle.lockfile \
    -L polarion-sso/build/osv/gradle.lockfile -L frontend/package-lock.json > build/osv-results.json 2> /dev/null || true
  node tools/osv/gate.mjs build/osv-results.json
  echo "P4-AC-7 dependency scan PASS"
else
  echo "P4-AC-7 dependency scan skipped here (no OSV-Scanner at $SCANNER; CI gate 4 runs it)"
fi

BASE="$BASE" sh tests/acceptance/run-p3.sh
echo "P4-AC-10 PASS (P0-P3 regression)"
