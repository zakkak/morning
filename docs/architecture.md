# Architecture

> **Status: target design, partly implemented.** Feature 001 (US1, US3) is implemented:
> see the "Implemented" column. Each feature updates this file when it lands, so the
> column always reflects the code. Reasons for
> decisions live in [ADRs](adr/). Binding rules live in the
> [constitution](../.specify/memory/constitution.md).

## Overview

```
morning <command>            config.yaml ──► MorningConfig (Jackson YAML records) ──► sections
   │
   ▼  (for each configured section of the command's type)
fetch (deterministic) ──► analyse (rules + optional isolated LLM calls) ──► render
   │                          │                                               │
   ├─ GraphQLQueries          ├─ rule engines per command                     ├─ tty (wrapped tables, OSC-8 links)
   └─ hub4j (Actions, diffs)  └─ AI services (fast/deep) + LlmToolbox         ├─ md
                                   └─ ReadOnlyGitHubGateway                    └─ json
```

Every command runs the configured sections of its type (ADR-0008). Each section is a
pipeline that produces plain Java records. `summary` reads the records saved by the
other sections, so it does not fetch again.

| Component | Responsibility | ADR | Implemented |
|---|---|---|---|
| Picocli commands | `all`, `ci`, `prs`, `reviews`, `summary`; common options | 0002 | `all`, `prs` and the options `--config`, `--section`, `--format`, `--width`, `--no-llm` (001) |
| `MorningConfig` | Jackson YAML into records, polymorphic on `type`, with Bean Validation and a secret-value check; validates before any API call | 0008 | yes; all four types parse, only `my-prs` runs (001) |
| `GitHubCredentials` | Token from `GITHUB_TOKEN`, then `GH_TOKEN`, then `gh auth token`; injected only into the two GitHub clients | 0003 | yes, GraphQL client only (001) |
| `GraphQLQueries` | Runs named `src/main/resources/graphql/*.graphql` documents; rejects mutations and subscriptions | 0004 | yes (001) |
| hub4j fetchers | Actions runs, jobs and logs; PR diffs | 0004 | no |
| `ReadOnlyGitHubGateway` + `LlmToolbox` | The LLM's read-only tools, with allowlists, caps and budgets | 0003 | no |
| AI services | `@RegisterAiService(modelName = "fast" \| "deep")`, structured outputs | 0006 | no |
| Redaction filter | Strips token-like strings from any text sent to the LLM | 0003 | no |
| Cache | `~/.cache/morning/`, keyed per section | 0008 | no |
| Table renderer | Wraps cells to the terminal width; OSC-8 links on a TTY, markdown links with `--format md` | — | yes (001) |

## CLI

```
morning                 # alias for `morning all`
morning all             # every configured section, in config order, then summary
morning ci              # ci-status sections                 (specs/002-ci-status)
morning prs             # my-prs sections                    (specs/001-my-prs)
morning reviews         # review-candidates sections         (specs/003-review-candidates)
morning summary         # summary section                    (specs/004-daily-summary)

Common options:
  --config=<path>       default ~/.config/morning/config.yaml
  --section=<name>      run only the named section(s); repeatable
  --no-llm              skip all LLM calls
  --format=tty|md|json  default tty when stdout is a TTY
  --width=N             override terminal width
  --refresh             ignore cache
  --max-llm-calls=N     hard budget per run (default 15)
  --since=<time>        (ci) override the run window
  --explain             (reviews) one-line LLM note per top PR
```

## GitHub access (ADR-0004)

**GraphQL by default. hub4j only for what GraphQL does not expose.**

| Data | Client | How |
|---|---|---|
| My open PRs, reviews, checks | GraphQL | `my-prs.graphql`: `search` (org filters as `org:`/`-org:` qualifiers), `reviewDecision`, the head commit's `statusCheckRollup`. `contexts` are paged with `pr-checks-page.graphql` when there are more than 100 checks. |
| Base-branch checks and earlier attempts | GraphQL | `base-and-attempts.graphql`, one request, only when some check fails: `nodes(ids: $refIds)` → the last 30 base commits with their checks; `nodes(ids: $prIds)` → `checkSuites { checkRuns(filterBy: {checkType: ALL}) }` on each failing PR's head commit. |
| Tracking-issue comments and timeline | GraphQL | `tracking-issues.graphql`, one alias per issue: comments plus `timelineItems(itemTypes: [CROSS_REFERENCED_EVENT, CONNECTED_EVENT])` |
| Linked issue status | GraphQL | `issue-status.graphql`, one alias per issue: `state`, `stateReason`, `assignees`, `closedByPullRequestsReferences` |
| Workflow YAML at a SHA | GraphQL | `object(expression: "<sha>:<path>") { ... on Blob { text } }` |
| Review candidates (per configured repo) | GraphQL | `reviews-candidates.graphql`, 100 per page: labels, `files{path}`, size, `mergeable`, `reviewDecision`, `reviewRequests`, my reviews, rollup state |
| Workflow runs (filtered), jobs, job logs | hub4j | `GHRepository.queryWorkflowRuns()`, `GHWorkflowRun.listJobs()`, `GHWorkflowJob.downloadLogs()` |
| PR file diff | hub4j | `GHPullRequest.listFiles()` → `getPatch()` |

## LLM boundary (ADR-0003, ADR-0006)

- **Calls.** Each call is independent, single-purpose and returns a structured record:

  | Call | Model | Input | Output |
  |---|---|---|---|
  | CI root cause, for unmatched signatures only | `deep` | job and workflow names, log excerpt (about 8k tokens or less), candidate known issues (number, title, state, two-line summary) | `{ matchedIssue, isNew, rootCause (3 sentences at most), confidence }` |
  | PR failure relatedness, for checks the rules leave unclassified | `fast` | changed file list with line counts, failure excerpt | `{ related: yes\|no\|unsure, reason }` |
  | Review explanation (`--explain`) | `fast` | title, body excerpt, relevant paths | one line |
  | Summary | `deep` | compact JSON of all results (about 3k tokens) | ordered `{ item, why, link }` |

- **Tools** (`LlmToolbox` → `ReadOnlyGitHubGateway`):

  | Tool | Backend | Allowlist |
  |---|---|---|
  | `moreLog(jobId, fromLine, toLine)` | hub4j | job IDs fetched in this run |
  | `getPrDiff(prId, path)` | hub4j | the PR under analysis |
  | `getIssue(repo, number)`, `searchIssues(repo, query)`, `getFile(repo, sha, path)` | GraphQL named queries; values passed as variables | the section's `repo` plus its `related-repos` (CI, reviews); the PR's own repo (PRs) |

- **Limits.** At most 5 tool calls per analysis, plus the global `--max-llm-calls` budget.
  Over budget, a tool returns "budget exhausted". Output is capped and redacted.
- **Configuration.**
  ```properties
  quarkus.langchain4j.fast.chat-model.provider=anthropic
  quarkus.langchain4j.anthropic.fast.chat-model.model-name=claude-haiku-5-5
  quarkus.langchain4j.deep.chat-model.provider=anthropic
  quarkus.langchain4j.anthropic.deep.chat-model.model-name=claude-sonnet-5-5
  ```
  The LLM key is read from `QUARKUS_LANGCHAIN4J_ANTHROPIC_API_KEY` only.

## Configuration (ADR-0008)

`~/.config/morning/config.yaml`, or `--config`. It holds no secrets. The maintainer's
routine is in `examples/zakkak.yaml`.

| Section type | Key fields |
|---|---|
| `my-prs` | `include-orgs`, `exclude-orgs` |
| `ci-status` | `repo`, `branches`, `workflows`, `events`, `include-fork-prs`, `tracking.strategy` (`none` \| `workflow-inputs`), `tracking.issue-keys`, `related-repos` |
| `review-candidates` | `repo`, `labels`, `paths`, `title-keywords`, `weights`, `related-repos` |
| `summary` | `order` |
| top level `llm` | `max-calls`, `enabled` |

Every section also has an optional `name`, which defaults to the type plus the repo.
Section names key the cache and `--section`.

## CI status analysis (ADR-0005)

The `workflow-inputs` tracking strategy generalises graalvm/mandrel's convention:

- `github-issue-updater.yml` runs `.github/quarkus-ecosystem-issue.java` after the
  Nightly, Weekly, Bi-weekly and Repo Sync workflows. It comments on long-lived tracking
  issues (for example `issue-number: "898"` and `mandrel-it-issue-number: "366"` in
  `nightly.yml`), reopens them on failure and closes them on success.
- The updater maps jobs to issues by job-name prefix, using the `" / "` delimiter. Port
  that logic and its `processFailedJob` context-window approach rather than reinventing
  them.

The pipeline, per `ci-status` section:

1. **Window.** List runs since the section's last successful run (24h if none, or
   `--since`). Drop runs from forks unless `include-fork-prs` is set, then apply the
   `branches`, `workflows` and `events` filters.
2. **Group** by (workflow, branch). Take the latest completed run in the window and the
   previous completed run of the same group.
3. **Jobs.** A job has failed if its conclusion is `failure`, `timed_out` or `cancelled`.
   Fully green groups go to the footer.
4. **New vs seen before.** Compare failed job names with the previous run, after
   normalising matrix names.
5. **Tracking issues** (`workflow-inputs` strategy only). Read the configured
   `issue-keys` and `issue-repo` from the workflow YAML at `head_sha`, falling back to the
   job logs. Map jobs to issues with the updater's prefix logic.
6. **Known issues.** Run `tracking-issues.graphql`, extract `owner/repo#N` references and
   URLs, then run `issue-status.graphql` and classify each issue as Fixed, Fix in
   progress, Being worked on or Needs owner.
7. **Signature.** Cut a log excerpt from `##[error]`, Maven `[ERROR]`,
   `Tests run:.*Failures: [1-9]`, `FAILED`, exception headlines and `Caused by:` lines,
   with context. Normalise it into failing test IDs, exception class and first frame,
   with timestamps, hashes, ports and paths stripped. Hash the result.
8. **Deterministic match.** A matching signature hash in the previous run, in any other
   group, or in the signature cache attaches the known issue. No LLM call is made.
9. **LLM root cause.** Only for unmatched signatures, one call per signature.

## Cache (`~/.cache/morning/`)

| Path | Content |
|---|---|
| `logs/{jobId}.txt` | raw job logs (immutable once the job completes) |
| `signatures/{hash}.json` | excerpt, jobs it was seen in, matched known issue |
| `llm/{kind}/{inputHash}.json` | LLM verdicts |
| `runs/{date}.json` | structured output of `all`, per section; input to `summary` |
| `state.json` | timestamp of each section's last successful run |

Entries older than 30 days are pruned on startup.

## Rendering

The table renderer is written in-house, about 150 lines, because the usual Java ASCII-table
libraries are unmaintained. It wraps cells to `--width` and grows rows vertically. It
emits OSC-8 hyperlinks on a TTY and markdown links with `--format md`. `--format json`
dumps the records.

## My-PRs analysis (spec 001)

1. **Fetch** (at most 2 requests, plus 1 per extra page of checks): search, then paging,
   then base-and-attempts if anything fails.
2. **Classify** each failing check, first match wins:
   - *unrelated (fails on base)*: the most recent of the last 30 base commits that
     finished a check with the same name failed it;
   - *likely flaky*: an earlier attempt with that name on the head commit passed;
   - otherwise *not analysed* (US2's LLM step will refine this later).
3. **Group and sort**: changes requested, then CI failures not classified as unrelated,
   then the rest; oldest update first within a group. Derive actions.
4. **Render**: tty (box table, OSC-8 links), md, or json (`morning.my-prs/1`).

## Test strategy

- `@QuarkusTest` for beans, `@QuarkusMainTest` for the CLI end to end. GitHub is a local
  WireMock (`wiremock-standalone`) with hand-written fixtures in the recorded response
  shape; GraphQL requests are matched on `operationName`. hub4j's own WireMock recording
  approach is reused for Actions.
- Surefire sets `GITHUB_TOKEN` to a sentinel; tests assert it appears only in the
  `Authorization` header.
- AI services are mocked. Live tests are opt-in only (`-Dmorning.live=true`).
- ArchUnit (`ReadOnlyBoundaryTest`):
  - the SmallRye GraphQL client is confined to `GraphQLQueries` and its producer;
  - only the client producer reads `GitHubCredentials.token()`;
  - `org.kohsuke.github` is confined to `…github.actions` (no classes yet);
  - no other package uses an HTTP client;
  - no hub4j mutator is called (added with the first hub4j class, 002).
- A GraphQL schema test validates every `.graphql` file against a vendored
  `schema.docs.graphql`.
