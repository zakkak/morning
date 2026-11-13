# Feature Specification: My open PRs status

**Feature Branch**: `001-my-prs`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Check my open GitHub PRs (in all repos) for required actions.
Check if CI runs have completed and if any failures are related to my changes. Sort: 1. PRs
with a review requesting changes, 2. PRs with CI failures I should take care of first,
3. the rest." (from the original `morning` skill)

> First feature. Its plan also sets up the project skeleton: Maven, Quarkus and Picocli;
> credentials; YAML configuration with typed sections (ADR-0008); both GitHub clients with
> their read-only wrappers; the table renderer; the cache directory; and the ArchUnit and
> GraphQL schema tests. See `docs/architecture.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which of my PRs need action, in priority order (Priority: P1)

Each morning I run `morning prs` and get one table of my open PRs across all
repositories, optionally limited by the `my-prs` section's org filters. It shows review state, CI state and merge conflicts, sorted so the PRs
that need me come first.

**Why this priority**: This is the first daily-usable command, and it needs no LLM.

**Independent Test**: Run against recorded fixtures covering a PR with changes requested,
a PR with failing CI, a green PR and a draft. Check the rows and their order.

**Acceptance Scenarios**:

1. **Given** a PR whose review decision is "changes requested", **When** I run
   `morning prs`, **Then** it appears in the first group, with action "address review".
2. **Given** a PR with a failing check that also failed on the most recent base-branch
   commit that ran a check with the same name (within the last 30 commits), **When** I run
   `morning prs`, **Then** the failure is marked "unrelated (fails on base)" and does not
   put the PR in the CI-failure group.
3. **Given** a PR with a failing check that passed on that most recent base-branch commit,
   or that has no base-branch counterpart, **When** I run
   `morning prs`, **Then** the PR is in the second group, with the failing check named.
4. **Given** a failing check whose earlier attempt on the same commit passed, **When** I
   run `morning prs`, **Then** it is marked "likely flaky".
5. **Given** any table output, **When** the terminal is narrow, **Then** cells wrap and
   rows grow taller, and nothing is truncated.

---

### User Story 2 - Know whether a CI failure is caused by my change (Priority: P3)

For failures the rules cannot classify, an LLM judges whether the failure relates to my
change. It is given the changed file list and the failure excerpt, and it may pull one
file's diff.

**Why this priority**: It adds judgement on top of US1, which already works without it.

**Independent Test**: With mocked AI services, an unclassified failure gets a verdict of
"related", "not related" or "unsure", with a reason. `--no-llm` shows "not analysed".

**Acceptance Scenarios**:

1. **Given** an unclassified failing check, **When** I run `morning prs`, **Then** exactly
   one LLM call is made for it and the verdict is shown with its reason.
2. **Given** the same head SHA and check on a later run, **When** I run `morning prs`,
   **Then** the cached verdict is used and no LLM call is made.

### User Story 3 - Configure the routine (Priority: P1)

I describe my routine in `~/.config/morning/config.yaml`, or pass `--config`. Invalid
config is reported before any API call, naming the section and field.

**Why this priority**: Every section type depends on it. This feature introduces it.

**Independent Test**: Load `examples/zakkak.yaml` plus a set of invalid configs: unknown
section type, missing `repo`, a secret-looking value.

**Acceptance Scenarios**:

1. **Given** no config file and no `--config`, **When** I run any command, **Then** it
   exits non-zero with a message pointing to `examples/`.
2. **Given** a config with an unknown section type or a missing required field, **Then**
   the error names the section and the field, and no GitHub request is made.
3. **Given** `include-orgs` or `exclude-orgs` in the `my-prs` section, **Then** only PRs
   from matching orgs are listed.
4. **Given** `--section <name>`, **Then** only that section runs.

### Edge Cases

- `mergeable` is `UNKNOWN` (GitHub has not computed it yet): show "?", not "conflict".
- A PR has more than 100 checks: all are considered (paging).
- A check is from a non-Actions CI system: only its summary text is available to the LLM.
- No open PRs: print "No open PRs" and exit 0.
- GitHub token missing or invalid: clear error message, non-zero exit code, token never
  printed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST list all open, non-archived PRs authored by the
  authenticated user across all repositories, filtered by the section's optional
  `include-orgs` and `exclude-orgs`.
- **FR-002**: For each PR, the system MUST show the review decision, CI state, the names
  of failing checks, merge-conflict state and draft state.
- **FR-003**: The system MUST classify each failing check as "unrelated (fails on base)",
  "likely flaky", or unclassified, using deterministic rules only.
- **FR-004**: The system MUST sort PRs as: changes requested; then failing checks that are
  not classified as unrelated; then the rest. Within each group, the oldest update comes
  first.
- **FR-005**: The system MUST support `--format tty|md|json`, `--width` and `--no-llm`.
- **FR-006**: With an LLM enabled, the system MUST request a relatedness verdict only for
  unclassified failing checks, and cache it by (head SHA, check name).
- **FR-007**: The system MUST NOT write to GitHub (constitution Principle III).
- **FR-008**: The system MUST load configuration from `--config`, or from
  `~/.config/morning/config.yaml`, into typed sections, and validate it before any API
  call (ADR-0008). Configuration MUST NOT be a source of secrets.

### Key Entities

- **PullRequestStatus**: repository, number, title, URL, draft, review decision,
  mergeable, CI state, failing checks, action needed.
- **MorningConfig**: the list of sections (type, name, type-specific fields) and the
  `llm` settings.
- **CheckFailure**: check name, CI system, classification, verdict (optional), excerpt
  reference.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With 50 open PRs or fewer, `morning prs --no-llm` uses at most 3 GitHub API
  requests in a typical run, plus 1 per PR with more than 100 checks.
- **SC-002**: The output order matches FR-004 for every fixture scenario.
- **SC-003**: No GitHub token or LLM key value appears in output, logs, or any prompt
  (verified by test).

## Assumptions

- Typically a few dozen open PRs at most.
- The token has read access to all repositories where I have open PRs.
- "Oldest update first" within a group surfaces stale work. Revisit after use.
