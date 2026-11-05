# SysArch Studio

A web-native, layered system-architecture modeling tool built on Sirius Web, with two-way
binding to Polarion. The spec is in `p0-spec-sysarch-vsm.md`. P0 runs as tasks T1–T9 (spec §8).

## Start in 3 lines

```bash
cp .env.example .env    # set POSTGRES_PASSWORD and GITHUB_TOKEN (PAT with read:packages)
docker compose up -d
curl -fsS localhost:8080/actuator/health    # expect "UP"; UI at http://localhost:3000
```

`GITHUB_TOKEN` is needed because Sirius Web artifacts are only published to GitHub Packages.
Docker passes it as a build secret, so it is never stored in an image.

## Building without Docker

```bash
export GITHUB_ACTOR=<github-user> GITHUB_TOKEN=<pat>   # or gpr.user / gpr.key in ~/.gradle/gradle.properties
./gradlew build
cd frontend && npm ci && npm run dev                     # dev server on :3000, proxies to :8080
```

Running the backend locally needs `POSTGRES_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD`.

## Layout

| Path | Contents |
|---|---|
| `metamodel/` | `sysarch.ecore` and genmodel (T2) |
| `views/` | VSM YAML view definitions and their JSON Schema (T3–T5) |
| `vsm-compiler/` | Generator from VSM YAML to `DiagramDescription` Java (T3) |
| `backend/` | Spring Boot + Sirius Web app, Polarion proxy (`model`, `view`, `validation`, `link`, `sync`) |
| `frontend/` | React shell, served by nginx on :3000 |
| `tests/acceptance/` | Acceptance scripts (spec §9), e.g. `ac1-health.sh` |

## Versions

Pinned in `gradle/libs.versions.toml`: JDK 21, Spring Boot 3.5.5, Sirius Web 2025.8.11, PostgreSQL 16,
React 18.3, TypeScript 5.9. The spec lists Spring Boot 3.3.x, but Sirius Web 2025.8.x is built on
Spring Boot 3.5.5, so the backend uses 3.5.5 to stay aligned with it.
