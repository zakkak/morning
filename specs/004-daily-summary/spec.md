# Feature Specification: Daily summary and `morning all`

**Feature Branch**: `004-daily-summary`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Give a prioritized list of things to work on based on the
above findings." (from the original `morning` skill)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One command for the whole routine, with a rule-based priority list (Priority: P1)

`morning` (or `morning all`) runs every configured section in config order, then prints
a prioritised to-do list built from their results.

**Why this priority**: It replaces the skill end to end, with no LLM needed.

**Independent Test**: Use fixtures for all sections of the reference config. The list
follows the `summary` section's `order`. The default order is `changes-requested`,
`my-ci-failures`, `new-ci-failures`, `needs-owner`, then `reviews` by score.

**Acceptance Scenarios**:

1. **Given** saved results from the three commands, **When** I run `morning summary
   --no-llm`, **Then** no GitHub request is made and the list follows the configured order.
2. **Given** one command fails (for example, a GitHub outage), **When** I run `morning
   all`, **Then** the other sections still render and the failure is reported in the
   summary.
3. **Given** a `summary` section whose `order` omits a category, **Then** items of that
   category are listed last, in default order.

---

### User Story 2 - An LLM-written prioritised list (Priority: P2)

Without `--no-llm`, one `deep` call turns the compact results into an ordered list of
`{ item, why, link }`.

**Independent Test**: With mocked AI services, exactly one call is made, with an input of
about 3k tokens or less.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `morning` with no subcommand MUST behave as `morning all`, which runs every
  configured section.
- **FR-005**: The priority order MUST come from the `summary` section's `order`; if it is
  absent, the default order applies.
- **FR-002**: `summary` MUST read the saved results of the latest `all` run and MUST NOT
  fetch from GitHub.
- **FR-003**: The LLM input MUST contain only IDs, titles, states and one-line reasons,
  never logs.
- **FR-004**: At the end of `all`, the system MUST print token usage and the number of
  LLM calls.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A typical `morning all` makes 5 LLM calls or fewer once the caches are
  warm.
- **SC-002**: A typical day's `morning all` uses at least 80% fewer LLM tokens than the
  original skill. [NEEDS CLARIFICATION: baseline token count of the original skill run,
  needed to measure this]

## Assumptions

- The summary is for the maintainer only. It is never posted anywhere.
