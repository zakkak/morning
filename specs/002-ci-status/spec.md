# Feature Specification: CI status for configured repositories

**Feature Branch**: `002-ci-status`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description, from the original `morning` skill: "Check the CI job results
in graalvm/mandrel and perform a root cause analysis. Let me know if the observed issues
are new or seen in the previous run as well. Look up the issues the CI automation creates
and see if they link to some known issue. One row per run/job, with columns for
previously-unknown issue, tracked issues (named and linked), and whether known issues
are fixed, have open PRs, or need someone."

This is generalised to a `ci-status` section type that works for any configured
repository (ADR-0008). Its scope rules are in ADR-0005. The reference configuration is the
graalvm/mandrel section in `examples/zakkak.yaml`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which CI jobs failed, and whether it is new (Priority: P1)

For each configured `ci-status` section, `morning ci` shows one row per failed job since
that section's last run. Matrix siblings with the same failure collapse into one row.
Each row says whether the failure is new, or was seen in the previous run of the same
(workflow, branch). Fully green workflows appear as a one-line footer.

**Why this priority**: This is the core daily question, and rules alone can answer it.

**Independent Test**: Use the Mandrel reference config with fixtures of two consecutive
runs of one (workflow, branch). In the latest run, one job fails again, one job fails for
the first time, and one job is fixed.

**Acceptance Scenarios**:

1. **Given** a job that failed in both the latest and the previous run of its (workflow,
   branch), **When** I run `morning ci`, **Then** the row is marked "seen before".
2. **Given** a job that fails only in the latest run, **Then** it is marked "new".
3. **Given** the same failure signature on two branches, or in two configured sections,
   **Then** it is analysed once and both rows reference the same result.
4. **Given** a run of a PR from a fork and `include-fork-prs` unset, **Then** the run is
   ignored.
5. **Given** `branches`, `workflows` or `events` filters in a section, **Then** only
   matching runs are considered.
6. **Given** no previous run of the section, **Then** the window is the last 24h.
   `--since` overrides the window.

---

### User Story 2 - See tracked and known issues and their status (Priority: P2)

For sections whose tracking strategy is `workflow-inputs`, each failed job links its
tracking issue. Each known issue referenced from that tracking issue (in comments or as a
cross-reference) is shown with a status: Fixed, Fix in progress, Being worked on, or Needs
owner. Sections with strategy `none` show no tracked issue.

**Why this priority**: It tells me what needs someone, but it builds on US1's rows.

**Independent Test**: Use a fixture tracking issue that references one closed issue, one
issue with an open PR, and one unassigned issue with no PR.

**Acceptance Scenarios**:

1. **Given** a referenced issue that is closed as completed, or whose closing PR is
   merged, **Then** its status is "Fixed".
2. **Given** an open referenced issue with an open PR that references or closes it,
   **Then** its status is "Fix in progress".
3. **Given** an open, unassigned referenced issue with no PR, **Then** its status is
   "Needs owner".

---

### User Story 3 - Get a root cause for failures nobody has seen yet (Priority: P3)

For failures whose signature matches nothing known, an LLM proposes a root cause (3
sentences at most), says whether the failure matches one of the candidate known issues,
and gives its confidence. It may read more log lines, and issues and files from the
section's `repo` and `related-repos`, through read-only tools.

**Why this priority**: This is the only part that needs an LLM, and it builds on US1 and
US2.

**Independent Test**: With mocked AI services, an unmatched signature gets exactly one
call. The same signature on a later run is served from the cache.

**Acceptance Scenarios**:

1. **Given** an unmatched signature, **When** I run `morning ci`, **Then** one LLM call
   is made and the row shows the root cause, any matched issue, and the confidence.
2. **Given** an LLM tool call for a job ID not fetched in this run, or for a repository
   outside the section's `repo` and `related-repos`, **Then** the call is refused and
   nothing is fetched.
3. **Given** `--no-llm`, **Then** the root cause column shows "not analysed".

### Edge Cases

- `workflow-inputs` strategy, but the workflow YAML has none of the configured keys and
  the job logs have none either: the tracked issue is shown as "none".
- A run is still in progress at the end of the window: the latest *completed* run is used.
- A job log has expired (GitHub retention) or is too large: a cached excerpt is used if
  there is one; otherwise the row is marked "log unavailable".
- The previous run of a group is weeks old (Weekly, Bi-weekly): it is still used for the
  comparison.
- The config names a repository the token cannot read: the section reports an error, and
  the other sections still run.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For each `ci-status` section, the system MUST consider every completed
  workflow run of the configured repository within the window, from the same repository
  unless `include-fork-prs` is set, after applying the optional `branches`, `workflows`
  and `events` filters.
- **FR-002**: The system MUST group runs by (workflow, branch), and compare each group's
  latest completed run with the previous completed run of the same group.
- **FR-003**: With the `workflow-inputs` strategy, the system MUST derive tracking issues
  from the configured workflow input keys. Jobs MUST be mapped to issues with the same
  job-name prefix rules as Mandrel's `quarkus-ecosystem-issue.java`.
- **FR-004**: The system MUST classify each referenced known issue as Fixed, Fix in
  progress, Being worked on or Needs owner. [NEEDS CLARIFICATION: are issue state,
  assignees and linked PRs enough, or should label conventions (for example `blocked`, or
  upstream labels) also count, and should those be configurable per section?]
- **FR-005**: The system MUST compute a normalised failure signature for each failed job,
  and match it deterministically before any LLM use.
- **FR-006**: The system MUST make at most one LLM call per unmatched signature, and cache
  the result by signature hash.
- **FR-007**: Output MUST be grouped by section, with the columns Workflow/branch/run,
  Event, Job, New?, Tracked/known issues, Known issue status, and Root cause. Links MUST
  be clickable.

### Key Entities

- **CiStatusSection** (config): name, repo, filters, `include-fork-prs`, tracking
  strategy and issue keys, `related-repos`.
- **RunGroup**: section, workflow, branch, latest run, previous run.
- **JobFailure**: job, run, signature hash, new or seen before, tracking issue.
- **KnownIssue**: reference, title, state, assignees, linked PRs, status.
- **FailureSignature**: hash, excerpt, jobs where seen, matched issue, verdict.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Measured with the graalvm/mandrel reference section:

- **SC-001**: A failure that recurs on consecutive days causes no LLM calls after its
  first analysis.
- **SC-002**: Known-issue status lookups take at most 2 GitHub GraphQL requests per
  section per run, however many issues are referenced.
- **SC-003**: `morning ci --no-llm` completes in under a minute on a typical day (about
  10 runs in the window).

## Assumptions

- "Previous run" means the previous completed run of the same (workflow, branch),
  whatever event started it.
- Only GitHub Actions is supported. External CI systems reporting through statuses are
  out of scope for this section type.
