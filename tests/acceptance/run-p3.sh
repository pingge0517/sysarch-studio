#!/usr/bin/env sh
# P3 acceptance (p3 spec §8, P3-AC-1..AC-9), as far as it runs without a browser, Cameo or interviews.
#   P3-AC-1    ParametricViewTest (3 constraint blocks bound and saved; a binding across units refused)
#   P3-AC-2/3/4 SolveServiceTest, SolversTest, SolveSystemTest (solved = hand calculation within 1e-6; overdetermined 422;
#              "import os" refused, no solver process) - the real Python solver runs when SYSARCH_SOLVER_PYTHON (or
#              C:\SysArchSolver\python) exists, else those cases are skipped
#   P3-AC-5    StateMachineViewTest, BehaviorChecksTest (two initial states and an unreachable state are R7/R8)
#   P3-AC-6    XmiExportTest writes backend/build/xmi-export/domain-controller.xmi; opening it in Cameo is manual
#              (docs/xmi-export.md §6)
#   P3-AC-7    XmiRoundTripTest (three golden cases, export -> import -> export identical)
#   P3-AC-8    probe A conclusion: docs/probes/p3-probe-a-sequence.md (checked to exist)
#   P3-AC-9    run-p2.sh (P2 acceptance, which runs run-p0.sh and run-p1.sh against BASE)
# Also: the other P3 tests (metamodel v1.3, activity / use-case views, XMI import refusals, label templates).
# Usage: sh tests/acceptance/run-p3.sh            (needs a running backend at BASE for the P0-P2 regression)
set -eu
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:8080}"

./gradlew -q :backend:test --tests "*MetamodelV13Test*" --tests "*BehaviorChecksTest*" --tests "*BehaviorReportTest*" \
  --tests "*ParametricViewTest*" --tests "*SolveServiceTest*" --tests "*SolversTest*" --tests "*SolveSystemTest*" \
  --tests "*StateMachineViewTest*" --tests "*ActivityAndUseCaseViewTest*" --tests "*LabelTemplatesTest*" \
  --tests "*XmiExportTest*" --tests "*XmiImportTest*" --tests "*XmiRoundTripTest*" --rerun
./gradlew -q :vsm-compiler:test --rerun
test -f backend/build/xmi-export/domain-controller.xmi
test -f docs/probes/p3-probe-a-sequence.md
echo "P3-AC-1..AC-5, AC-7, AC-8 PASS (AC-6: open backend/build/xmi-export/domain-controller.xmi in Cameo by hand)"

BASE="$BASE" sh tests/acceptance/run-p2.sh
echo "P3-AC-9 PASS (P0-P2 regression)"
