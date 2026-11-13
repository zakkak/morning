# 0001. Record architecture decisions

- Status: accepted
- Date: 2026-11-13

## Context

The project is developed mostly by coding agents across many sessions. Decisions made
in one session are lost to the next unless they are written down. A single, editable
design document drifts, and loses the reasons behind earlier choices.

## Decision

Record every cross-cutting or hard-to-reverse decision as an Architecture Decision
Record in `docs/adr/NNNN-title.md`, using a short MADR-style template: Context,
Decision, Alternatives considered, Consequences.

- ADRs are **append-only**. To change a decision, write a new ADR that supersedes the
  old one, and set the old one's status to `superseded by NNNN`.
- `docs/architecture.md` describes the current system and links to ADRs for the reasons.
- Feature-local decisions stay in that feature's `specs/NNN-*/research.md`.

## Consequences

- Agents load only the ADRs relevant to a task, not one large design document.
- The history of a decision survives its replacement.
