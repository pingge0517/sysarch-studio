#!/usr/bin/env sh
# P2 acceptance (p2 spec §9, P2-AC-1..AC-9), as far as it runs without a browser or Polarion.
#   P2-AC-1/2  LockTest (second editor 423 with the holder; no heartbeat for 90 s -> released)
#   P2-AC-3/4  MergeServiceTest, MergeEngineTest (5 changes previewed and applied; a field conflict listed, not overwritten)
#   P2-AC-5    ReportTemplatesTest (SRS >= 12 pages with the three layers and the trace table)
#   P2-AC-6    SysmlImportTest (export -> import, same element count)
#   P2-AC-7    TenThousandBenchmarkTest + checkPerfThreshold (IBD of 10,000 elements < 3 s, and the other §6.1 limits)
#   T8         AccessTest (reviewer refused and audited)
#   P2-AC-8    with --load: tests/perf/collab-load.mjs against BASE (a backend in SYSARCH_AUTH_MODE=dev); default 30 minutes,
#              --load-seconds N for a shorter run
#   P2-AC-9    run-p0.sh and run-p1.sh (also against BASE)
# Usage: sh tests/acceptance/run-p2.sh [--load] [--load-seconds 1800]
set -eu
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:8080}"
LOAD=false
SECONDS_LOAD=1800
while [ $# -gt 0 ]; do
  case "$1" in
    --load) LOAD=true ;;
    --load-seconds) SECONDS_LOAD="$2"; shift ;;
  esac
  shift
done

./gradlew -q :backend:test --tests "*LockTest*" --tests "*SessionTest*" --tests "*BranchTest*" --tests "*MergeEngineTest*" \
  --tests "*MergeServiceTest*" --tests "*ReportTemplatesTest*" --tests "*ReportServiceTest*" --tests "*SysmlImportTest*" \
  --tests "*ImportServiceTest*" --tests "*AccessTest*" --tests "*TenThousandBenchmarkTest*" --rerun
./gradlew -q :backend:checkPerfThreshold
echo "P2-AC-1..AC-7 and T8 tests PASS"

BASE="$BASE" sh tests/acceptance/run-p0.sh
BASE="$BASE" sh tests/acceptance/run-p1.sh
echo "P2-AC-9 PASS (P0 and P1 regression)"

if [ "$LOAD" = true ]; then
  node tests/perf/collab-load.mjs --base "$BASE" --duration "$SECONDS_LOAD"
  echo "P2-AC-8 PASS"
fi
