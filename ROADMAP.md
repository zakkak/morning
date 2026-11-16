# Roadmap

Ordered by priority. Each item links to its spec once one exists. Statuses: `idea` →
`specified` → `planned` → `in progress` → `done`.

| # | Feature | Spec | Status | Notes |
|---|---|---|---|---|
| 1 | My open PRs status (plus the project skeleton and YAML configuration) | [001-my-prs](specs/001-my-prs/spec.md) | in progress | US1 and US3 implemented on branch `001-my-prs`, awaiting review. US2 (LLM relatedness) is deferred until 002 US3 introduces the LLM boundary. |
| 2 | CI status for configured repositories | [002-ci-status](specs/002-ci-status/spec.md) | specified | US3 introduces the LLM boundary (gateway, toolbox, redaction, cache). |
| 3 | Review candidates for configured interests | [003-review-candidates](specs/003-review-candidates/spec.md) | specified | |
| 4 | Daily summary and `morning all` | [004-daily-summary](specs/004-daily-summary/spec.md) | specified | Includes the token and LLM-call report. |
| 5 | Native executable built with Mandrel | — | idea | Fast startup; dogfooding. Needs `quarkus-github-api` native support. |
| 6 | Distribution | — | idea | Open: a `morning` wrapper around `quarkus-run.jar` first, then a native binary? Install location? |
| 7 | More agent integrations | — | idea | Skills are already agent-agnostic in `.agents/skills` (ADR-0007). Add `specify integration install <agent>` only for agents that don't read `.agents/skills` (for example bob or copilot). |
| 8 | More tracking-issue strategies | — | idea | For example "open issues with label X opened by bot Y". Add when a real configured repository needs one (ADR-0008). |
| 9 | Other forges and trackers (GitLab, Jira) | — | idea | Out of scope for now (ADR-0008). Would need new fetchers. |

## Upgrades to track

- hub4j 2.0 at GA (ADR-0004).
- Quarkus 4.0 and quarkus-langchain4j 2.0 at GA (ADR-0002).
