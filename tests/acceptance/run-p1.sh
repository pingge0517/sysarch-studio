#!/usr/bin/env sh
# P1 acceptance (p1 spec §6), as far as it runs without a browser, Polarion or SysON:
#   p1-acceptance.mjs against the running backend (BASE, default http://localhost:8080): P1-AC-1..AC-4 on a
#   throw-away demo project; then the tests behind every P1 acceptance item, including AC-3's Polarion side and
#   AC-6 (TraceAndScheduleIT, WireMock) and the T9 view-switch baseline. P1-AC-5 (import into SysON) is manual.
set -eu
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:8080}"

node tests/acceptance/p1-acceptance.mjs "$BASE"
./gradlew -q :backend:test --tests "*LayerViewsTest*" --tests "*AllocationMatrixTest*" --tests "*RequirementTraceTest*" \
  --tests "*TraceAndScheduleIT*" --tests "*BaselineTest*" --tests "*SysmlV2ExportTest*" --tests "*ViewSwitchPerfTest*" \
  --tests "*InterfaceMigrationTest*" --tests "*InterfaceCompatibilityTest*" --rerun
echo "P1 acceptance tests PASS (P1-AC-5 in SysON is manual)"
