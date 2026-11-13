# 0005. CI status scope: every same-repo run of each configured repository

- Status: accepted
- Date: 2026-11-13

## Context

The original routine looked only at the scheduled CI of graalvm/mandrel. Real CI runs on
schedules, pushes, `workflow_dispatch`, `workflow_run` and same-repo PRs (for example
Dependabot), across many branches.

In graalvm/mandrel, for the week of 2026-11-06 to 2026-11-13, there were 72 runs: 62
same-repo, about 8 workflows, 25 branches. That is small enough to process everything.
Other repositories configured as `ci-status` sections (ADR-0008) can be much larger.

## Decision

For each configured `ci-status` section:

- **Default scope:** every run whose `head_repository` is the configured repository, for
  any event and any branch.
- **Optional filters:** `branches`, `workflows` and `events` (glob or allow lists)
  narrow the scope. Runs of PRs from forks are excluded unless `include-fork-prs: true`.
- **Grouping:** runs are grouped by **(workflow, branch)**. Each group's latest completed
  run in the window is compared with the previous completed run of the same group,
  whatever event started either run.
- **Window:** since the section's last successful run; 24h if there is none; `--since`
  overrides.
- Fully green groups collapse into a one-line footer.
- **Tracking issues:** each section picks a strategy (`none` or `workflow-inputs`); see
  ADR-0008. graalvm/mandrel uses `workflow-inputs`, its issue updater's convention.

## Consequences

- With the defaults, bookkeeping workflows are visible only when they fail.
- For large repositories, the `branches`, `workflows` and `events` filters keep the run
  count and log downloads bounded. The spec's success criteria are measured against the
  Mandrel example configuration.
