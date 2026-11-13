# Feature Specification: Review candidates for configured interests

**Feature Branch**: `003-review-candidates`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description, from the original `morning` skill: "Go through the open
Quarkus PRs and find any I could review (my expertise is in native-image, Mandrel and
GraalVM). Sort based on what can be unblocked faster."

This is generalised to a `review-candidates` section type. Each section names a
repository and the interests (labels, path globs, title keywords) that make a PR relevant
to the user (ADR-0008). The reference configuration is the quarkusio/quarkus native-image
section in `examples/zakkak.yaml`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A ranked list of relevant PRs per configured interest (Priority: P1)

For each `review-candidates` section, `morning reviews` lists the repository's open,
non-draft PRs that are not authored by me and that match the section's interests. They
are ranked by how quickly my review could unblock them.

**Why this priority**: This is the core value, and it needs no LLM.

**Independent Test**: Use fixture pages and the Quarkus reference config. The pages hold
PRs that are relevant by label, by path, by keyword and by review request, plus irrelevant
ones. Check the filtering and the order.

**Acceptance Scenarios**:

1. **Given** a PR with one of the section's `labels`, **Then** it is listed, with "label:
   <name>" as the reason.
2. **Given** a PR changing a file that matches one of the section's `paths`, **Then** it
   is listed, with the matching path as the reason.
3. **Given** a PR whose title contains one of the section's `title-keywords`, **Then** it
   is listed, with "keyword" as the reason.
4. **Given** a PR where my review is requested, **Then** it is listed and ranked higher,
   even if no interest matches.
5. **Given** a PR I approved with no newer commits, **Then** it is not listed.
6. **Given** two relevant PRs that differ only in CI state, **Then** the green one ranks
   higher.
7. **Given** custom `weights` in the section, **Then** the scores use them.

---

### User Story 2 - A one-line hint per PR (Priority: P3)

With `--explain`, each of the top N PRs gets a one-line note on what to look at, given
the section's interests.

**Why this priority**: Nice to have. Costs tokens. Off by default.

**Independent Test**: With mocked AI services, `--explain` makes exactly N calls and shows
N notes. Without the flag, no calls are made.

### Edge Cases

- The repository has more than 300 open PRs: all are scanned through paging.
- A PR changes more than 100 files: [NEEDS CLARIFICATION: page through the files, or treat
  the PR as relevant only by label, keyword and review request?]
- A section has no `labels`, `paths` or `title-keywords`: config error, "section has no
  interests"; only PRs requesting my review would otherwise match.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For each `review-candidates` section, the system MUST scan all of the
  repository's open, non-draft PRs not authored by me.
- **FR-002**: Relevance MUST come only from the section's `labels`, `paths` (globs) and
  `title-keywords`, plus review requests addressed to me. None of these may be hardcoded.
- **FR-003**: Ranking MUST use a documented, deterministic score. The default weights are:
  review requested from me (++), CI green (+), at least one approval and waiting for more
  (+), small size (+), mergeable (+), age (slight +), and a penalty for configurable "on
  hold" labels (−). Every weight can be overridden per section.
- **FR-004**: Output MUST be grouped by section. Each row MUST show the PR, title, why it
  is relevant, size, CI state, approvals and score.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Measured with the quarkusio/quarkus reference section:

- **SC-001**: A full scan takes at most about 5 GitHub GraphQL requests for 500 open PRs.
- **SC-002**: After tuning, the maintainer judges at least 80% of listed PRs relevant over
  two weeks of use.

## Assumptions

- Reference interests, to be tuned with real data:
  - labels: `area/native-image`, `area/mandrel`;
  - paths: `**/graal/**`, `**/*Substitution*.java`, `**/nativeimage/**`,
    `core/deployment/**/pkg/steps/Native*`, `integration-tests/**/native*`,
    `**/*.native.*`, `**/reflect-config*.json`;
  - "on hold" labels: `triage/needs-rebase`, `triage/on-ice`.
