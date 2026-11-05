# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The spec, `p0-spec-sysarch-vsm.md` (Chinese), is the source of truth for everything below. Work proceeds by running tasks **T1–T9 (spec §8) in order**. Each task starts with a paste-ready instruction block and has hard dependencies (T3 needs T2, T4 needs T3, and so on).

T1 scaffold is in place. The spec §2.2 monorepo lives at the **repository root**, not in a `sysarch-studio/` subfolder. Gradle modules are `backend` and `vsm-compiler`. `frontend/` is a separate npm project (Vite + React 18, served by nginx on :3000, which proxies `/api`, `/graphql`, `/actuator` and `/subscriptions` to the backend). Package-level `TODO(Tn)` comments mark where each later task goes.

Deviations from the spec that are already decided:
- **Spring Boot is 3.5.5, not 3.3.x.** Sirius Web 2025.8.x (pinned at 2025.8.11) is built on Boot 3.5.5, and the spec's reason for its Boot version is alignment with Sirius Web.
- **Sirius Web artifacts come from GitHub Packages and need a token.** They are published to the `eclipse-sirius/sirius-web` and `eclipse-sirius/sirius-emf-json` repos. Set `GITHUB_TOKEN` (any PAT with `read:packages`) or `gpr.key`. Docker receives it as a BuildKit secret.
- The backend Docker build context is the repo root (`backend/Dockerfile`), because it needs the root Gradle files.

In the spec, code blocks marked `[COPY]` must be written to disk **verbatim**, with no edits: `metamodel/sysarch.ecore` (§3.1), `views/sysarch-core.vsm.yaml` (§4.3), `views/sysarch-ibd.vsm.yaml` (§4.4) and `backend/src/main/resources/api/polarion-proxy.openapi.yaml` (§6.1). Items marked `[GENERATE]` are produced by Claude Code or by the generators.

## What is being built

This is a web-native system-architecture modeling tool built on **Sirius Web 2025.8.x**. It is pinned and forked, with no upstream upgrades during P0. It has three parts:
- A custom Ecore metamodel (`sysarch`), not SysON's full SysML v2. Its classes are PartDefinition, Part, Port, Connection, ItemFlow, Requirement and Allocate, plus an abstract `ArchElement`.
- Diagrams generated from a declarative YAML DSL ("VSM", following Papyrus `.odesign` semantics). They are not hand-written canvas code.
- Two-way binding to Polarion Work Items, plus a consistency report.

Stack (versions locked): JDK 21, Spring Boot 3.5.5 (see above), Gradle 8.14.3 Kotlin DSL (versions in `gradle/libs.versions.toml`), PostgreSQL 16, React 18 + TS 5, Spring GraphQL, EMF 2.35, Eclipse OCL 6.x (Pivot), Polarion REST v2.

## Commands

Commands for later tasks come from spec §8–9 and only work once that task is done.

```bash
cp .env.example .env                                               # then fill POSTGRES_PASSWORD, GITHUB_TOKEN
sh tests/acceptance/ac1-health.sh                                  # AC-1, must be UP within 60s
cd frontend && npm ci && npm run build                             # frontend type-check + build (npm run dev for :3000)
./gradlew build                                                    # full build + CI gates
./gradlew :vsm-compiler:test
./gradlew :vsm-compiler:runEcoreConverter --args="metamodel/sysarch.ecore build/sysarch.metamodel.json"
./gradlew :backend:test --tests "*ModelSmokeTest*"       # T2
./gradlew :backend:test --tests "*BddSnapshotTest*"      # T4
./gradlew :backend:test --tests "*IbdSyncTest*"          # T5 (also writes build/perf-refresh.json)
./gradlew :backend:test --tests "*ValidationMatrixTest*" # T6
./gradlew :backend:test --tests "*PolarionSyncIT*"       # T7 (WireMock)
```

CI has three gates, and all must pass: gradle tests, a sha256 check that VSM generation is deterministic, and the perf threshold check.

Env vars: `POSTGRES_URL/USER/PASSWORD`, `POLARION_BASE_URL`, `CONSISTENCY_STRATEGY` (`MODEL_WINS`|`POLARION_WINS`) are required. `POLARION_TOKEN` (demo only), `SYNC_RATE_LIMIT_RPS` (default 10) and `VIEW_REFRESH_BUDGET_MS` (default 2000) are optional. Never hardcode credentials in `application.yml`.

## Architecture

```
frontend → backend (GraphQL) → Sirius Web core (read-only dependency)
                               ├─ sysarch metamodel (EMF code, package com.example.sysarch.model)
                               ├─ VSM-generated views (backend/.../view/generated/)
                               └─ link/ + sync/ (Polarion binding)
backend → polarion proxy → Polarion REST v2   (browser never calls Polarion directly)
```

**Hard boundaries.** Breaking any of these fails review.
- Do not modify Sirius Web source, replace its renderer, or bypass `EditingContext` to write to the DB. Changes are additive only: new packages, new `DiagramDescription`s registered, `IEditingContext` extensions, interceptors.
- No separate account system. Auth always passes through the user's Polarion login, and the service stores no passwords or tokens.
- An implementation where "changing a field requires changing core code" fails.

**VSM pipeline.** `views/*.vsm.yaml` → vsm-compiler (parse → cross-check against the metamodel → FreeMarker templates → registry) → `<RepId>DiagramDescriptionProvider.java` + `ViewpointRegistry.java` in `backend/src/main/java/com/example/sysarch/view/generated/`.
- Output must be byte-for-byte deterministic. Delete `generated/` before each run, and never read runtime or machine config.
- Validation errors are fatal and never warnings. They print `views/<file>:<line> <message>` and exit with code 1. The cases are an unknown class, an unknown feature, a bad edge endpoint, and an unknown `{feature}` in a label.
- YAML is never parsed at runtime. The backend reads only the generated code.
- DSL fields are capped at the list in spec §4.2. Adding a field requires review.
- A new diagram means a new YAML file plus regenerating. No Java changes.

**View sync semantics (spec §5)** are what AC-4 checks.
- The model is the single source of truth. Every diagram node must map back to an `elementRef`.
- `DiagramLayout` (x/y/size/waypoints) is stored separately from the model. Deleting a model element cascades to its layout and edges. Layout changes must not emit model events.
- `synchronization: auto` views cannot "remove from diagram but keep in model". Hiding is only possible through filters such as `hideAbstract`.
- Refresh must be **incremental**, computed as a diff from `autoExpand` depth and pushed over a throttled GraphQL subscription. Redrawing the whole diagram is forbidden. Perf gate: with 500 elements and 20 inserts, median < 800ms and P95 < 2000ms.

**Validation (spec §3.2, T6).** The OCL invariants are NameNotEmpty, InterfaceTypeRequired, PortTypeCompatible, NoSelfLoop, DirectionCompatible and LayerAdjacentOnly. They run on save and on edge drop (`validateOnDrop: true`). Errors use the shape `{code, elementId, message, anchors[]}`.
- `PortTypeCompatible` is plain string equality on `interfaceType`, with no inheritance. Document this limit in code.
- For `Allocate`, only adjacent layers are allowed: OPERATIONAL→SYSTEM→LOGICAL→PHYSICAL→EPBS.
- Layers are a `layer` enum plus `stereotypes`. Do not reuse Capella's metamodel.

**Polarion sync (spec §6.2–6.3, §7).**
- Each field has exactly one writer, per the ownership matrix in §6.2. Fields the matrix doesn't cover are recorded as CONFLICT and never auto-merged. Only creates merge automatically. New Work Items go into an "unbound pool" until someone claims them.
- `/sync/push` and `/sync/pull` require an `Idempotency-Key`, with 24h dedup.
- Push runs a full precheck first and rolls back the whole batch on any failure.
- Polarion calls go through a token bucket (10 rps). On 429, back off per `Retry-After`, up to 3 times.
- Every sync writes to the `sync_audit` table.
- Scheduled jobs only detect problems. Repair happens only when a human calls `/consistency/repair`.
- Webhooks verify the signature and only enqueue.
- The consistency rules are R1 orphan binding, R2 dangling edge, R3 duplicate binding, R4 layer violation and R5 unbound element.

## Gotchas (spec §11.2)

- Escape `<>` in Ecore OCL as `&lt;&gt;`, or the OCL silently stops working.
- A border node (`isBorderNode`) needs the correct `affiliationFeature` (`partOwner` / `owner`), or ports float in the middle of the canvas.
- `eOpposite` must be paired. If it isn't, the EMF notification chain breaks and auto-sync gets no events.

## Out of scope for P0 (spec §1.3)

Parametric solving and simulation, real-time collaboration (use edit locks instead), a custom metamodel engine or renderer, diagrams beyond BDD, IBD and one allocation table, XMI import/export, report templates and SysML v2 text export.
