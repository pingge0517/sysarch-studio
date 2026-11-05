# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The spec, `p0-spec-sysarch-vsm.md` (Chinese), is the source of truth for everything below. Work proceeds by running tasks **T1–T9 (spec §8) in order**. Each task starts with a paste-ready instruction block and has hard dependencies (T3 needs T2, T4 needs T3, and so on).

T1 scaffold is in place. The spec §2.2 monorepo lives at the **repository root**, not in a `sysarch-studio/` subfolder. Gradle modules are `metamodel` (EMF codegen tool), `backend` and `vsm-compiler`. T1–T3 are done. T4 and T5 are partly done. The Sirius-independent layers exist: projection, snapshot diffing and auto-sync (`view.refresh`), and the layout store with delete cascade (`view.layout`), tested by `BddSnapshotTest` and `IbdSyncTest`. All of it is blocked on the Sirius Web adapter, which can't be built until the Sirius Web jars resolve. The adapter must render snapshots, apply diffs to open canvases, write layout on drag end, fall back to auto-layout, register the views in the model tree, and pass the manual UI checks. T6 is partly done too: `validation.ConstraintValidator` and `ValidationMatrixTest` exist. Hooking validation into save and the IBD create-edge tool needs the same adapter. The whole build now resolves and `./gradlew :backend:test` passes (44 tests). That does not include starting the application: no test boots the Spring context, because that needs PostgreSQL and this machine has no Docker. `frontend/` is a separate npm project (Vite + React 18, served by nginx on :3000, which proxies `/api`, `/graphql`, `/actuator` and `/subscriptions` to the backend). Package-level `TODO(Tn)` comments mark where each later task goes.

Deviations from the spec that are already decided:
- **Spring Boot is 3.5.5, not 3.3.x.** Sirius Web 2025.8.x (pinned at 2025.8.11) is built on Boot 3.5.5, and the spec's reason for its Boot version is alignment with Sirius Web.
- **Sirius Web artifacts come from GitHub Packages and need a token.** They are published to the `eclipse-sirius/sirius-web` and `eclipse-sirius/sirius-emf-json` repos. Set `GITHUB_TOKEN` (a **classic** PAT with `read:packages`; fine-grained tokens don't work) or `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties`, where this machine has it. Docker receives it as a BuildKit secret. Sirius Web's expression language AQL (`org.eclipse.acceleo:aql`) comes from Eclipse's Acceleo Maven repository, declared in `settings.gradle.kts`, because Gradle ignores repositories declared inside poms.
- The backend Docker build context is the repo root (`backend/Dockerfile`), because it needs the root Gradle files.
- **EMF code generation is headless** (T2). The `metamodel` Gradle module's `EmfCodeGenerator` replaces the spec's `java -jar org.eclipse.emf.codegen.ecore.jar`. It rebuilds `metamodel/sysarch.genmodel` from the Ecore, deletes the old output, and generates the model and edit layers into `backend/src/main/java/com/example/sysarch/model/{,impl,util,provider}`. It also writes `plugin.properties` and `icons/` to `backend/src/main/resources`. There is no `metamodel/src-gen`. The spec's package `com.example.sysarch.model` comes from base package `com.example` plus a `model` suffix, because EMF always inserts the EPackage name (`sysarch`). **Never hand-edit generated code.** Regenerate it instead; output is deterministic (LF, no timestamps). Hand-written code goes in other packages, e.g. `com.example.sysarch.config.SysarchEMFConfiguration`.
- **No Ecore-to-JSON converter.** Sirius Web takes Java EPackages directly: `@Bean EPackage` feeds its `EPackage.Registry`, and `@Bean AdapterFactory` feeds its `ComposedAdapterFactory`. So T2 step 4 / `runEcoreConverter` is not needed.
- **EMF runtime matches Sirius Web** (ecore 2.37, xmi 2.38, edit 2.22, common 2.31), not the spec's 2.35. The generated code targets the 2.35 API level instead (`runtimeVersion=2.35`).
- `backend` puts processed resources into the classes directory (`sourceSets.main.output.resourcesDir`), because `EMFPlugin` finds `plugin.properties` relative to the classes. Without it, every edit-layer label throws `MissingResourceException` in tests and `bootRun`.
- **Generated view code targets our own descriptor API, not Sirius Web's** (T3). The §4.5 sample's types (`IDiagramDescriptionProvider`, `DiagramDescription.newDiagramDescription`, `NodeStyle.builder`, `RefreshPolicy.incremental`…) don't exist in Sirius Web. They are implemented as plain Java in `com.example.sysarch.view.api`, with no Sirius dependency. Generated providers in `view.generated` use only that API and `@Component`. A hand-written adapter (T4) maps the descriptors onto Sirius Web, so a Sirius upgrade never touches generated code.
- **Several YAML files may share a viewpoint id** if they declare it identically. Both spec files declare `sysarch.core`, so a literal reading of §4.2's "conflict fails the build" would reject the spec's own files. A conflict is the same id with a different label, metamodelUri or priority. Representation ids are global and must be unique.
- **The VSM schema's field set is exactly what spec §4.2–§4.4 use**, which is more than §4.2's table lists. Adding a field means changing `views/schema/vsm.schema.json`, `ViewModel`, the templates and the descriptor API, and it needs review (spec §10.1).
- Beyond §4.2's three fatal checks (unknown class, unknown feature, unknown reference endpoint), vsm-compiler also rejects the two §4.4 pitfalls: a border node whose `affiliationFeature` is not a container reference, and a `create-edge` tool without `validateOnDrop: true`. It also rejects a `create-child` target type that the containment feature can't hold.
- **Diagram content comes from our projection layer** (T4), `view.projection.DiagramProjector`. It turns (description, root, active filters) into a value-typed `DiagramSnapshot`, and the Sirius adapter only renders that. This keeps the model the single source of truth (§5.1), makes snapshot tests meaningful, and gives T5 something to diff. The P0 projection rules are documented on `DiagramProjector`. Two of them were product decisions made with the user, because the verbatim BDD YAML can't express them:
  - an edge end with no node of its own is drawn to the node of its `typedBy`, with the label read from that end. So a BDD composition goes owner block → part's type, labelled with the part name;
  - when several node styles apply to one class, `isAbstract = true` elements get the last style and the rest get the first (`block`/`abstractBlock`).
  - (T5, also the user's decision) edge styles with the same type and ends are alternatives: the element gets the one whose id matches its `kind` literal, ignoring case and underscores (IBD `assembly`/`delegation` by `Connection.kind`), otherwise the first. The edge id uses the group's first style, so changing `kind` restyles an edge without losing its waypoints.

  Also: a node style of the root's type shows every such element in the document (a BDD shows all blocks), while other styles show only what `autoExpand` reaches.
- **Auto-sync = re-project and diff** (T5). `DiagramSynchronizer` listens to EMF notifications on the model resource and coalesces them into one refresh on the editing context's executor. It re-projects and publishes only the `SnapshotDiff` (insert, delete and update of nodes and edges). It doesn't derive the affected set from `autoExpand` depth as spec T5 step 3 words it: the diff yields exactly the affected set, and also catches cross-reference effects like a renamed type changing every part's sub-label. At 500 elements the median is about 3 ms server side (`build/perf-refresh.json`).
- **Layout lives in its own resource** (`DiagramLayoutStore`, records are `DiagramLayout` objects). It must never be the model resource, and `LayoutCascade.attach` rejects that. As a result, moving a node raises no model event, and deleting layout can't touch the model. `LayoutCascade` deletes the layout of deleted elements (node records, plus edge records whose ends were deleted) in every diagram. A move to another container is not a deletion. Edge layout refs are edge ids (`style:sourceId->targetId`). Known gap: undoing a delete does not restore its layout.
- **Constraints are evaluated by Eclipse OCL Pivot, read from the Ecore** (T6). `ConstraintValidator` compiles every `.../OCL/Pivot` annotation of `sysarch.ecore` once and evaluates the invariants on the element's class and superclasses. All 7 are loaded, including `NoSelfAllocate`, which the spec doesn't count. Do not re-implement them in Java. Java only adds the message (Chinese, e.g. `跨层跳过：LA → EPBS`) and the `anchors`, the elements to highlight. There are two entry points:
  - `validate`/`validateAll`, which runs everything before an edit is committed;
  - `validateEdgeDrop`, which runs only §3.2's on-drop constraints on a transient Connection that never enters the model.
- **Eclipse OCL is not on any Maven repository.** It comes from the 6.19.0 p2 update site through the artifact-only ivy repository in `settings.gradle.kts` (pseudo-group `eclipse.ocl.p2`), together with the Xtext 2.33 release it was built with. `libs.bundles.ocl.runtime` is the exact jar set verified standalone and is added non-transitively. The 5 Eclipse platform jars in it are only there so classes load. **Risk:** Guava, Guice and ANTLR 3 may clash with Sirius Web's versions; check once the backend resolves.
- **Risk for the Sirius adapter, not yet verified against its source:** as far as I know, Sirius Web's diagram event processor re-renders the whole diagram on each change and sends it in full. Meeting "no full redraw" (§5.2, AC-4) may therefore mean pushing `SnapshotDiff`s through our own subscription rather than relying on Sirius' refresh. Check this first when the jars are available.
- **Generated code is committed** and is not regenerated during the backend build. `VsmCompilerTest.committedGeneratedCodeIsUpToDate` fails if `view/generated` differs from a fresh generation.

In the spec, code blocks marked `[COPY]` must be written to disk **verbatim**, with no edits: `metamodel/sysarch.ecore` (§3.1), `views/sysarch-core.vsm.yaml` (§4.3), `views/sysarch-ibd.vsm.yaml` (§4.4) and `backend/src/main/resources/api/polarion-proxy.openapi.yaml` (§6.1). Items marked `[GENERATE]` are produced by Claude Code or by the generators.

## What is being built

This is a web-native system-architecture modeling tool built on **Sirius Web 2025.8.x**. It is pinned and forked, with no upstream upgrades during P0. It has three parts:
- A custom Ecore metamodel (`sysarch`), not SysON's full SysML v2. Its classes are PartDefinition, Part, Port, Connection, ItemFlow, Requirement and Allocate, plus an abstract `ArchElement`.
- Diagrams generated from a declarative YAML DSL ("VSM", following Papyrus `.odesign` semantics). They are not hand-written canvas code.
- Two-way binding to Polarion Work Items, plus a consistency report.

Stack (versions locked): JDK 21, Spring Boot 3.5.5 (see above), Gradle 8.14.3 Kotlin DSL (versions in `gradle/libs.versions.toml`), PostgreSQL 16, React 18 + TS 5, Spring GraphQL, EMF (see above), Eclipse OCL 6.x (Pivot), Polarion REST v2.

## Commands

Commands for later tasks come from spec §8–9 and only work once that task is done.

```bash
cp .env.example .env                                               # then fill POSTGRES_PASSWORD, GITHUB_TOKEN
sh tests/acceptance/ac1-health.sh                                  # AC-1, must be UP within 60s
cd frontend && npm ci && npm run build                             # frontend type-check + build (npm run dev for :3000)
./gradlew build                                                    # full build + CI gates
./gradlew :metamodel:generateModel                       # T2: regenerate genmodel + EMF model/edit code after changing sysarch.ecore
./gradlew :backend:test --tests "*ModelSmokeTest*"       # T2
./gradlew :vsm-compiler:generateViews                    # T3: regenerate backend view/generated from views/*.vsm.yaml (exit 1 + file:line on any error)
./gradlew :vsm-compiler:test                             # T3 (also checks committed generated code is fresh and compiles)
./gradlew :vsm-compiler:test --tests "*misspelledAppliesTo*"   # single test
./gradlew :backend:test --tests "*BddSnapshotTest*"      # T4 (projection of the generated BDD; IbdProjectionTest covers border nodes)
./gradlew :backend:test --tests "*IbdSyncTest*"          # T5 (also writes backend/build/perf-refresh.json)
./gradlew :backend:test --tests "*ValidationMatrixTest*" # T6 (6 spec cases + edge-drop/save paths, via Eclipse OCL)
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
- Ecore quirks found in T2. Keep the Ecore verbatim and handle these in the task that needs them:
  - The OCL `.../OCL/Pivot` annotations aren't declared through EMF's `constraints` annotation or a `validationDelegates` setting. EMF therefore generates no `SysarchValidator`, and T6 must load the invariants through OCL explicitly.
  - There are 7 invariants (`NoSelfAllocate` is extra), although the spec says six.
  - Nothing contains Connection, ItemFlow, Requirement, Allocate or DiagramLayout. They can only be resource roots.
  - `DiagramLayout` extends `ArchElement`, so it inherits `NameNotEmpty` and lives in the model. Keep this in mind for spec §5.1 invariant 2 (T5/T6).
  - EMF naming gives `isIsAbstract()` and `isIsConjugated()`.

## Out of scope for P0 (spec §1.3)

Parametric solving and simulation, real-time collaboration (use edit locks instead), a custom metamodel engine or renderer, diagrams beyond BDD, IBD and one allocation table, XMI import/export, report templates and SysML v2 text export.

## Running locally without Docker (this machine)

This machine has no Docker. It runs a local PostgreSQL 16.13 instead: the zip binaries in `C:\PostgreSQL\16`, data in `C:\PostgreSQL\16\data`, listening on `127.0.0.1:5432` with scram-sha-256. It is not a Windows service, so start it after a reboot. The `postgres` superuser password is in `C:\PostgreSQL\superuser.pwd`. The app role and database are both `sysarch`, and the app password is in the git-ignored `.env`. The local AC-1 equivalent passed on 2026-11-05: health UP 21 s after start, home page 200, Sirius Web's Liquibase created 15 tables.

```bash
# Start PostgreSQL. Redirect output: pg_ctl's child keeps inherited pipes open, so a piped command never returns
/c/PostgreSQL/16/bin/pg_ctl.exe -D C:/PostgreSQL/16/data -l C:/PostgreSQL/16/postgres.log -w start > /dev/null 2>&1
./gradlew :backend:bootJar
set -a; . ./.env; set +a; POSTGRES_URL=jdbc:postgresql://localhost:5432/sysarch java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.jar
cd frontend && npm run build && npx vite preview --port 3000 --strictPort   # proxies /api, /graphql, /actuator, /subscriptions to :8080
```

## Gradle troubleshooting

- `Classpath entry points to a non-existent location ... gradle-dist ...` or `Could not read workspace metadata ... kotlin-dsl`: a stale Kotlin DSL cache. Run `./gradlew --stop`, then delete `.gradle/` and `~/.gradle/caches/8.14.3/kotlin-dsl`.
- `Remote host terminated the handshake` while downloading: a transient network error on this machine; rerun.
