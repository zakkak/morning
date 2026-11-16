# Research: 001-my-prs

Phase 0 of `plan.md`. All findings were checked on 2026-11-13 against Maven Central, the
Quarkus 3.40.1 BOM, and GitHub's live GraphQL API (read-only queries).

## R1. Scope of this iteration: US1 and US3 now, US2 deferred

- **Decision:** implement US1 (PR status table) and US3 (configuration). Defer US2 (LLM
  relatedness) until feature 002's US3 has built the LLM boundary (gateway, toolbox,
  redaction, verdict cache), as `ROADMAP.md` already notes.
- **Rationale:** US2 is P3. Building the LLM boundary in two features at once would split
  that work. Without US2, the "failures related to me?" column shows only the rule
  results: "unrelated (fails on base)", "likely flaky", or "not analysed".
- **Consequence:** hub4j and the cache directory have no users in 001, so they arrive with
  002. The ArchUnit rules that confine hub4j are still added now, so the boundary exists
  before the first hub4j class does.
- **Alternatives:** building the whole skeleton (hub4j, cache, LLM) up front was rejected
  as YAGNI. Those parts would be untested until 002.

## R2. Configuration: Jackson YAML into records, not `@ConfigMapping`

- **Decision:**
  - Read the user's routine with `jackson-dataformat-yaml` (managed by the Quarkus BOM,
    2.21.7) into Java records.
  - Sections are polymorphic: `@JsonTypeInfo(use = NAME, property = "type")` with
    `@JsonSubTypes` (`my-prs`, `ci-status`, `review-candidates`, `summary`).
  - Unknown properties are rejected (`FAIL_ON_UNKNOWN_PROPERTIES`) to catch typos.
  - Constraints use Bean Validation (`quarkus-hibernate-validator`).
  - `application.properties` stays for app settings (LLM models and so on), not for the
    user's routine.
- **Rationale:**
  1. SmallRye `@ConfigMapping` has no type-discriminated polymorphism. A list of
     differently-typed sections would need one superset group with every field optional,
     plus hand-written per-type validation.
  2. Quarkus resolves config at startup, before Picocli parses `--config`, so a runtime
     `--config` path would need a pre-parsing `@QuarkusMain` and a custom config source.
  3. Jackson reports errors with a JSON path (for example `morning.sections[2].repo`),
     which satisfies "names the section and field" (spec 001, US3).
- **Alternatives:** `quarkus-config-yaml` with `@ConfigMapping`, as ADR-0008 proposed,
  was rejected for reasons 1 and 2. **ADR-0008 is updated on this branch.**
- **Note:** only the `my-prs` and `summary` section types are implemented here. Other
  types are accepted by the schema but rejected with "not supported yet (feature NNN)",
  so a full example config still parses.

## R3. My-PRs query shape and cost (validated live)

- **Decision:** one query, `my-prs.graphql`:
  - `search(query: "is:pr is:open author:@me archived:false", type: ISSUE, first: 50)`;
  - per PR: `number title url isDraft mergeable reviewDecision updatedAt
    repository{nameWithOwner owner{login}} baseRefName headRefOid`;
  - head commit `statusCheckRollup { state contexts(first: 100) { totalCount pageInfo nodes {
    ... on CheckRun {...} ... on StatusContext {...} } } }`.
  - Org filters are applied as `org:`/`-org:` qualifiers inside the search query. GitHub
    filters server-side, so no extra requests are needed. Checked live during
    implementation: several `org:` are ORed, `-org:` stack, and `org:` also matches
    personal accounts.
- **Evidence:** on the maintainer's account (17 open PRs) the query cost **2 points** and
  returned one page. The largest check list was 72 contexts, under 100.
- **Follow-up paging:** `pr-checks-page.graphql` (by PR node ID, `contexts(after:)`), only
  for PRs where `contexts.pageInfo.hasNextPage` is true.
- **`mergeable` is often `UNKNOWN`** (seen on 9 of 17 PRs), because GitHub computes it
  lazily. It is shown as "?", as the spec already says.

## R4. "Fails on base too": search recent base-branch history

- **Finding:** `baseRef.target.statusCheckRollup` (the base branch **head** commit) was
  non-null for **1 of 17** PRs. Many repositories (quarkusio/quarkus, oracle/graal) don't
  attach checks to every commit on their default branch.
- **Measured:** among the last 30 commits on the base branch, the number with a rollup was:

  | Repository | Commits with a rollup |
  |---|---|
  | quarkusio/quarkus `main` | 17 |
  | oracle/graal `master` | 6 |
  | graalvm/graalvm-community-jdk21u | 21 |
  | Karm/mandrel-integration-tests | 28 |

  Each query cost 1 point.
- **Decision:**
  - A second query, `base-and-attempts.graphql` (merged with R5's attempts lookup), runs
    only when at least one PR has a failing check. It has one alias per distinct (repo, base branch) and fetches
    `history(first: 30) { nodes { oid committedDate statusCheckRollup { contexts(first: 100) {...} } } }`.
  - For each failing check, find the **most recent** base commit whose rollup contains a
    check with the **same name**. If that check failed, mark the PR's failure
    *unrelated (fails on base)*.
  - If no base commit in the last 30 has that check, the result is "not analysed".
- **As built:** instead of one alias per (repo, base branch), the document is static and
  takes the base branches' Ref node IDs (from `baseRef { id }` in the search) as a
  variable: `bases: nodes(ids: $refIds)`. No query text is generated at runtime, which keeps
  Principle III simple. Paging runs *before* this query, because a failing check can be on
  a later page.
- **Rationale:** this widens the rule's coverage from about 6% of PRs to most of them, at
  a cost of one request.
- **Live result (2026-11-16):** none of the 33 failing checks on the maintainer's 6 failing
  PRs had a same-named check in the base history (oracle/graal `master` ran only 3 check
  names in 30 commits). The rule never misfires, but rarely fires; US2 covers the rest.
- **Caveat:** check names sometimes differ between push and pull_request workflows. A
  missing name falls back to "not analysed", never to a wrong "unrelated".
- **Spec impact:** FR-003 and acceptance scenario 2 say "base branch's latest commit". The
  plan reads that as "most recent base commit carrying the same check". SC-001 (3
  requests or fewer) still holds: search, base history, and at most one paging request.

## R5. "Likely flaky"

- **Decision:** part of `base-and-attempts.graphql`, only for PRs with failing CheckRuns,
  one alias per PR. It fetches the head commit's
  `checkSuites { nodes { checkRuns(filterBy: {checkType: ALL}, first: 100) { nodes { name conclusion } } } }`.
  A failing check is *likely flaky* if an earlier run with the same name on the same SHA
  succeeded.
- **Budget:** base history and attempts always travel in one request
  (`attempts: nodes(ids: $prIds)`, as built). Validated live: cost 4 points for 6 PRs and
  3 base branches. This keeps SC-001 at 3 requests or fewer.

## R6. GraphQL client and safety wrapper

- **Decision:** `quarkus-smallrye-graphql-client` (BOM-managed). `DynamicGraphQLClient` is
  built programmatically with `DynamicGraphQLClientBuilder.newBuilder().url(...).header("Authorization", "Bearer " + token)`.
  The token never appears in config files.
- **`GraphQLQueries`:**
  - loads documents from `classpath:graphql/<name>.graphql` by name only (an enum of
    known queries);
  - rejects any document containing a `mutation` or `subscription` operation, using a
    comment-stripped keyword check;
  - passes values only as variables.
- **Schema check:** a test-scope `graphql-java` (BOM-managed, 25.0) parses every document,
  asserts each operation is a `query`, and validates it against a vendored
  `src/test/resources/github/schema.docs.graphql`, taken from GitHub's published public
  schema. The schema file is refreshed manually, with the date recorded in a sibling
  `SCHEMA_VERSION` file.
- **Base URL:** configurable (`morning.github.graphql-url`, default
  `https://api.github.com/graphql`) so tests can point it at WireMock.

## R7. Token resolution

- **Decision:** `GITHUB_TOKEN`, then `GH_TOKEN`, then `gh auth token`. The command runs
  with `ProcessBuilder` and a 10 s timeout, and its stdout is trimmed. Any failure gives
  "GitHub token not found" with the three options, and exit code 2. The token is held in a
  `GitHubCredentials` bean that has no `toString` exposure.
- **Secret-in-config check:** configuration string values are scanned with the same
  token patterns as the future redaction filter (`gh[pousr]_…`, `github_pat_…`,
  `sk-ant-…`). A match is a config error that names the field and never prints the value.

## R8. Testing stack

| Tool | Version | Use |
|---|---|---|
| `quarkus-junit5` | BOM | `@QuarkusTest`, `@QuarkusMainTest` for CLI end-to-end tests |
| `org.wiremock:wiremock-standalone` | **3.13.2** (latest stable; 4.0 is in beta, project active 2026-11-13) | Local stand-in for GitHub; GraphQL stubs matched on `operationName` |
| `com.tngtech.archunit:archunit-junit5` | **1.5.1** (2026-09-25) | Read-only boundary rules |
| `graphql-java` | BOM 25.0, test scope | Schema validation of `.graphql` documents |

**As built:**

- `quarkus-wiremock-test` was dropped. `@QuarkusMainTest` does not inject its
  `@ConnectWireMock` client, so `PrsCommandTest` starts WireMock itself and passes the URL
  as a system property.
- Fixtures are **hand-written in the recorded shape** rather than sanitised recordings.
  The shape was captured from the live API, but real data doesn't cover every scenario
  (for example, no live failure fails on base). They live under
  `src/test/resources/fixtures/my-prs/`, with a scenario table in its README.

## R9. Terminal width and links

- **Width:** `--width`, then `$COLUMNS`, then `stty size` on `/dev/tty` (500 ms timeout),
  then 120. No JLine dependency.
- **TTY detection:** `System.console() != null && System.console().isTerminal()` (Java 22+).
  OSC-8 links are used only on a TTY.
- **Width calculation:** ignore OSC-8 escape sequences. Cell text is treated as
  single-width code points, which is good enough for PR titles; wide characters are a
  known limitation.

## R10. Project coordinates

- `groupId` `net.zakkak`, `artifactId` `morning`, base package `net.zakkak.morning`.
- Fast-jar packaging. A Maven wrapper is generated with `mvn wrapper:wrapper`, so
  `./mvnw` works without a system Maven.
