# SysArch Studio

A web-native, layered system-architecture modeling tool built on Sirius Web, with two-way
binding to Polarion. The spec is in `p0-spec-sysarch-vsm.md`. P0 runs as tasks T1–T9 (spec §8).

## Start in 3 lines

```bash
cp .env.example .env    # set POSTGRES_PASSWORD and GITHUB_TOKEN (PAT with read:packages)
docker compose up -d
curl -fsS localhost:8080/actuator/health    # expect "UP"; modeling UI (Sirius Web) at http://localhost:8080
```

`GITHUB_TOKEN` is needed because Sirius Web artifacts are only published to GitHub Packages.
Docker passes it as a build secret, so it is never stored in an image.

## Building without Docker

```bash
export GITHUB_ACTOR=<github-user> GITHUB_TOKEN=<pat>   # or gpr.user / gpr.key in ~/.gradle/gradle.properties
./gradlew build
cd frontend && NPM_TOKEN=$GITHUB_TOKEN npm ci && npm run dev   # :3000, proxies to :8080; NPM_TOKEN: Sirius Web's npm packages (GitHub Packages)
```

Running the backend locally needs `POSTGRES_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD`.

## Layout

| Path | Contents |
|---|---|
| `metamodel/` | `sysarch.ecore` and genmodel (T2) |
| `views/` | VSM YAML view definitions and their JSON Schema (T3–T5) |
| `vsm-compiler/` | Generator from VSM YAML to `DiagramDescription` Java (T3) |
| `backend/` | Spring Boot + Sirius Web app, Polarion proxy (`model`, `view`, `validation`, `link`, `sync`) |
| `frontend/` | The application on :3000 (nginx): Sirius Web's own application (project browser, diagram editor) hosting SysArch's pages at `/sysarch` (P4 T3), and the Polarion embed page `/?embed=1` |
| `tests/acceptance/` | Acceptance scripts (spec §9), e.g. `ac1-health.sh` |
| `docs/` | `metamodel-and-views.md`: metamodel, field tables, view mapping, adding a view (T9) |
| `docs/param-behavior.md` | P3 user guide (parametric view and solving, state machine / activity / use case, XMI) and the **three external limitations**: XMI round trips do not keep layers, solving needs an external back end, behaviour diagrams are not executed |
| `docs/probes/` | P3 probes: A (sequence diagrams, No-Go) and B (geometry view, interview guide) |
| `docs/p4-pilot-report.md` | P4 pilot report (template, filled in during the 4-week pilot): preparation, coverage, issues by week, operations, release checks |
| `docs/e2e-test-report.md` | End-to-end test of every implemented feature (P0-P4) on one instance in production configuration: 95 API + 26 UI checks, regression, multi-user load; 3 defects found and fixed |
| `docs/security-review-p4.md` | Security review (P4 T8): every write endpoint and its permission check, findings and their status, the open read-access decision |
| `docs/sirius-upgrade-2026.11.md` | Sirius Web 2026.11.0 upgrade rehearsal (P4 T7): Spring Boot 4.1.1, 70 compile errors in 9 adapter files, frontend unchanged; stay on 2025.8.11 for now |
| `docs/monitoring.md` | Metrics (/actuator/prometheus with a token), alerts, caps on logs and reports, Polarion log retention advice (P4 T5) |
| `docs/polarion-embedding.md` | Embedding SysArch in Polarion: the single sign-on extension (`polarion-sso/`), its installation, the SysArch settings and the embed page |
| `solver/` | The spec's solver script (verbatim), the remote solver service and its Dockerfile (P3) |
| `.github/workflows/ci.yml` | CI: tests, VSM determinism (sha256), refresh perf threshold (T9) |

## Demo data

In the Sirius Web UI, create a project, then a model from the template **系统架构模型（域控制器演示）**:
a domain controller with 3 logical parts, 6 ports, 4 connections and 5 requirements across LA/PA
(`backend/.../demo/DomainControllerDemo.java`). Open a BDD or IBD on `PD-001 域控制器`.

## CI

`./gradlew build` runs the three CI gates: all tests, `:vsm-compiler:verifyDeterministicGeneration`
(two generations and the committed code have the same sha256) and `:backend:checkPerfThreshold`
(median < 800 ms, P95 < 2000 ms). GitHub Actions needs the secrets `GPR_TOKEN` (classic PAT, `read:packages`)
and `GPR_USER`.

## Versions

Pinned in `gradle/libs.versions.toml`: JDK 21, Spring Boot 3.5.5, Sirius Web 2025.8.11, PostgreSQL 16,
React 18.3, TypeScript 5.9. The spec lists Spring Boot 3.3.x, but Sirius Web 2025.8.x is built on
Spring Boot 3.5.5, so the backend uses 3.5.5 to stay aligned with it.
