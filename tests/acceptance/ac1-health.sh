#!/usr/bin/env sh
# AC-1 (spec §9.1): after `docker compose up -d`, backend health is UP within 60s
# and the frontend home page returns 200.
set -eu
docker compose up -d
for i in $(seq 1 60); do
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q UP; then
    curl -fsS -o /dev/null http://localhost:3000/ && echo "AC-1 PASS after ${i}s" && exit 0
  fi
  sleep 1
done
echo "AC-1 FAIL: health not UP within 60s" >&2
docker compose logs --tail=50 backend >&2
exit 1
