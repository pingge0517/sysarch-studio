#!/usr/bin/env sh
# P0 acceptance (p0 spec §9.1, AC-1..AC-6), as far as it runs without a browser.
#   AC-1  health of the running backend (BASE, default http://localhost:8080); with --compose, ac1-health.sh
#         (docker compose up, then health UP within 60 s and the frontend 200)
#   AC-2..AC-6  the automated tests that check them:
#         AC-2 ModelSmokeTest (all element kinds), AC-3 VsmCompilerTest (YAML -> generated descriptions),
#         AC-4 IbdSyncTest + SiriusRenderingTest (model change reaches the diagram), AC-5 ValidationMatrixTest
#         (incompatible ports refused, both ends marked), AC-6 ConsistencyIT (deleted Work Item -> R1, 待认领)
# The browser-only parts (border colour on the canvas, red ports, the Validation view) were checked by hand at
# v0.1.0; see CLAUDE.md.
set -eu
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:8080}"

if [ "${1:-}" = "--compose" ]; then
  sh tests/acceptance/ac1-health.sh
else
  if curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"'; then
    echo "AC-1 PASS: $BASE health UP"
  else
    echo "AC-1 FAIL: $BASE health is not UP (start the backend, see CLAUDE.md 'Running locally')" >&2
    exit 1
  fi
fi

./gradlew -q :backend:test --tests "*ModelSmokeTest*" --tests "*IbdSyncTest*" --tests "*SiriusRenderingTest*" \
  --tests "*ValidationMatrixTest*" --tests "*ConsistencyIT*" --tests "*DomainControllerDemoTest*" --rerun
./gradlew -q :vsm-compiler:test --tests "*VsmCompilerTest*" --rerun
echo "P0 AC-2..AC-6 tests PASS"
