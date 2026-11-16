# Implementation Plan: My open PRs status (plus the project skeleton and configuration)

**Branch**: `001-my-prs` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-my-prs/spec.md`

## Summary

Deliver the first daily-usable command, `morning prs`. It shows every open PR I authored,
across repositories, in one table, sorted so the PRs needing action come first: changes
requested, then CI failures that are probably mine, then the rest. It needs no LLM.

The feature also lays the project foundation:

- the Quarkus/Picocli CLI;
- the YAML routine configuration (ADR-0008, with the loading mechanism revised in research
  R2);
- token resolution;
- the read-only GraphQL access layer (ADR-0004);
- the table renderer;
- the architecture tests that enforce the constitution's read-only boundary.

**Technical approach** (see [research.md](research.md)):

- at most 3 GraphQL requests per run:
  1. search plus rollups;
  2. one combined base-history and earlier-attempts query, only when there are failures;
  3. contexts paging, only when a PR has more than 100 checks;
- deterministic failure classification against the most recent base-branch commit that
  ran the same check;
- rendering as tty, markdown or JSON.

US2 (LLM relatedness) is deferred until after feature 002 builds the LLM boundary (R1).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Quarkus 3.40.1 (`quarkus-picocli`,
`quarkus-smallrye-graphql-client`, `quarkus-jackson` + `jackson-dataformat-yaml`,
`quarkus-hibernate-validator`). All are managed by the Quarkus BOM.

**Storage**: none. The config file is read-only. A cache arrives with 002.

**Testing**: `quarkus-junit` (`@QuarkusTest`, `@QuarkusMainTest`), `wiremock-standalone`
3.13.2 (research R8, as built), `archunit-junit5` 1.5.1, `graphql-java` (BOM 25.0, test
scope)

**Target Platform**: Linux and macOS, JVM (fast-jar). Native is a roadmap item.

**Project Type**: CLI

**Performance Goals**: at most 3 GitHub requests per run (SC-001), and under 5 s
end-to-end for about 50 PRs on a normal connection

**Constraints**:

- read-only toward GitHub (constitution Principle III);
- no network in tests;
- no secrets in config or output;
- no hardcoded repositories, orgs or people in `src/main`.

**Scale/Scope**: a few dozen open PRs; one user

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / constraint | How this plan complies | Status |
|---|---|---|
| I. Deterministic first | US1 and US3 are pure rules. The LLM story (US2) is deferred, and the spec names it as the only judgement step. | ✅ |
| II. LLM isolation | No LLM code in this feature. The token lives only in `GitHubCredentials`, which only the GraphQL client producer injects. | ✅ (n/a) |
| III. Read-only | `GraphQLQueries` executes only named, checked-in documents and rejects `mutation` and `subscription`. Values are passed as variables. ArchUnit confines `DynamicGraphQLClient` to `GraphQLQueries`, and `org.kohsuke.github` to the future `…github.actions` package. A schema test asserts every document is a `query`. | ✅ |
| IV. Token frugality | No LLM. GitHub requests are capped at 3 per run (also frugal on API cost). | ✅ (n/a) |
| V. Offline tests, `--no-llm` | WireMock fixtures only. `--no-llm` is accepted, and the output is complete without an LLM. | ✅ |
| VI. Simplicity, reuse, latest stable | Only BOM-managed extensions, plus 2 test libraries, both latest stable and active. No custom HTTP client, parser or table library beyond a renderer of about 150 lines (no maintained alternative; architecture.md). Jackson polymorphism is used instead of a custom config dispatcher. | ✅ |
| Configuration constraint | Secret-looking values are rejected, the config grants only read scope, and nothing is hardcoded. | ✅ |
| Secrets, TLS, logging | Token from the environment or `gh auth token` only. Default TLS. JSON logging off by default for the CLI, and request headers are never logged. | ✅ |

**Post-design re-check (after Phase 1):** ✅ No violations.

- R2 replaces `@ConfigMapping` with Jackson. That is a reuse-driven change within
  Principle VI, recorded in ADR-0008 (revised on this branch).
- R4 widens the "fails on base" lookup to the last 30 base commits. That stays
  deterministic and costs at most one request.

## Project Structure

### Documentation (this feature)

```text
specs/001-my-prs/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── cli.md           # commands, options, exit codes, messages
│   ├── config.md        # YAML schema (001 scope)
│   └── output-json.md   # --format json shape, schema "morning.my-prs/1"
└── tasks.md             # Phase 2 (speckit-tasks; not created by this command)
```

### Source Code (repository root)

```text
pom.xml                                  # groupId net.zakkak, artifactId morning
mvnw, .mvn/wrapper/
src/main/java/net/zakkak/morning/
├── cli/            # Picocli: MorningCommand (top, global options), PrsCommand, AllCommand, ExitCodes
├── config/         # MorningConfig, Section (sealed) + subtypes, ConfigLoader (Jackson YAML), ConfigError, SecretPatterns
├── github/         # GitHubCredentials, TokenResolver, GraphQLClientProducer, GraphQLQueries, Query (enum of documents)
├── prs/            # MyPrsFetcher (GraphQL → records), FailureClassifier, PrSorter, MyPrsReport, records
└── render/         # Table (wrap/width), TerminalInfo, TtyRenderer (OSC-8), MarkdownRenderer, JsonRenderer
src/main/resources/
├── application.properties               # morning.github.graphql-url, logging
└── graphql/        # my-prs.graphql, pr-checks-page.graphql, base-and-attempts.graphql
src/test/java/net/zakkak/morning/
├── arch/           # ReadOnlyBoundaryTest (ArchUnit)
├── github/         # GraphQLDocumentsTest (graphql-java schema validation), GraphQLQueriesTest, TokenResolverTest
├── config/         # ConfigLoaderTest (valid example, unknown type, missing field, secret value, overlap)
├── prs/            # FailureClassifierTest, PrSorterTest (pure unit tests)
├── render/         # TableTest (wrapping, width, OSC-8 width-neutral)
└── cli/            # PrsCommandTest (@QuarkusMainTest + WireMock): US1 1–5, US3 1–4, SC-001, SC-003
src/test/resources/
├── github/schema.docs.graphql, github/SCHEMA_VERSION
├── fixtures/my-prs/*.json               # sanitised recordings
└── config/*.yaml                        # valid and invalid configs
```

**Structure Decision**: a single Maven module. Packages are by capability (`cli`, `config`,
`github`, `render`) plus one package per section type (`prs` now; `ci`, `reviews`,
`summary` later), so each feature adds a package rather than modifying shared ones. Tests
mirror the packages, plus `arch/` for boundary rules.

## Delivery order (input for speckit-tasks)

1. **Skeleton:**
   - generate the project with the Quarkus Maven plugin (`3.40.1`, extension `picocli`);
   - add the other extensions and test dependencies;
   - generate the Maven wrapper;
   - `ReadOnlyBoundaryTest` (empty rules pass);
   - `.gitignore` already covers `target/`.
2. **Config (US3):** records, Jackson YAML loader, validation, secret patterns, errors with
   paths; `examples/zakkak.yaml` parses. Unit tests.
3. **GitHub access:** `TokenResolver`, `GitHubCredentials`, client producer,
   `GraphQLQueries` with mutation rejection, the three `.graphql` documents, vendored schema
   plus `GraphQLDocumentsTest`.
4. **PRs:** fetcher (search, then conditional base-and-attempts, then conditional paging),
   `FailureClassifier`, `PrSorter`, actions. Unit tests on records.
5. **Render:** `Table`, `TerminalInfo`, tty, md and json renderers. Unit tests.
6. **CLI wiring and end-to-end:** `PrsCommand` and `AllCommand`, exit codes,
   `@QuarkusMainTest` with WireMock fixtures covering every acceptance scenario, SC-001 and
   SC-003.
7. **Close-out:**
   - `docs/architecture.md` (the Implemented column, the config mechanism, R4 base lookup);
   - ADR-0008 revision (R2);
   - `ROADMAP.md` status;
   - `AGENTS.md` commands verified;
   - a manual live run per quickstart.

## Spec alignment notes

- FR-003 and US1 scenario 2 say "the base branch's latest commit". This plan uses the
  **most recent base commit that ran a check with the same name**, within the last 30
  (R4), because head commits often carry no checks (1 of 17 PRs in a live sample). The
  spec wording is updated on this branch to match.
- US2 is deferred (R1). The "Failures related to me?" column shows rule results only.

## Complexity Tracking

No constitution violations to justify.
