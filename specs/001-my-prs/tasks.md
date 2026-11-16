---
description: "Task list for 001-my-prs"
---

# Tasks: My open PRs status (plus the project skeleton and configuration)

**Input**: `specs/001-my-prs/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests**: requested. The spec's independent tests, SC-001/SC-003 and the constitution's
definition of done (ArchUnit, GraphQL schema checks) all need them.

**Scope**: US1 (P1) and US3 (P1). US2 (P3) is deferred to after feature 002 (research R1).
US3 comes first because US1 depends on the config loader.

Paths are relative to the repository root. `M` = `src/main/java/net/zakkak/morning`,
`T` = `src/test/java/net/zakkak/morning`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 or US3

---

## Phase 1: Setup

- [X] T001 Generate the Quarkus 3.40.1 project in place (`groupId` `net.zakkak`, `artifactId` `morning`, extension `picocli`, Java 25) and remove generated sample code; `pom.xml`
- [X] T002 Add extensions `quarkus-smallrye-graphql-client`, `quarkus-jackson`, `jackson-dataformat-yaml`, `quarkus-hibernate-validator`, and test dependencies `quarkus-junit5`, `org.wiremock:wiremock-standalone:3.13.2` (was `quarkus-wiremock-test`; see research R8, as built), `com.tngtech.archunit:archunit-junit5:1.5.1`, `com.graphql-java:graphql-java` (BOM, test scope); `pom.xml`
- [X] T003 Generate the Maven wrapper (`mvn wrapper:wrapper`); `mvnw`, `.mvn/wrapper/`
- [X] T004 [P] `application.properties`: `morning.github.graphql-url=https://api.github.com/graphql`, quiet console logging for the CLI (banner off, level WARN), no header logging; `src/main/resources/application.properties`
- [X] T005 [P] `ReadOnlyBoundaryTest` (ArchUnit): `DynamicGraphQLClient` used only by `github.GraphQLQueries` and `github.GraphQLClientProducer`; `org.kohsuke.github` only in `..github.actions..` (allow empty); `T/arch/ReadOnlyBoundaryTest.java`
- [X] T006 `./mvnw verify` passes on the empty skeleton

---

## Phase 2: Foundational (blocks US1 and US3)

- [X] T007 [P] `ExitCodes` (0 OK, 1 section failed, 2 usage/config/credentials); `M/cli/ExitCodes.java`
- [X] T008 `MorningCommand` top command with global options `--config`, `--section` (repeatable), `--format=tty|md|json`, `--width`, `--no-llm`, `-h`, `-V`; no subcommand runs `all`; `M/cli/MorningCommand.java`

**Checkpoint**: `morning --help` prints the options.

---

## Phase 3: User Story 3 – Configure the routine (P1)

**Goal**: load and validate `config.yaml` into typed sections before any API call.

**Independent test**: `examples/zakkak.yaml` loads; unknown type, missing field, unknown
key, org overlap and secret-looking value each give a `ConfigError` with its path.

### Tests (write first)

- [X] T009 [P] [US3] Config fixtures: valid minimal, unknown type, unknown key, missing `repo` in `ci-status`, include/exclude overlap, bad org name, secret value (`ghp_` + 36 chars), duplicate section names, empty `sections`; `src/test/resources/config/*.yaml`
- [X] T010 [P] [US3] `ConfigLoaderTest`: `examples/zakkak.yaml` parses; each invalid fixture gives the expected path (e.g. `morning.sections[1].include-orgs`); secret value never appears in the message; missing file gives "no configuration found"; `T/config/ConfigLoaderTest.java`

### Implementation

- [X] T011 [P] [US3] `Section` sealed interface with Jackson `@JsonTypeInfo(use = NAME, property = "type")` / `@JsonSubTypes` (`my-prs`, `ci-status`, `review-candidates`, `summary`); `M/config/Section.java`
- [X] T012 [P] [US3] `MyPrsSection(name, includeOrgs, excludeOrgs)`: default name "my-prs"; each org matches `^[A-Za-z0-9-]{1,39}$`; "include-orgs and exclude-orgs must not overlap"; `M/config/MyPrsSection.java`
- [X] T013 [P] [US3] `CiStatusSection` (`repo` required, `branches`, `workflows`, `events`, `include-fork-prs`, `tracking{strategy, issue-keys}`, `related-repos`) and `ReviewCandidatesSection` (`repo` required, `labels`, `paths`, `title-keywords`, `weights`, `on-hold-labels`, `related-repos`), structure only; default name type + repo; `M/config/CiStatusSection.java`, `M/config/ReviewCandidatesSection.java`
- [X] T014 [P] [US3] `SummarySection(name, order)` and `LlmSettings(enabled = true, maxCalls = 15)`; `M/config/SummarySection.java`, `M/config/LlmSettings.java`
- [X] T015 [US3] `MorningConfig(sections, llm)`: at least 1 section, names unique; `M/config/MorningConfig.java`
- [X] T016 [P] [US3] `SecretPatterns` (`gh[pousr]_[A-Za-z0-9]{36,}`, `github_pat_[A-Za-z0-9_]{20,}`, `sk-ant-[A-Za-z0-9_-]{20,}`); `M/config/SecretPatterns.java`
- [X] T017 [US3] `ConfigLoader`: resolve path (`--config`, else `$XDG_CONFIG_HOME/morning/config.yaml`, else `~/.config/morning/config.yaml`); Jackson `YAMLMapper` with `FAIL_ON_UNKNOWN_PROPERTIES`, kebab-case names; Bean Validation; secret scan over the YAML tree; map every failure to `ConfigError(path, message)`; `M/config/ConfigLoader.java`, `M/config/ConfigError.java`

**Checkpoint**: `ConfigLoaderTest` green.

---

## Phase 4: User Story 1 – My PRs in priority order (P1) 🎯 MVP

**Goal**: `morning prs` prints one sorted table of my open PRs, with at most 3 GraphQL
requests and no LLM.

**Independent test**: WireMock fixtures with a changes-requested PR, a failing PR
(fails on base / passes on base / flaky), a green PR and a draft; assert rows, order,
classifications, `apiRequests <= 3`, token never printed.

### GitHub access

- [X] T018 [P] [US1] `GitHubCredentials(token)` with `toString()` = `GitHubCredentials[***]`; `TokenResolver`: `GITHUB_TOKEN`, then `GH_TOKEN`, then `gh auth token` (ProcessBuilder, 10 s timeout, stdout trimmed); failure → credential error, exit 2; `M/github/GitHubCredentials.java`, `M/github/TokenResolver.java`
- [X] T019 [P] [US1] `TokenResolverTest` (env precedence, `gh` missing → error, toString masked); `T/github/TokenResolverTest.java`
- [X] T020 [P] [US1] GraphQL documents: `my-prs.graphql` (R3), `pr-checks-page.graphql`, `base-and-attempts.graphql` (R4+R5; static, node IDs as variables); `src/main/resources/graphql/`
- [X] T021 [P] [US1] Vendor GitHub's public schema and record its date; `src/test/resources/github/schema.docs.graphql`, `src/test/resources/github/SCHEMA_VERSION`
- [X] T022 [US1] `GraphQLDocumentsTest`: every document parses, contains only `query` operations, and validates against the vendored schema; `T/github/GraphQLDocumentsTest.java`
- [X] T023 [US1] `Query` enum of documents and `GraphQLQueries`: load by enum only, reject `mutation`/`subscription` (comment-stripped), count requests, map GraphQL errors to a section failure; `GraphQLClientProducer` builds `DynamicGraphQLClient` with `Authorization: Bearer` from `GitHubCredentials`; `M/github/Query.java`, `M/github/GraphQLQueries.java`, `M/github/GraphQLClientProducer.java`
- [X] T024 [P] [US1] `GraphQLQueriesTest`: mutation and subscription documents are rejected; `T/github/GraphQLQueriesTest.java`

### Analysis

- [X] T025 [P] [US1] Records: `PullRequestNode`, `CheckContext` (outcome mapping: CheckRun FAILURE, TIMED_OUT, CANCELLED, STARTUP_FAILURE, ACTION_REQUIRED → FAILURE; StatusContext FAILURE, ERROR → FAILURE; no conclusion and status != COMPLETED → PENDING), `BaseHistory`, `BaseCommit`, `CheckAttempts`, `Mergeable`, `ReviewDecision`; `M/prs/*.java`
- [X] T026 [P] [US1] `FailureClass`, `CheckFailure`, `PrGroup`, `Action`, `PullRequestStatus`, `MyPrsReport`; `M/prs/*.java`
- [X] T027 [P] [US1] `FailureClassifierTest`: fails on most recent base commit with the same name → UNRELATED_FAILS_ON_BASE; passed there → NOT_ANALYSED; no base counterpart → NOT_ANALYSED; earlier attempt passed → LIKELY_FLAKY; base rule wins over flaky; `T/prs/FailureClassifierTest.java`
- [X] T028 [P] [US1] `PrSorterTest`: groups CHANGES_REQUESTED, CI_FAILURE (LIKELY_FLAKY counts), OTHER; oldest `updatedAt` first; action derivation; `T/prs/PrSorterTest.java`
- [X] T029 [US1] `FailureClassifier` and `PrSorter` (groups, actions, sort); `M/prs/FailureClassifier.java`, `M/prs/PrSorter.java`
- [X] T030 [US1] `MyPrsFetcher`: search with `org:`/`-org:` qualifiers, then paging only when `hasNextPage`, then `base-and-attempts` only when a check fails; returns `MyPrsReport` with `apiRequests`; `M/prs/MyPrsFetcher.java`

### Rendering

- [X] T031 [P] [US1] `TableTest`: wraps to width, rows grow, nothing truncated, OSC-8 sequences don't count toward width; `T/render/TableTest.java`
- [X] T032 [US1] `Table` (column widths, word wrap, hard-break long words) and `TerminalInfo` (`--width`, `$COLUMNS`, `stty size` on `/dev/tty` with 500 ms timeout, 120; TTY via `System.console().isTerminal()`); `M/render/Table.java`, `M/render/TerminalInfo.java`
- [X] T033 [US1] `TtyRenderer` (OSC-8 links), `MarkdownRenderer`, `JsonRenderer` (schema `morning.my-prs/1`, contracts/output-json.md); "No open PRs" when empty; `M/render/*.java`

### CLI and end-to-end

- [X] T034 [US1] Hand-write fixtures in the recorded shape for search, base-and-attempts and paging; `src/test/resources/fixtures/my-prs/*.json`
- [X] T035 [US1] `PrsCommand` and `AllCommand` (001: my-prs only, notes "ci, reviews, summary: not implemented yet"); `--section` filter; unsupported section types → "not supported yet (planned in feature NNN)"; exit codes and messages per contracts/cli.md; `M/cli/PrsCommand.java`, `M/cli/AllCommand.java`
- [X] T036 [US1] `PrsCommandTest` (`@QuarkusMainTest` + WireMock): US1 scenarios 1–4, US3 scenarios 1–4 (no GitHub request on config error), "No open PRs", `--format md|json`, SC-001 (`apiRequests <= 3`), SC-003 (sentinel token absent from stdout, stderr and logs); `T/cli/PrsCommandTest.java`

**Checkpoint**: `./mvnw verify` green; `morning prs` usable.

---

## Phase 5: Polish

- [X] T037 Update `docs/architecture.md` (Implemented column; base lookup per R4), `ROADMAP.md` status, `AGENTS.md` commands and lessons learned
- [X] T038 Manual live run per quickstart.md (read-only; 2026-11-16: 17 PRs, 2 requests)

---

## Dependencies

- Phase 1 → Phase 2 → US3 → US1 → Polish.
- Within US1: T018–T024 (GitHub) and T025–T029 (analysis) and T031–T033 (render) are
  independent of each other; T030 needs T023 and T029; T035–T036 need everything.

## Parallel examples

- US3: T009, T010, T011–T014, T016 together; then T015, T017.
- US1: T018, T020, T021, T025, T026, T027, T028, T031 together.

## Implementation strategy

MVP is the whole of this feature (US3 + US1): neither story is useful alone. Commit locally
after each phase.
