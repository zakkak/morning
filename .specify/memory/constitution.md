# morning Constitution

`morning` is a command-line tool that runs a configurable daily developer routine on
GitHub: the user's open PRs, CI status of configured repositories, PRs matching the
user's review interests, and a prioritised summary. It does this deterministically, and
calls an LLM only where judgement is needed.

## Core Principles

### I. Deterministic First

Anything that can be expressed as a rule (an API query, set difference, sort order,
regex, scoring formula) MUST be implemented as code. An LLM MAY be used only for steps
that need judgement, such as root-cause analysis, deciding whether a failure relates
to a change, or writing a prioritised summary. Each LLM use MUST be named in the
feature's spec, with the reason a rule cannot do the job.

### II. LLM Isolation (NON-NEGOTIABLE)

- The LLM MUST NOT receive credentials of any kind: no GitHub token, no LLM provider
  key, no other service secret.
- The LLM MUST receive only data the program has already fetched, or data it fetches
  through program-owned tools.
- LLM tools MUST be read-only, limited to allowlisted repositories and to IDs fetched in
  the current run, capped in output size, and subject to a per-analysis and per-run call
  budget.
- Every string sent to the LLM MUST pass through the redaction filter.
- All fetched text (CI logs, issue and PR bodies, comments) is untrusted input. Prompts
  MUST say so, and LLM verdicts MUST be shown with their confidence level.
- Each LLM call MUST be an independent, single-purpose invocation with structured
  output (a Java record). There is no long-running agent session.

### III. Read-Only Toward External Services (NON-NEGOTIABLE)

The program MUST NOT write to GitHub or any other external service: no comments,
labels, reviews, reruns, issue changes or posts. This is enforced in code:

- hub4j mutator methods are never called (an ArchUnit test checks this).
- GraphQL runs only named, checked-in query documents. Mutations and subscriptions are
  rejected, and LLM-chosen values are passed only as validated variables.
- Only the gateway and fetcher classes may depend on the GitHub clients (an ArchUnit
  test checks this).

### IV. Token Frugality

- Prompts MUST carry compact structured data and extracted excerpts, never raw logs or
  full diffs by default.
- LLM verdicts MUST be cached by content hash, so the same input is never analysed twice.
- The program MUST report token usage per run.

### V. Works Offline Without an LLM

- Every command MUST support `--no-llm` and still produce complete tables, with LLM
  columns marked "not analysed".
- Tests MUST NOT touch the network. GitHub interactions use recorded WireMock fixtures,
  and AI services are mocked.
- Live tests are opt-in only (`-Dmorning.live=true`).

### VI. Simplicity and Reuse

- Prefer the boring, obvious solution.
- Before writing code, check whether a well-supported, actively maintained library or
  existing project code already does the job, and reuse it.
- Dependencies MUST be the latest stable release, actively maintained (a commit within
  the last 6 months), and under an Apache-2.0, MIT or BSD licence. Any exception MUST be
  recorded in an ADR.
- Touch only what the current task requires.

## Technical and Security Constraints

- **Stack**: Java 25, Quarkus (latest stable), `quarkus-picocli`, `quarkus-langchain4j`,
  and Maven. See ADR-0002.
- **GitHub access**: GraphQL by default through `quarkus-smallrye-graphql-client`.
  hub4j (`org.kohsuke:github-api`) is used only for what GraphQL does not expose:
  Actions runs, jobs and logs, and PR diffs. See ADR-0004.
- **Configuration**: a YAML routine of built-in section types, loaded into typed records with
  Jackson YAML and Bean Validation (ADR-0008).
  - Configuration MUST NOT be a source of secrets.
  - It MAY widen **read** scope only: which repositories are fetched, and which ones
    LLM tools may read (`repo` plus `related-repos`).
  - It MUST NOT enable any write, or relax Principles II and III.
- **LLM provider**: access goes through LangChain4j abstractions and named models
  (`fast`, `deep`) only; no provider-specific code outside configuration. See ADR-0006.
- **Secrets**:
  - Read only from environment variables, or from `gh auth token` for GitHub.
  - Never hardcoded, never committed, never logged, never placed in URLs.
  - Each secret is injected only into the client that needs it.
- **TLS**: certificate verification MUST never be disabled.
- **Logging**: structured, with no request headers and no secret values.
- **No network listeners**: the CLI does not bind ports.

## Development Workflow

- **Spec Kit flow** (agent-neutral skill names; see AGENTS.md for invocation syntax):
  1. `speckit-specify`
  2. `speckit-clarify`, if any `[NEEDS CLARIFICATION]` markers remain
  3. `speckit-plan`
  4. `speckit-tasks`
  5. `speckit-analyze`
  6. `speckit-implement`
- **Human gates**: the maintainer approves the spec, then the plan, and reviews the
  change before merge.
- **Decisions**: any cross-cutting or hard-to-reverse decision is recorded as an ADR in
  `docs/adr/`. ADRs are append-only: supersede them, never rewrite them.
- **Definition of done**:
  - tests pass, including the ArchUnit and GraphQL schema checks;
  - `docs/architecture.md` reflects the change;
  - `ROADMAP.md` status is updated;
  - new lessons are added to `AGENTS.md`.
- **Commits**:
  - Use the `Assisted-By: <Tool> <Model>` trailer for LLM-assisted commits.
  - Agents MAY commit locally.
  - Agents MUST NOT push, open PRs or issues, or comment anywhere unless the maintainer
    explicitly asks.

## Governance

- This constitution overrides all other project guidance. `AGENTS.md` holds operational
  guidance (commands, conventions) and MUST NOT contradict it.
- Every plan's Constitution Check MUST evaluate Principles I–VI and the constraints
  above. A justified exception goes in the plan's Complexity Tracking table, and in an
  ADR if it lasts beyond that feature.
- Amendments are proposed as a change to this file with a version bump:
  - MAJOR: a principle is removed or redefined.
  - MINOR: a principle or section is added.
  - PATCH: clarifications.
  Amendments require the maintainer's approval.

**Version**: 1.0.0 | **Ratified**: 2026-11-13 | **Last Amended**: 2026-11-13
